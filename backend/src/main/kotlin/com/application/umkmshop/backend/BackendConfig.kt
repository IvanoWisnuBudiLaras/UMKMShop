package com.application.umkmshop.backend

import com.application.umkmshop.backend.oauth.OAuthServerConfig
import com.application.umkmshop.backend.worker.WorkerConfig

data class BackendConfig(
    val port: Int,
    val oauth: OAuthServerConfig,
    val workerMode: WorkerMode,
    val worker: WorkerConfig?,
    val supabaseJwtSecret: String?,
    val supabaseUrl: String?,
    val supabaseKey: String?,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): BackendConfig {
            // ... (keep port and worker logic)
            val port = env.intValue("UMKMSHOP_BACKEND_PORT")
                ?: env.intValue("PORT")
                ?: env.intValue("UMKMSHOP_OAUTH_PORT")
                ?: env.intValue("UMKMSHOP_HEALTH_PORT")
                ?: 8090

            val explicitWorkerEnabled = env.booleanValue("UMKMSHOP_WORKER_ENABLED")
            val workerCanStart = env.hasDatabaseUrl() && env.hasFirebaseCredentials()
            val workerMode = when {
                explicitWorkerEnabled == true -> WorkerMode.Enabled
                explicitWorkerEnabled == false -> WorkerMode.DisabledByConfig
                workerCanStart -> WorkerMode.Enabled
                else -> WorkerMode.DisabledMissingConfig
            }

            val workerConfig = if (workerMode == WorkerMode.Enabled) {
                WorkerConfig.fromEnvironment(env)
            } else {
                null
            }

            return BackendConfig(
                port = port,
                oauth = OAuthServerConfig.fromEnvironment(env, defaultPort = port),
                workerMode = workerMode,
                worker = workerConfig,
                supabaseJwtSecret = env["SUPABASE_JWT_SECRET"] ?: env["UMKMSHOP_SUPABASE_JWT_SECRET"],
                supabaseUrl = env["SUPABASE_URL"],
                supabaseKey = env["SUPABASE_ANON_KEY"] ?: env["SUPABASE_PUBLISHABLE_KEY"]
            )
        }
    }
}

enum class WorkerMode {
    Enabled,
    DisabledByConfig,
    DisabledMissingConfig,
}

internal fun Map<String, String>.intValue(name: String): Int? =
    this[name]?.takeIf { it.isNotBlank() }?.toInt()

private fun Map<String, String>.booleanValue(name: String): Boolean? =
    this[name]?.takeIf { it.isNotBlank() }?.lowercase()?.let { value ->
        when (value) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> error("$name must be true or false.")
        }
    }

private fun Map<String, String>.hasDatabaseUrl(): Boolean =
    !this["UMKMSHOP_BACKEND_DATABASE_URL"].isNullOrBlank() ||
        !this["UMKMSHOP_DATABASE_URL"].isNullOrBlank() ||
        !this["DATABASE_URL"].isNullOrBlank()

private fun Map<String, String>.hasFirebaseCredentials(): Boolean =
    !this["FIREBASE_SERVICE_ACCOUNT_JSON"].isNullOrBlank() ||
        !this["GOOGLE_APPLICATION_CREDENTIALS"].isNullOrBlank()
