package com.application.umkmshop.data.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class ChatRoomSummary(
    val id: String,
    val buyerId: String,
    val sellerId: String,
    val productId: String,
    val productName: String,
    val productImageUrl: String?,
    val otherUserName: String,
    val lastMessageAt: String?,
    val lastMessagePreview: String?,
    val isSellerView: Boolean,
)

data class ChatRoomDetail(
    val id: String,
    val buyerId: String,
    val sellerId: String,
    val productId: String,
    val productName: String,
    val productImageUrl: String?,
    val buyerName: String,
    val sellerName: String,
    val currentUserId: String,
    val sellerRatingAvg: Double = 0.0,
    val sellerRatingCount: Int = 0,
    val myReview: SellerReview? = null,
)

data class SellerReview(
    val id: String,
    val chatRoomId: String,
    val reviewerId: String,
    val sellerId: String,
    val rating: Int,
    val comment: String?,
)

data class ChatMessage(
    val id: String,
    val roomId: String,
    val senderId: String,
    val messageText: String,
    val createdAt: String,
) {
    fun isMine(currentUserId: String): Boolean = senderId == currentUserId
}

@Serializable
data class ChatRoomDto(
    @SerialName("id")
    val id: String,
    @SerialName("buyer_id")
    val buyerId: String,
    @SerialName("seller_id")
    val sellerId: String,
    @SerialName("product_id")
    val productId: String,
    @SerialName("last_message_at")
    val lastMessageAt: String? = null,
    @SerialName("last_message_id")
    val lastMessageId: String? = null,
    @SerialName("is_replied")
    val isReplied: Boolean = false,
    @SerialName("reminder_sent")
    val reminderSent: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
)

@Serializable
data class ChatRoomInsertDto(
    @SerialName("buyer_id")
    val buyerId: String,
    @SerialName("seller_id")
    val sellerId: String,
    @SerialName("product_id")
    val productId: String,
)

@Serializable
data class MessageDto(
    @SerialName("id")
    val id: String? = null,
    @SerialName("room_id")
    val roomId: String,
    @SerialName("sender_id")
    val senderId: String,
    @SerialName("message_text")
    val messageText: String,
    @SerialName("created_at")
    val createdAt: String? = null,
)

@Serializable
data class ReviewDto(
    @SerialName("id")
    val id: String? = null,
    @SerialName("chat_room_id")
    val chatRoomId: String,
    @SerialName("reviewer_id")
    val reviewerId: String,
    @SerialName("seller_id")
    val sellerId: String,
    @SerialName("rating")
    val rating: Int,
    @SerialName("comment")
    val comment: String? = null,
)
