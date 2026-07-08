package com.application.umkmshop.backend

import com.application.umkmshop.backend.oauth.*
import com.application.umkmshop.backend.worker.*
import io.ktor.http.*
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant

fun main() {
    runBlocking {
        runBackend()
    }
}

private suspend fun runBackend() = coroutineScope {
    val log = LoggerFactory.getLogger("UMKMShopBackend")
    val config = BackendConfig.fromEnvironment()
    val clock = Clock.systemUTC()
    
    // 1. Database & Store Setup (Merged Pool for Worker + OAuth)
    val dataSource = if (config.oauth.useJdbcStore || config.workerMode == WorkerMode.Enabled) {
        val workerConfig = config.worker ?: WorkerConfig.fromEnvironment()
        createDataSource(workerConfig, poolSize = 12)
    } else null

    val store = if (config.oauth.useJdbcStore && dataSource != null) {
        JdbcOAuthStore(dataSource).also { 
            it.registerClient(config.oauth.demoClient)
            ensureSigningKey(it) 
        }
    } else {
        InMemoryOAuthStore().also { 
            it.registerClient(config.oauth.demoClient)
            ensureSigningKey(it) 
        }
    }

    // 2. Service Setup
    val jwtService = JwtService(config.oauth.issuer, store, clock)
    val oauthService = OAuthService(config.oauth.issuer, store, jwtService, clock)
    val supabaseAuth = if (config.supabaseJwtSecret != null && config.supabaseUrl != null && config.supabaseKey != null) {
        SupabaseAuthService(config.supabaseUrl, config.supabaseKey, config.supabaseJwtSecret)
    } else null

    // 3. Background Worker (Isolated Coroutine Scope)
    var lastWorkerPoll = Instant.EPOCH
    var workerError: String? = null
    
    val workerJob = config.worker?.let { workerConfig ->
        log.info("Preparing isolated Notification Worker...")
        
        launch(Dispatchers.Default + SupervisorJob()) {
            val ds = dataSource ?: createDataSource(workerConfig, poolSize = 12)
            val database = WorkerDatabase(ds, workerConfig.queueName)
            
            val notifier = try {
                createFirebaseNotifier(workerConfig)
            } catch (e: Exception) {
                val msg = "FATAL: Failed to initialize Firebase Notifier: ${e.message}"
                log.error(msg)
                workerError = msg
                return@launch
            }

            val worker = NotificationWorker(workerConfig, database, notifier)
            log.info("Launching Notification Worker loop...")
            
            while (isActive) {
                try {
                    lastWorkerPoll = Instant.now()
                    worker.runBatch()
                    workerError = null
                } catch (e: Exception) {
                    workerError = "Worker Batch Error: ${e.message}"
                    log.warn(workerError)
                }
                delay(workerConfig.emptyPollDelayMillis)
            }
        }
    }

    // 4. HTTP Server
    val server = embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Get)
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                log.error("Unhandled OAuth/HTTP Exception", cause)
                call.respondText(
                    """{"error":"server_error","error_description":"${cause.message ?: "Internal Error"}"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }
        routing {
            get("/health") {
                val now = Instant.now().epochSecond
                val workerStillPolling = workerJob == null || (now - lastWorkerPoll.epochSecond < 60)
                val dbHealthy = dataSource?.connection?.use { !it.isClosed } ?: true
                
                if (workerStillPolling && dbHealthy && workerError == null) {
                    call.respondText("ok", status = HttpStatusCode.OK)
                } else {
                    val status = "worker_active=$workerStillPolling, db=$dbHealthy, error=${workerError ?: "none"}"
                    call.respondText(status, status = HttpStatusCode.ServiceUnavailable)
                }
            }
        }
        oauthRoutes(oauthService, supabaseAuth)
    }

    try {
        log.info("Starting UMKMShop Backend (Worker + OAuth) on port {}", config.port)
        server.start(wait = true)
    } finally {
        workerJob?.cancelAndJoin()
        dataSource?.close()
        server.stop(1_000, 5_000)
    }
}

private fun ensureSigningKey(store: OAuthStore) {
    if (store.getActiveSigningKey() == null) {
        val pair = generateRsaKeyPair()
        val pems = pair.toPem()
        store.saveSigningKey(SigningKey(
            kid = "prod-rsa-${Instant.now().epochSecond}",
            privateKeyPem = pems.first,
            publicKeyPem = pems.second
        ))
    }
}
