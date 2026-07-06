package com.application.umkmshop.data.order

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val ORDER_STATUS_PENDING = "pending"
const val ORDER_STATUS_PAID = "paid"
const val ORDER_STATUS_EXPIRED = "expired"
const val ORDER_STATUS_CANCELLED = "cancelled"

data class OrderInput(
    val chatRoomId: String,
    val buyerId: String,
    val sellerId: String,
    val productId: String,
    val itemNote: String?,
    val weightGrams: Int?,
    val subtotal: Double,
    val shippingCost: Double = 0.0,
)

data class OrderTransaction(
    val id: String,
    val chatRoomId: String,
    val buyerId: String,
    val sellerId: String,
    val productId: String,
    val productName: String,
    val itemNote: String?,
    val weightGrams: Int?,
    val subtotal: Double,
    val shippingCost: Double,
    val totalAmount: Double,
    val status: String,
    val xenditInvoiceId: String?,
    val xenditInvoiceUrl: String?,
    val createdAt: String,
    val paidAt: String?,
    val expiredAt: String?,
    val isSellerView: Boolean,
)

@Serializable
data class OrderDto(
    @SerialName("id")
    val id: String? = null,
    @SerialName("chat_room_id")
    val chatRoomId: String,
    @SerialName("buyer_id")
    val buyerId: String,
    @SerialName("seller_id")
    val sellerId: String,
    @SerialName("product_id")
    val productId: String,
    @SerialName("item_note")
    val itemNote: String? = null,
    @SerialName("weight_grams")
    val weightGrams: Int? = null,
    @SerialName("subtotal")
    val subtotal: Double,
    @SerialName("shipping_cost")
    val shippingCost: Double = 0.0,
    @SerialName("total_amount")
    val totalAmount: Double? = null,
    @SerialName("status")
    val status: String = ORDER_STATUS_PENDING,
    @SerialName("xendit_invoice_id")
    val xenditInvoiceId: String? = null,
    @SerialName("xendit_invoice_url")
    val xenditInvoiceUrl: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("paid_at")
    val paidAt: String? = null,
    @SerialName("expired_at")
    val expiredAt: String? = null,
)

@Serializable
data class CreateXenditInvoiceRequest(
    @SerialName("order_id")
    val orderId: String,
)

@Serializable
data class CreateXenditInvoiceResponse(
    @SerialName("invoice_url")
    val invoiceUrl: String,
    @SerialName("order")
    val order: OrderDto? = null,
)

@Serializable
data class OrderInsertDto(
    @SerialName("chat_room_id")
    val chatRoomId: String,
    @SerialName("buyer_id")
    val buyerId: String,
    @SerialName("seller_id")
    val sellerId: String,
    @SerialName("product_id")
    val productId: String,
    @SerialName("item_note")
    val itemNote: String? = null,
    @SerialName("weight_grams")
    val weightGrams: Int? = null,
    @SerialName("subtotal")
    val subtotal: Double,
    @SerialName("shipping_cost")
    val shippingCost: Double,
)
