package com.application.umkmshop.backend.worker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NotificationPayloadParserTest {
    @Test
    fun parsesNewMessagePayload() {
        val payload = NotificationPayloadParser.parse(
            """
            {
              "type": "new_message",
              "message_id": "10000000-0000-0000-0000-000000000001",
              "room_id": "20000000-0000-0000-0000-000000000002",
              "to_user_id": "30000000-0000-0000-0000-000000000003",
              "preview_text": "Halo"
            }
            """.trimIndent(),
        )

        val newMessage = payload as NewMessagePayload
        assertEquals("10000000-0000-0000-0000-000000000001", newMessage.messageId.toString())
        assertEquals("20000000-0000-0000-0000-000000000002", newMessage.roomId.toString())
        assertEquals("30000000-0000-0000-0000-000000000003", newMessage.toUserId.toString())
        assertEquals("Halo", newMessage.previewText)
    }

    @Test
    fun parsesReplyReminderPayload() {
        val payload = NotificationPayloadParser.parse(
            """
            {
              "type": "reply_reminder",
              "room_id": "20000000-0000-0000-0000-000000000002",
              "to_user_id": "30000000-0000-0000-0000-000000000003"
            }
            """.trimIndent(),
        )

        val reminder = payload as ReplyReminderPayload
        assertEquals("20000000-0000-0000-0000-000000000002", reminder.roomId.toString())
        assertEquals("30000000-0000-0000-0000-000000000003", reminder.toUserId.toString())
    }

    @Test
    fun parsesOrderEventPayload() {
        val payload = NotificationPayloadParser.parse(
            """
            {
              "type": "payment_paid",
              "order_id": "10000000-0000-0000-0000-000000000001",
              "room_id": "20000000-0000-0000-0000-000000000002",
              "to_user_id": "30000000-0000-0000-0000-000000000003",
              "title": "Pembayaran diterima",
              "body": "Pembayaran untuk madu mentah sudah tercatat."
            }
            """.trimIndent(),
        )

        val orderEvent = payload as OrderEventPayload
        assertEquals("payment_paid", orderEvent.type)
        assertEquals("10000000-0000-0000-0000-000000000001", orderEvent.orderId.toString())
        assertEquals("20000000-0000-0000-0000-000000000002", orderEvent.roomId.toString())
        assertEquals("30000000-0000-0000-0000-000000000003", orderEvent.toUserId.toString())
        assertEquals("Pembayaran diterima", orderEvent.title)
    }

    @Test
    fun rejectsUnsupportedPayloadType() {
        assertFailsWith<IllegalStateException> {
            NotificationPayloadParser.parse(
                """
                {
                  "type": "unknown",
                  "room_id": "20000000-0000-0000-0000-000000000002",
                  "to_user_id": "30000000-0000-0000-0000-000000000003"
                }
                """.trimIndent(),
            )
        }
    }
}
