package com.application.umkmshop.backend.worker

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.postgresql.util.PGobject
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource
import org.slf4j.LoggerFactory

class WorkerDatabase(
    private val dataSource: DataSource,
    private val queueName: String,
) {
    private val log = LoggerFactory.getLogger(WorkerDatabase::class.java)

    fun readQueue(visibilityTimeoutSeconds: Int, batchSize: Int): List<QueueJob> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select msg_id, read_ct, enqueued_at, vt, message::text as message
                from pgmq.read(?, ?, ?, '{}'::jsonb)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, queueName)
                statement.setInt(2, visibilityTimeoutSeconds)
                statement.setInt(3, batchSize)

                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(rows.toQueueJob())
                        }
                    }
                }
            }
        }

    fun archiveJob(msgId: Long): Boolean =
        executeBoolean("select pgmq.archive(?, ?)", msgId)

    fun deleteJob(msgId: Long): Boolean =
        executeBoolean("select pgmq.delete(?, ?)", msgId)

    fun getPushTokens(userId: UUID): List<PushToken> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "select id, fcm_token from public.push_tokens where user_id = ?"
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(PushToken(rows.getObject("id", UUID::class.java), rows.getString("fcm_token")))
                        }
                    }
                }
            }
        }

    fun deletePushToken(id: UUID): Int =
        dataSource.connection.use { connection ->
            connection.prepareStatement("delete from public.push_tokens where id = ?").use { statement ->
                statement.setObject(1, id)
                statement.executeUpdate()
            }
        }

    fun getNewMessageDetails(messageId: UUID, roomId: UUID): NewMessageDetails? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select sender.name as sender_name,
                       products.name as product_name,
                       messages.message_text
                from public.messages
                join public.profiles sender on sender.id = messages.sender_id
                join public.chat_rooms rooms on rooms.id = messages.room_id
                left join public.products products on products.id = rooms.product_id
                where messages.id = ?
                  and messages.room_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, messageId)
                statement.setObject(2, roomId)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) null else NewMessageDetails(
                        senderName = rows.getString("sender_name"),
                        productName = rows.getString("product_name"),
                        messageText = rows.getString("message_text"),
                    )
                }
            }
        }

    fun getReplyReminderDetails(roomId: UUID): ReplyReminderDetails? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "select p.name as product_name from public.chat_rooms r left join public.products p on p.id = r.product_id where r.id = ?"
            ).use { statement ->
                statement.setObject(1, roomId)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) null else ReplyReminderDetails(productName = rows.getString("product_name"))
                }
            }
        }

    fun getOrderNotificationDetails(orderId: UUID): OrderNotificationDetails? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "select p.name as product_name, o.total_amount, o.status from public.orders o left join public.products p on p.id = o.product_id where o.id = ?"
            ).use { statement ->
                statement.setObject(1, orderId)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) null else OrderNotificationDetails(
                        productName = rows.getString("product_name"),
                        totalAmount = rows.getDouble("total_amount").takeUnless { rows.wasNull() },
                        status = rows.getString("status"),
                    )
                }
            }
        }

    private fun executeBoolean(sql: String, msgId: Long): Boolean =
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, queueName)
                statement.setLong(2, msgId)
                statement.executeQuery().use { rows ->
                    rows.next() && rows.getBoolean(1)
                }
            }
        }
}

fun createDataSource(config: WorkerConfig, poolSize: Int = config.dbPoolMaxSize): HikariDataSource {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.databaseUrl
        maximumPoolSize = poolSize
        minimumIdle = 1
        poolName = "umkmshop-backend-pool"
        addDataSourceProperty("prepareThreshold", "0") // Penting untuk Supavisor
    }
    return HikariDataSource(hikariConfig)
}

private fun ResultSet.toQueueJob(): QueueJob =
    QueueJob(
        msgId = getLong("msg_id"),
        readCount = getLong("read_ct"),
        enqueuedAt = getObject("enqueued_at", OffsetDateTime::class.java),
        visibleAt = getObject("vt", OffsetDateTime::class.java),
        rawMessage = when (val value = getObject("message")) {
            is PGobject -> value.value.orEmpty()
            else -> getString("message")
        },
    )
