package com.application.umkmshop.data.chat

import com.application.umkmshop.data.auth.ProfileDto
import com.application.umkmshop.data.product.ProductDto
import com.application.umkmshop.data.product.ProductImageDto
import com.application.umkmshop.data.product.PRODUCT_STATUS_ACTIVE
import com.application.umkmshop.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ChatRepository(
    private val supabaseClient: SupabaseClient = SupabaseClientProvider.client,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val client get() = supabaseClient
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getOrCreateRoomForProduct(productId: String): ChatRoomDetail =
        withContext(ioDispatcher) {
            val buyerId = currentUserId()
            val product = client.from("products")
                .select {
                    filter {
                        eq("id", productId)
                        eq("status", PRODUCT_STATUS_ACTIVE)
                    }
                }
                .decodeSingle<ProductDto>()

            require(product.sellerId != buyerId) {
                "Penjual tidak bisa membuka chat ke produknya sendiri."
            }

            val existingRoom = findRoom(
                buyerId = buyerId,
                sellerId = product.sellerId,
                productId = product.id,
            )
            val room = existingRoom ?: runCatching {
                client.from("chat_rooms")
                    .insert(
                        ChatRoomInsertDto(
                            buyerId = buyerId,
                            sellerId = product.sellerId,
                            productId = product.id,
                        ),
                    ) {
                        select()
                    }
                    .decodeSingle<ChatRoomDto>()
            }.getOrElse {
                findRoom(
                    buyerId = buyerId,
                    sellerId = product.sellerId,
                    productId = product.id,
                ) ?: throw it
            }

            buildRoomDetail(room = room, product = product)
        }

    suspend fun getRoom(roomId: String): ChatRoomDetail =
        withContext(ioDispatcher) {
            val room = client.from("chat_rooms")
                .select {
                    filter {
                        eq("id", roomId)
                    }
                }
                .decodeSingle<ChatRoomDto>()
            buildRoomDetail(room = room)
        }

    suspend fun listRooms(): List<ChatRoomSummary> =
        withContext(ioDispatcher) {
            val currentUserId = currentUserId()
            val buyerRooms = client.from("chat_rooms")
                .select {
                    filter {
                        eq("buyer_id", currentUserId)
                    }
                    order(column = "last_message_at", order = Order.DESCENDING, nullsFirst = false)
                }
                .decodeList<ChatRoomDto>()
            val sellerRooms = client.from("chat_rooms")
                .select {
                    filter {
                        eq("seller_id", currentUserId)
                    }
                    order(column = "last_message_at", order = Order.DESCENDING, nullsFirst = false)
                }
                .decodeList<ChatRoomDto>()

            val rooms = (buyerRooms + sellerRooms)
                .distinctBy { it.id }
                .sortedWith(compareByDescending<ChatRoomDto> { it.lastMessageAt ?: it.createdAt.orEmpty() })
            val productsById = loadProducts(rooms.map { it.productId })
            val imagesByProduct = loadImagesFor(rooms.map { it.productId })
            val profilesById = loadProfiles(
                rooms.flatMap { room ->
                    listOf(room.buyerId, room.sellerId)
                },
            )
            val latestMessagesByRoom = loadLatestMessages(rooms)

            rooms.map { room ->
                val isSellerView = room.sellerId == currentUserId
                val otherUserId = if (isSellerView) room.buyerId else room.sellerId
                ChatRoomSummary(
                    id = room.id,
                    buyerId = room.buyerId,
                    sellerId = room.sellerId,
                    productId = room.productId,
                    productName = productsById[room.productId]?.name ?: "Produk",
                    productImageUrl = imagesByProduct[room.productId]?.firstOrNull(),
                    otherUserName = profilesById[otherUserId]?.name ?: "Pengguna",
                    lastMessageAt = room.lastMessageAt,
                    lastMessagePreview = latestMessagesByRoom[room.id]?.messageText,
                    isSellerView = isSellerView,
                )
            }
        }

    suspend fun listMessages(roomId: String): List<ChatMessage> =
        withContext(ioDispatcher) {
            client.from("messages")
                .select {
                    filter {
                        eq("room_id", roomId)
                    }
                    order(column = "created_at", order = Order.ASCENDING)
                }
                .decodeList<MessageDto>()
                .mapNotNull { it.toChatMessageOrNull() }
        }

    suspend fun sendMessage(roomId: String, messageText: String): ChatMessage =
        withContext(ioDispatcher) {
            val cleanedMessage = messageText.trim()
            require(cleanedMessage.isNotEmpty()) { "Pesan tidak boleh kosong." }
            val senderId = currentUserId()
            client.from("messages")
                .insert(
                    MessageDto(
                        roomId = roomId,
                        senderId = senderId,
                        messageText = cleanedMessage,
                    ),
                ) {
                    select()
                }
                .decodeSingle<MessageDto>()
                .toChatMessageOrNull()
                ?: error("Pesan tersimpan tetapi payload tidak lengkap.")
        }

    suspend fun submitSellerReview(
        roomId: String,
        sellerId: String,
        rating: Int,
        comment: String?,
    ): SellerReview =
        withContext(ioDispatcher) {
            require(rating in 1..5) { "Rating harus antara 1 sampai 5." }
            val reviewerId = currentUserId()
            require(reviewerId != sellerId) { "Penjual tidak bisa memberi rating untuk dirinya sendiri." }

            client.from("reviews")
                .insert(
                    ReviewDto(
                        chatRoomId = roomId,
                        reviewerId = reviewerId,
                        sellerId = sellerId,
                        rating = rating,
                        comment = comment.cleanedOrNull(),
                    ),
                )
                .decodeSingle<ReviewDto>()
                .toSellerReviewOrNull()
                ?: error("Review tersimpan tetapi payload tidak lengkap.")
        }

    fun subscribeToRoomMessages(roomId: String): Flow<ChatMessage> = flow {
        val channel = client.channel("room-messages-$roomId")
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter(FilterOperation("room_id", FilterOperator.EQ, roomId))
        }.mapNotNull { action ->
            json.decodeFromJsonElement(MessageDto.serializer(), action.record)
                .toChatMessageOrNull()
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

    internal suspend fun findRoom(
        buyerId: String,
        sellerId: String,
        productId: String,
    ): ChatRoomDto? =
        client.from("chat_rooms")
            .select {
                filter {
                    eq("buyer_id", buyerId)
                    eq("seller_id", sellerId)
                    eq("product_id", productId)
                }
            }
            .decodeList<ChatRoomDto>()
            .firstOrNull()

    internal suspend fun buildRoomDetail(
        room: ChatRoomDto,
        product: ProductDto? = null,
    ): ChatRoomDetail {
        val resolvedProduct = product ?: client.from("products")
            .select {
                filter {
                    eq("id", room.productId)
                }
            }
            .decodeSingle<ProductDto>()
        val profilesById = loadProfiles(listOf(room.buyerId, room.sellerId))
        return ChatRoomDetail(
            id = room.id,
            buyerId = room.buyerId,
            sellerId = room.sellerId,
            productId = room.productId,
            productName = resolvedProduct.name,
            productImageUrl = loadImagesFor(listOf(room.productId))[room.productId]?.firstOrNull(),
            buyerName = profilesById[room.buyerId]?.name ?: "Pembeli",
            sellerName = profilesById[room.sellerId]?.name ?: "Penjual",
            sellerRatingAvg = profilesById[room.sellerId]?.ratingAvg ?: 0.0,
            sellerRatingCount = profilesById[room.sellerId]?.ratingCount ?: 0,
            currentUserId = currentUserId(),
            myReview = loadOwnReview(room.id),
        )
    }

    internal suspend fun loadProducts(productIds: List<String>): Map<String, ProductDto> {
        if (productIds.isEmpty()) return emptyMap()
        return client.from("products")
            .select {
                filter {
                    isIn("id", productIds.distinct())
                }
            }
            .decodeList<ProductDto>()
            .associateBy { it.id }
    }

    internal suspend fun loadProfiles(userIds: List<String>): Map<String, ProfileDto> {
        val ids = userIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        return client.from("profiles")
            .select {
                filter {
                    isIn("id", ids)
                }
            }
            .decodeList<ProfileDto>()
            .associateBy { it.id }
    }

    internal suspend fun loadImagesFor(productIds: List<String>): Map<String, List<String>> {
        val ids = productIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        return client.from("product_images")
            .select {
                filter {
                    isIn("product_id", ids)
                }
                order(column = "sort_order", order = Order.ASCENDING)
            }
            .decodeList<ProductImageDto>()
            .groupBy(
                keySelector = { it.productId },
                valueTransform = { it.imageUrl },
            )
    }

    internal suspend fun loadLatestMessages(rooms: List<ChatRoomDto>): Map<String, ChatMessage> {
        val messageIds = rooms.mapNotNull { it.lastMessageId }.distinct()
        if (messageIds.isEmpty()) return emptyMap()
        return client.from("messages")
            .select {
                filter {
                    isIn("id", messageIds)
                }
            }
            .decodeList<MessageDto>()
            .mapNotNull { it.toChatMessageOrNull() }
            .associateBy { it.roomId }
    }

    internal suspend fun currentUserId(): String =
        client.auth.currentUserOrNull()?.id
            ?: error("Session login tidak tersedia.")

    internal suspend fun loadOwnReview(roomId: String): SellerReview? =
        client.from("reviews")
            .select {
                filter {
                    eq("chat_room_id", roomId)
                    eq("reviewer_id", currentUserId())
                }
            }
            .decodeList<ReviewDto>()
            .firstOrNull()
            ?.toSellerReviewOrNull()

    private fun MessageDto.toChatMessageOrNull(): ChatMessage? {
        val messageId = id ?: return null
        val timestamp = createdAt ?: return null
        return ChatMessage(
            id = messageId,
            roomId = roomId,
            senderId = senderId,
            messageText = messageText,
            createdAt = timestamp,
        )
    }

    private fun ReviewDto.toSellerReviewOrNull(): SellerReview? {
        val reviewId = id ?: return null
        return SellerReview(
            id = reviewId,
            chatRoomId = chatRoomId,
            reviewerId = reviewerId,
            sellerId = sellerId,
            rating = rating,
            comment = comment,
        )
    }

    private fun String?.cleanedOrNull(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }
}
