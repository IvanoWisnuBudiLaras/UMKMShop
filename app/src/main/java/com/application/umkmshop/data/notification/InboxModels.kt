package com.application.umkmshop.data.notification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val NOTIFICATION_TYPE_ORDER_CREATED = "order_created"
const val NOTIFICATION_TYPE_PAYMENT_PAID = "payment_paid"
const val NOTIFICATION_TYPE_PAYMENT_EXPIRED = "payment_expired"
const val NOTIFICATION_TYPE_PAYMENT_CANCELLED = "payment_cancelled"
const val NOTIFICATION_TYPE_REPLY_REMINDER = "reply_reminder"

data class InboxNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String?,
    val relatedOrderId: String?,
    val isRead: Boolean,
    val createdAt: String,
)

@Serializable
data class InboxNotificationDto(
    @SerialName("id")
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("type")
    val type: String,
    @SerialName("title")
    val title: String,
    @SerialName("body")
    val body: String? = null,
    @SerialName("related_order_id")
    val relatedOrderId: String? = null,
    @SerialName("is_read")
    val isRead: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
)
