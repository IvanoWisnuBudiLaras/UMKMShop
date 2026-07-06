package com.application.umkmshop.backend

import com.application.umkmshop.backend.oauth.createDemoOAuthService
import com.application.umkmshop.backend.oauth.oauthRoutes
import com.application.umkmshop.backend.worker.NotificationWorker
import com.application.umkmshop.backend.worker.WorkerDatabase
import com.application.umkmshop.backend.worker.createDataSource
import com.application.umkmshop.backend.worker.createFirebaseNotifier
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    runBlocking {
        runBackend()
    }
}

private suspend fun runBackend() = coroutineScope {
    val log = LoggerFactory.getLogger("UMKMShopBackend")
    val config = BackendConfig.fromEnvironment()
    val oauthService = createDemoOAuthService(config.oauth)

    val workerResources = config.worker?.let { workerConfig ->
        val dataSource = createDataSource(workerConfig)
        val database = WorkerDatabase(dataSource, workerConfig.queueName)
        val notifier = createFirebaseNotifier(workerConfig)
        WorkerResources(dataSource, NotificationWorker(workerConfig, database, notifier))
    }

    val workerJob = workerResources?.let { resources ->
        log.info("Starting notification worker in backend process")
        launch(SupervisorJob() + Dispatchers.Default) {
            resources.worker.run()
        }
    }

    if (workerResources == null) {
        when (config.workerMode) {
            WorkerMode.DisabledByConfig -> log.info("Notification worker disabled by UMKMSHOP_WORKER_ENABLED=false")
            WorkerMode.DisabledMissingConfig -> log.info(
                "Notification worker disabled because database/Firebase env is incomplete. Set UMKMSHOP_WORKER_ENABLED=true to fail fast in production.",
            )
            WorkerMode.Enabled -> Unit
        }
    }

    val server = embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        routing {
            get("/health") {
                call.respondText("ok", status = HttpStatusCode.OK)
            }
        }
        oauthRoutes(oauthService, config.oauth.demoUser)
    }
    try {
        log.info("Starting UMKMShop backend on port {}", config.port)
        server.start(wait = true)
    } finally {
        workerJob?.cancelAndJoin()
        workerResources?.dataSource?.close()
        server.stop(1_000, 5_000)
    }
}

private data class WorkerResources(
    val dataSource: AutoCloseable,
    val worker: NotificationWorker,
)
