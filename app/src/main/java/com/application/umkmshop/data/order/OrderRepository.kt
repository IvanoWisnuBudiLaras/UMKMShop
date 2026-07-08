package com.application.umkmshop.data.order

import com.application.umkmshop.BuildConfig
import com.application.umkmshop.data.product.ProductDto
import com.application.umkmshop.data.profile.ProfileAddressDto
import com.application.umkmshop.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OrderRepository(
    private val httpClient: HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = XENDIT_FUNCTION_TIMEOUT_MS
            connectTimeoutMillis = XENDIT_FUNCTION_TIMEOUT_MS
            socketTimeoutMillis = XENDIT_FUNCTION_TIMEOUT_MS
        }
    },
    private val supabaseClient: SupabaseClient = SupabaseClientProvider.client,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val client
        get() = supabaseClient

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createOrder(input: OrderInput): OrderTransaction =
        withContext(ioDispatcher) {
            require(input.sellerId == currentUserId()) {
                "Hanya penjual di chat ini yang bisa membuat invoice."
            }
            require(input.subtotal >= 0) {
                "Subtotal tidak boleh negatif."
            }
            require(input.shippingCost >= 0) {
                "Ongkir tidak boleh negatif."
            }
            require(input.weightGrams == null || input.weightGrams > 0) {
                "Berat barang harus lebih dari 0 gram."
            }

            val inserted = client.from("orders")
                .insert(
                    OrderInsertDto(
                        chatRoomId = input.chatRoomId,
                        buyerId = input.buyerId,
                        sellerId = input.sellerId,
                        productId = input.productId,
                        itemNote = input.itemNote.cleanedOrNull(),
                        weightGrams = input.weightGrams,
                        subtotal = input.subtotal,
                        shippingCost = input.shippingCost,
                    ),
                ) {
                    select()
                }
                .decodeSingle<OrderDto>()
            val orderId = inserted.id ?: error("Order tersimpan tetapi id tidak tersedia.")
            val invoiced = createXenditInvoice(orderId).order ?: client.from("orders")
                .select {
                    filter {
                        eq("id", orderId)
                    }
                }
                .decodeSingle<OrderDto>()

            val products = loadProducts(listOf(invoiced.productId))
            invoiced.toOrderTransaction(
                currentUserId = input.sellerId,
                productName = products[invoiced.productId]?.name ?: "Produk",
            ) ?: error("Order tersimpan tetapi payload tidak lengkap.")
        }

    suspend fun createXenditInvoice(orderId: String): CreateXenditInvoiceResponse =
        withContext(ioDispatcher) {
            val session = client.auth.currentSessionOrNull()
                ?: error("Session login tidak tersedia.")
            val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/create-xendit-invoice"

            val response = try {
                httpClient.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
                    header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                    setBody(
                        TextContent(
                            json.encodeToString(CreateXenditInvoiceRequest(orderId = orderId)),
                            ContentType.Application.Json,
                        ),
                    )
                }
            } catch (_: HttpRequestTimeoutException) {
                error("Pembuatan invoice Xendit timeout. Coba lagi.")
            }

            val body = response.bodyAsText()
            if (response.status != HttpStatusCode.OK) {
                error(body.extractMessage() ?: "Gagal membuat invoice Xendit.")
            }
            json.decodeFromString<CreateXenditInvoiceResponse>(body)
        }

    suspend fun listOrders(): List<OrderTransaction> =
        withContext(ioDispatcher) {
            val currentUserId = currentUserId()
            val buyerOrders = client.from("orders")
                .select {
                    filter {
                        eq("buyer_id", currentUserId)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<OrderDto>()
            val sellerOrders = client.from("orders")
                .select {
                    filter {
                        eq("seller_id", currentUserId)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<OrderDto>()

            val orders = (buyerOrders + sellerOrders)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt.orEmpty() }
            val products = loadProducts(orders.map { it.productId })

            orders.mapNotNull { order ->
                order.toOrderTransaction(
                    currentUserId = currentUserId,
                    productName = products[order.productId]?.name ?: "Produk",
                )
            }
        }

    suspend fun getParticipantVillageCodes(buyerId: String, sellerId: String): Pair<String?, String?> =
        withContext(ioDispatcher) {
            val profiles = client.from("profiles")
                .select {
                    filter {
                        isIn("id", listOf(buyerId, sellerId).distinct())
                    }
                }
                .decodeList<ProfileAddressDto>()
                .associateBy { it.id }

            profiles[sellerId]?.villageCode to profiles[buyerId]?.villageCode
        }

    internal suspend fun loadProducts(productIds: List<String>): Map<String, ProductDto> {
        val ids = productIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        return client.from("products")
            .select {
                filter {
                    isIn("id", ids)
                }
            }
            .decodeList<ProductDto>()
            .associateBy { it.id }
    }

    internal suspend fun currentUserId(): String =
        client.auth.currentUserOrNull()?.id
            ?: error("Session login tidak tersedia.")

    private fun OrderDto.toOrderTransaction(
        currentUserId: String,
        productName: String,
    ): OrderTransaction? {
        val orderId = id ?: return null
        val timestamp = createdAt ?: return null
        return OrderTransaction(
            id = orderId,
            chatRoomId = chatRoomId,
            buyerId = buyerId,
            sellerId = sellerId,
            productId = productId,
            productName = productName,
            itemNote = itemNote,
            weightGrams = weightGrams,
            subtotal = subtotal,
            shippingCost = shippingCost,
            totalAmount = totalAmount ?: subtotal + shippingCost,
            status = status,
            xenditInvoiceId = xenditInvoiceId,
            xenditInvoiceUrl = xenditInvoiceUrl,
            createdAt = timestamp,
            paidAt = paidAt,
            expiredAt = expiredAt,
            isSellerView = currentUserId == sellerId,
        )
    }

    private fun String?.cleanedOrNull(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String.extractMessage(): String? =
        runCatching {
            json.parseToJsonElement(this)
                .jsonObject["message"]
                ?.jsonPrimitive
                ?.content
        }.getOrNull()

    private companion object {
        const val XENDIT_FUNCTION_TIMEOUT_MS = 15_000L
    }
}
