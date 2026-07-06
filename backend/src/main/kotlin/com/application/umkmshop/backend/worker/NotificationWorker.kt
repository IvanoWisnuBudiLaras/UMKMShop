package com.application.umkmshop.backend.worker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class NotificationWorker(
    private val config: WorkerConfig,
    private val database: WorkerDatabase,
    private val notifier: PushNotifier,
) {
    private val log = LoggerFactory.getLogger(NotificationWorker::class.java)
    private val effectiveBatchSize = minOf(config.batchSize, config.maxConcurrency)

    suspend fun run() {
        log.info(
            "Starting notification worker queue={} batchSize={} maxConcurrency={} maxAttempts={}",
            config.queueName,
            config.batchSize,
            config.maxConcurrency,
            config.maxAttempts,
        )

        coroutineScope {
            while (isActive) {
                val jobs = database.readQueue(config.visibilityTimeoutSeconds, effectiveBatchSize)
                if (jobs.isEmpty()) {
                    delay(config.emptyPollDelayMillis)
                    continue
                }

                jobs.map { job ->
                    async(Dispatchers.IO) {
                        processJob(job)
                    }
                }.awaitAll()
            }
        }
    }

    private fun processJob(job: QueueJob) {
        try {
            if (retryDecision(job.readCount, config.maxAttempts) == RetryDecision.ArchiveMaxAttempts) {
                database.archiveJob(job.msgId)
                log.error("Archived notification job {} after {} reads", job.msgId, job.readCount)
                return
            }

            val payload = NotificationPayloadParser.parse(job.rawMessage)
            val tokens = database.getPushTokens(payload.toUserId)
            if (tokens.isEmpty()) {
                database.archiveJob(job.msgId)
                log.info("Archived notification job {} because recipient has no push tokens", job.msgId)
                return
            }

            val notification = buildNotification(payload)
            val transientFailures = mutableListOf<Throwable>()
            var successCount = 0

            tokens.forEach { token ->
                try {
                    notifier.send(token.fcmToken, notification)
                    successCount += 1
                } catch (exception: InvalidPushTokenException) {
                    database.deletePushToken(token.id)
                    log.info("Deleted invalid FCM token {} for job {}", token.id, job.msgId)
                } catch (exception: Exception) {
                    transientFailures += exception
                }
            }

            if (deliveryCompletionDecision(successCount, transientFailures.size) == DeliveryCompletionDecision.RetryJob) {
                throw transientFailures.first()
            }

            // Partial token failure is intentionally treated as complete once any token succeeds.
            // Retrying the whole pgmq job would duplicate pushes for tokens that already received it.
            database.deleteJob(job.msgId)
            if (transientFailures.isNotEmpty()) {
                log.warn(
                    "Deleted notification job {} after {} successful token sends and {} transient token failures",
                    job.msgId,
                    successCount,
                    transientFailures.size,
                )
            } else {
                log.info("Deleted completed notification job {}", job.msgId)
            }
        } catch (exception: Exception) {
            log.warn("Notification job {} failed and will retry after visibility timeout", job.msgId, exception)
        }
    }

    private fun buildNotification(payload: NotificationPayload): PushNotification =
        when (payload) {
            is NewMessagePayload -> {
                val details = database.getNewMessageDetails(payload.messageId, payload.roomId)
                val sender = details?.senderName?.takeIf { it.isNotBlank() } ?: "Pembeli"
                val product = details?.productName?.takeIf { it.isNotBlank() }
                val body = details?.messageText?.takeIf { it.isNotBlank() }
                    ?: payload.previewText?.takeIf { it.isNotBlank() }
                    ?: "Ada pesan baru."

                PushNotification(
                    title = if (product == null) "Pesan baru dari $sender" else "Pesan baru: $product",
                    body = body.take(100),
                    data = mapOf(
                        "type" to "new_message",
                        "room_id" to payload.roomId.toString(),
                        "message_id" to payload.messageId.toString(),
                    ),
                )
            }

            is ReplyReminderPayload -> {
                val details = database.getReplyReminderDetails(payload.roomId)
                val product = details?.productName?.takeIf { it.isNotBlank() }

                PushNotification(
                    title = "Reminder chat belum dibalas",
                    body = if (product == null) {
                        "Ada calon pembeli yang menunggu balasan."
                    } else {
                        "Calon pembeli produk $product menunggu balasan."
                    },
                    data = mapOf(
                        "type" to "reply_reminder",
                        "room_id" to payload.roomId.toString(),
                    ),
                )
            }

            is OrderEventPayload -> {
                val details = database.getOrderNotificationDetails(payload.orderId)
                val title = payload.title?.takeIf { it.isNotBlank() }
                    ?: payload.type.defaultOrderTitle()
                val body = payload.body?.takeIf { it.isNotBlank() }
                    ?: details?.productName?.takeIf { it.isNotBlank() }?.let { product ->
                        "$product: status ${details.status ?: "terbaru"}."
                    }
                    ?: "Ada pembaruan pesanan."

                PushNotification(
                    title = title,
                    body = body.take(100),
                    data = mapOf(
                        "type" to payload.type,
                        "order_id" to payload.orderId.toString(),
                        "room_id" to payload.roomId.toString(),
                    ),
                )
            }
        }
}

private fun String.defaultOrderTitle(): String =
    when (this) {
        "order_created" -> "Invoice baru"
        "payment_paid" -> "Pembayaran berhasil"
        "payment_expired" -> "Invoice kedaluwarsa"
        "payment_cancelled" -> "Invoice dibatalkan"
        else -> "Pembaruan pesanan"
    }

fun CoroutineScope.launchWorker(
    config: WorkerConfig,
    database: WorkerDatabase,
    notifier: PushNotifier,
): Job =
    launch(SupervisorJob() + Dispatchers.Default) {
        NotificationWorker(config, database, notifier).run()
    }
