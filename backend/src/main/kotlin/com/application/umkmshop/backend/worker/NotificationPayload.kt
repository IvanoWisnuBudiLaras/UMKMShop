package com.application.umkmshop.backend.worker

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

sealed interface NotificationPayload {
    val roomId: UUID
    val toUserId: UUID
}

data class NewMessagePayload(
    val messageId: UUID,
    override val roomId: UUID,
    override val toUserId: UUID,
    val previewText: String?,
) : NotificationPayload

data class ReplyReminderPayload(
    override val roomId: UUID,
    override val toUserId: UUID,
) : NotificationPayload

data class OrderEventPayload(
    val type: String,
    val orderId: UUID,
    override val roomId: UUID,
    override val toUserId: UUID,
    val title: String?,
    val body: String?,
) : NotificationPayload

object NotificationPayloadParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawMessage: String): NotificationPayload {
        val payload = json.parseToJsonElement(rawMessage).jsonObject
        return when (payload.requiredString("type")) {
            "new_message" -> NewMessagePayload(
                messageId = UUID.fromString(payload.requiredString("message_id")),
                roomId = UUID.fromString(payload.requiredString("room_id")),
                toUserId = UUID.fromString(payload.requiredString("to_user_id")),
                previewText = payload.optionalString("preview_text"),
            )

            "reply_reminder" -> ReplyReminderPayload(
                roomId = UUID.fromString(payload.requiredString("room_id")),
                toUserId = UUID.fromString(payload.requiredString("to_user_id")),
            )

            "order_created",
            "payment_paid",
            "payment_expired",
            "payment_cancelled" -> OrderEventPayload(
                type = payload.requiredString("type"),
                orderId = UUID.fromString(payload.requiredString("order_id")),
                roomId = UUID.fromString(payload.requiredString("room_id")),
                toUserId = UUID.fromString(payload.requiredString("to_user_id")),
                title = payload.optionalString("title"),
                body = payload.optionalString("body"),
            )

            else -> error("Unsupported notification payload type")
        }
    }
}

private fun JsonObject.requiredString(name: String): String =
    optionalString(name)?.takeIf { it.isNotBlank() } ?: error("Missing notification payload field: $name")

private fun JsonObject.optionalString(name: String): String? =
    (this[name] as? JsonPrimitive)?.jsonPrimitive?.content
