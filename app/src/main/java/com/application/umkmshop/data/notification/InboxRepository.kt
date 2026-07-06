package com.application.umkmshop.data.notification

import com.application.umkmshop.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class InboxRepository(
    private val supabaseClient: SupabaseClient = SupabaseClientProvider.client,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) {
    private val client
        get() = supabaseClient
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listNotifications(): List<InboxNotification> =
        withContext(ioDispatcher) {
            client.from("notifications")
                .select {
                    order(column = "created_at", order = Order.DESCENDING)
                    limit(100)
                }
                .decodeList<InboxNotificationDto>()
                .mapNotNull { it.toInboxNotificationOrNull() }
        }

    suspend fun unreadCount(): Int =
        withContext(ioDispatcher) {
            client.postgrest
                .rpc("unread_notifications_count")
                .decodeAs<Long>()
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }

    suspend fun markAllRead(): Int =
        withContext(ioDispatcher) {
            client.postgrest
                .rpc("mark_notifications_read")
                .decodeAs<Int>()
        }

    fun subscribeToInbox(): Flow<InboxNotification> = flow {
        val userId = currentUserId()
        val channel = client.channel("inbox-notifications-$userId")
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "notifications"
            filter(FilterOperation("user_id", FilterOperator.EQ, userId))
        }.mapNotNull { action ->
            json.decodeFromJsonElement(InboxNotificationDto.serializer(), action.record)
                .toInboxNotificationOrNull()
        }

        try {
            channel.subscribe()
            emitAll(changes)
        } finally {
            withContext(NonCancellable) {
                channel.unsubscribe()
            }
        }
    }

    private suspend fun currentUserId(): String =
        client.auth.currentUserOrNull()?.id
            ?: error("Session login tidak tersedia.")

    private fun InboxNotificationDto.toInboxNotificationOrNull(): InboxNotification? {
        val notificationId = id ?: return null
        val timestamp = createdAt ?: return null
        return InboxNotification(
            id = notificationId,
            type = type,
            title = title,
            body = body,
            relatedOrderId = relatedOrderId,
            isRead = isRead,
            createdAt = timestamp,
        )
    }
}
