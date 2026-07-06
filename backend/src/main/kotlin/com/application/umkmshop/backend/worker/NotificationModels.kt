package com.application.umkmshop.backend.worker

import java.time.OffsetDateTime
import java.util.UUID

data class QueueJob(
    val msgId: Long,
    val readCount: Long,
    val enqueuedAt: OffsetDateTime,
    val visibleAt: OffsetDateTime,
    val rawMessage: String,
)

data class PushToken(
    val id: UUID,
    val fcmToken: String,
)

data class PushNotification(
    val title: String,
    val body: String,
    val data: Map<String, String>,
)

data class NewMessageDetails(
    val senderName: String?,
    val productName: String?,
    val messageText: String?,
)

data class ReplyReminderDetails(
    val productName: String?,
)

data class OrderNotificationDetails(
    val productName: String?,
    val totalAmount: Double?,
    val status: String?,
)
