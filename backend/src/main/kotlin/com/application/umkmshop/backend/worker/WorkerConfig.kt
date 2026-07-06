package com.application.umkmshop.backend.worker

data class WorkerConfig(
    val databaseUrl: String,
    val queueName: String = "notifications",
    val maxAttempts: Int = 5,
    val visibilityTimeoutSeconds: Int = 30,
    val batchSize: Int = 10,
    val maxConcurrency: Int = 8,
    val emptyPollDelayMillis: Long = 2_000,
    val dbPoolMaxSize: Int = 8,
    val healthPort: Int? = 8081,
    val firebaseServiceAccountJson: String? = null,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): WorkerConfig {
            val databaseUrl = env["UMKMSHOP_BACKEND_DATABASE_URL"]
                ?: env["UMKMSHOP_DATABASE_URL"]
                ?: env["DATABASE_URL"]
                ?: error("UMKMSHOP_BACKEND_DATABASE_URL, UMKMSHOP_DATABASE_URL, or DATABASE_URL is required")

            return WorkerConfig(
                databaseUrl = databaseUrl.withPrepareThresholdDisabled(),
                queueName = env["UMKMSHOP_BACKEND_QUEUE_NAME"] ?: env["UMKMSHOP_QUEUE_NAME"] ?: "notifications",
                maxAttempts = env.intValue("UMKMSHOP_BACKEND_MAX_ATTEMPTS", env.intValue("UMKMSHOP_MAX_ATTEMPTS", 5)),
                visibilityTimeoutSeconds = env.intValue(
                    "UMKMSHOP_BACKEND_VISIBILITY_TIMEOUT_SECONDS",
                    env.intValue("UMKMSHOP_VISIBILITY_TIMEOUT_SECONDS", 30),
                ),
                batchSize = env.intValue("UMKMSHOP_BACKEND_BATCH_SIZE", env.intValue("UMKMSHOP_BATCH_SIZE", 10)),
                maxConcurrency = env.intValue(
                    "UMKMSHOP_BACKEND_MAX_CONCURRENCY",
                    env.intValue("UMKMSHOP_MAX_CONCURRENCY", 8),
                ),
                emptyPollDelayMillis = env.longValue(
                    "UMKMSHOP_BACKEND_EMPTY_POLL_DELAY_MS",
                    env.longValue("UMKMSHOP_EMPTY_POLL_DELAY_MS", 2_000),
                ),
                dbPoolMaxSize = env.intValue("UMKMSHOP_BACKEND_DB_POOL_MAX_SIZE", env.intValue("UMKMSHOP_DB_POOL_MAX_SIZE", 8)),
                healthPort = env["UMKMSHOP_HEALTH_PORT"]?.takeIf { it.isNotBlank() }?.toInt(),
                firebaseServiceAccountJson = env["FIREBASE_SERVICE_ACCOUNT_JSON"]?.takeIf { it.isNotBlank() },
            )
        }
    }
}

private fun Map<String, String>.intValue(name: String, defaultValue: Int): Int =
    this[name]?.takeIf { it.isNotBlank() }?.toInt() ?: defaultValue

private fun Map<String, String>.longValue(name: String, defaultValue: Long): Long =
    this[name]?.takeIf { it.isNotBlank() }?.toLong() ?: defaultValue

internal fun String.withPrepareThresholdDisabled(): String {
    if (contains("prepareThreshold=", ignoreCase = true)) return this
    val separator = if (contains("?")) "&" else "?"
    return "$this${separator}prepareThreshold=0"
}
