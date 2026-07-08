package com.application.umkmshop.data.product

import com.application.umkmshop.data.auth.ProfileDto
import com.application.umkmshop.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.http.ContentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class ProductRepository(
    private val supabaseClient: SupabaseClient = SupabaseClientProvider.client,
    private val storageService: ProductStorageService = SupabaseProductStorage(supabaseClient),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val client get() = supabaseClient

    suspend fun listBuyerProducts(
        filter: BuyerCatalogFilter,
        page: Int,
        pageSize: Int = BUYER_CATALOG_PAGE_SIZE,
    ): BuyerProductPage =
        withContext(ioDispatcher) {
            val safePage = page.coerceAtLeast(0)
            val offset = safePage * pageSize
            val requestedRange = offset.toLong()..(offset + pageSize).toLong()
            val sellerIdsByCity = filter.city.cleanedOrNull()?.let { city ->
                loadSellerIdsByCity(city)
            }
            if (sellerIdsByCity != null && sellerIdsByCity.isEmpty()) {
                return@withContext BuyerProductPage(
                    products = emptyList(),
                    page = safePage,
                    hasPreviousPage = safePage > 0,
                    hasNextPage = false,
                )
            }
            val products = client.from("products")
                .select {
                    filter {
                        eq("status", PRODUCT_STATUS_ACTIVE)
                        sellerIdsByCity?.let { sellerIds ->
                            isIn("seller_id", sellerIds)
                        }
                        filter.category.cleanedOrNull()?.let { category ->
                            eq("category", category)
                        }
                        filter.minPrice?.let { minPrice ->
                            gte("price", minPrice)
                        }
                        filter.maxPrice?.let { maxPrice ->
                            lte("price", maxPrice)
                        }
                        filter.searchQuery.cleanedOrNull()?.let { query ->
                            val pattern = "%${query.escapePostgrestLike()}%"
                            ilike("name", pattern)
                        }
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                    range(requestedRange)
                }
                .decodeList<ProductDto>()

            val visibleProducts = products.take(pageSize)
            val imagesByProduct = loadImagesFor(visibleProducts.map { it.id })
            val favoriteProductIds = loadFavoriteProductIdsFor(visibleProducts.map { it.id })
            val sellersById = loadProfiles(visibleProducts.map { it.sellerId })
            BuyerProductPage(
                products = visibleProducts.map { product ->
                    product.toBuyerProduct(
                        imageUrls = imagesByProduct[product.id].orEmpty(),
                        seller = sellersById[product.sellerId],
                        isFavorite = product.id in favoriteProductIds,
                    )
                },
                page = safePage,
                hasPreviousPage = safePage > 0,
                hasNextPage = products.size > pageSize,
            )
        }

    suspend fun getBuyerProductDetail(productId: String): BuyerProduct =
        withContext(ioDispatcher) {
            val product = client.from("products")
                .select {
                    filter {
                        eq("id", productId)
                        eq("status", PRODUCT_STATUS_ACTIVE)
                    }
                }
                .decodeSingle<ProductDto>()
            val seller = client.from("profiles")
                .select {
                    filter {
                        eq("id", product.sellerId)
                    }
                }
                .decodeSingle<ProfileDto>()

            product.toBuyerProduct(
                imageUrls = loadImages(product.id).map { it.imageUrl },
                seller = seller,
                isFavorite = product.id in loadFavoriteProductIdsFor(listOf(product.id)),
            )
        }

    suspend fun listFavoriteProducts(): List<BuyerProduct> =
        withContext(ioDispatcher) {
            val userId = currentUserId()
            val wishlistRows = client.from("wishlists")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<WishlistDto>()

            val orderedProductIds = wishlistRows.map { it.productId }.distinct()
            if (orderedProductIds.isEmpty()) return@withContext emptyList()

            val products = client.from("products")
                .select {
                    filter {
                        isIn("id", orderedProductIds)
                        eq("status", PRODUCT_STATUS_ACTIVE)
                    }
                }
                .decodeList<ProductDto>()
                .associateBy { it.id }
            val imagesByProduct = loadImagesFor(products.keys.toList())
            val sellersById = loadProfiles(products.values.map { it.sellerId })

            orderedProductIds.mapNotNull { productId ->
                val product = products[productId] ?: return@mapNotNull null
                product.toBuyerProduct(
                    imageUrls = imagesByProduct[productId].orEmpty(),
                    seller = sellersById[product.sellerId],
                    isFavorite = true,
                )
            }
        }

    suspend fun setProductFavorite(productId: String, isFavorite: Boolean) =
        withContext(ioDispatcher) {
            val userId = currentUserId()
            if (isFavorite) {
                client.from("wishlists")
                    .insert(
                        WishlistDto(
                            userId = userId,
                            productId = productId,
                        ),
                    )
            } else {
                client.from("wishlists")
                    .delete {
                        filter {
                            eq("user_id", userId)
                            eq("product_id", productId)
                        }
                    }
            }
        }

    suspend fun reportProduct(productId: String, reason: String) =
        withContext(ioDispatcher) {
            val userId = currentUserId()
            val cleanReason = reason.cleanedOrNull()
                ?: error("Alasan laporan wajib diisi.")

            client.from("product_reports")
                .insert(
                    ProductReportInsertDto(
                        productId = productId,
                        reporterId = userId,
                        reason = cleanReason,
                    ),
                )
        }

    suspend fun listAvailableCities(): List<String> =
        withContext(ioDispatcher) {
            client.from("profiles")
                .select()
                .decodeList<ProfileDto>()
                .mapNotNull { it.city.cleanedOrNull() }
                .distinctBy { it.lowercase() }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        }

    suspend fun getOwnCity(): String =
        withContext(ioDispatcher) {
            val userId = currentUserId()
            client.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<ProfileDto>()
                .city
                .orEmpty()
        }

    suspend fun updateOwnCity(city: String) =
        withContext(ioDispatcher) {
            val userId = currentUserId()
            client.from("profiles")
                .update(
                    ProfileCityUpdateDto(city = city.cleanedOrNull()?.toDisplayCity()),
                ) {
                    filter {
                        eq("id", userId)
                    }
                }
        }

    suspend fun listOwnProducts(): List<SellerProduct> =
        withContext(ioDispatcher) {
            val sellerId = currentUserId()
            val products = client.from("products")
                .select {
                    filter {
                        eq("seller_id", sellerId)
                    }
                }
                .decodeList<ProductDto>()

            val imagesByProduct = loadImagesFor(products.map { it.id })
            products.map { product ->
                product.toSellerProduct(imagesByProduct[product.id].orEmpty())
            }
        }

    suspend fun createProduct(input: SellerProductInput): SellerProduct =
        withContext(ioDispatcher) {
            val sellerId = currentUserId()
            val imageUpload = requireNotNull(input.imageUpload) {
                "Minimal satu foto produk wajib dipilih."
            }
            var uploadedImage: UploadedProductImage? = null
            val product = client.from("products")
                .insert(
                    ProductInsertDto(
                        sellerId = sellerId,
                        name = input.name.trim(),
                        price = input.price,
                        description = input.description.cleanedOrNull(),
                        category = input.category.cleanedOrNull(),
                        status = PRODUCT_STATUS_INACTIVE,
                    ),
                ) {
                    select() // WAJIB: Meminta data balik agar tidak EOF
                }
                .decodeSingle<ProductDto>()

            try {
                uploadedImage = storageService.uploadProductImage(
                    sellerId = sellerId,
                    productId = product.id,
                    upload = imageUpload,
                )
                client.from("product_images")
                    .insert(
                        ProductImageDto(
                            productId = product.id,
                            imageUrl = uploadedImage.url,
                            sortOrder = 0,
                        ),
                    )
                client.from("products")
                    .update(
                        {
                            set("status", PRODUCT_STATUS_ACTIVE)
                        },
                    ) {
                        filter {
                            eq("id", product.id)
                            eq("seller_id", sellerId)
                        }
                    }
            } catch (error: Throwable) {
                uploadedImage?.path?.let { runCatching { storageService.deleteImage(it) } }
                val deleteResult = runCatching { deleteOwnProduct(product.id, sellerId) }
                
                val userFriendlyError = if (error.message?.contains("bucket", ignoreCase = true) == true) {
                    Exception("Gagal mengunggah foto. Pastikan bucket 'product-images' sudah ada di Supabase.", error)
                } else if (error.message?.contains("permission", ignoreCase = true) == true) {
                    Exception("Gagal mengunggah foto. Periksa kebijakan RLS Storage Anda.", error)
                } else {
                    Exception("Produk tersimpan sebagai 'Nonaktif' karena gagal mengunggah foto: ${error.message}", error)
                }
                throw userFriendlyError
            }

            product.copy(status = PRODUCT_STATUS_ACTIVE)
                .toSellerProduct(listOf(uploadedImage.url))
        }

    suspend fun updateProduct(productId: String, input: SellerProductInput): SellerProduct =
        withContext(ioDispatcher) {
            val sellerId = currentUserId()
            client.from("products")
                .update(
                    {
                        set("name", input.name.trim())
                        set("price", input.price)
                        set("description", input.description.cleanedOrNull())
                        set("category", input.category.cleanedOrNull())
                    },
                ) {
                    filter {
                        eq("id", productId)
                        eq("seller_id", sellerId)
                    }
                }

            input.imageUpload?.let { upload ->
                val uploadedImage = try {
                    storageService.uploadProductImage(
                        sellerId = sellerId,
                        productId = productId,
                        upload = upload,
                    )
                } catch (error: Throwable) {
                    throw Exception("Gagal mengganti foto: ${error.message}", error)
                }
                
                try {
                    replacePrimaryImage(productId = productId, imageUrl = uploadedImage.url)
                } catch (error: Throwable) {
                    runCatching { storageService.deleteImage(uploadedImage.path) }
                    throw Exception("Gagal memperbarui data foto di database.", error)
                }
            }

            getOwnProduct(productId)
        }

    suspend fun deactivateProduct(productId: String): SellerProduct =
        withContext(ioDispatcher) {
            val sellerId = currentUserId()
            client.from("products")
                .update(
                    {
                        set("status", PRODUCT_STATUS_INACTIVE)
                    },
                ) {
                    filter {
                        eq("id", productId)
                        eq("seller_id", sellerId)
                    }
                }
            getOwnProduct(productId)
        }

    suspend fun deleteProduct(productId: String) =
        withContext(ioDispatcher) {
            val sellerId = currentUserId()
            
            // 1. Hapus gambar dari database (cascade akan hapus record)
            // 2. Hapus produk dari database
            client.from("products")
                .delete {
                    filter {
                        eq("id", productId)
                        eq("seller_id", sellerId)
                    }
                }
            
            // Catatan: File fisik di Supabase Storage sebaiknya juga dihapus.
            // Karena path kita products/{seller_id}/{product_id}/..., 
            // kita bisa mencoba menghapus folder tersebut jika storage SDK mendukungnya.
        }

    private suspend fun getOwnProduct(productId: String): SellerProduct {
        val sellerId = currentUserId()
        val product = client.from("products")
            .select {
                filter {
                    eq("id", productId)
                    eq("seller_id", sellerId)
                }
            }
            .decodeSingle<ProductDto>()
        return product.toSellerProduct(loadImages(productId).map { it.imageUrl })
    }

    internal suspend fun deleteOwnProduct(productId: String, sellerId: String) {
        client.from("products")
            .delete {
                filter {
                    eq("id", productId)
                    eq("seller_id", sellerId)
                }
            }
    }

    internal suspend fun replacePrimaryImage(productId: String, imageUrl: String) {
        val existing = loadImages(productId).minByOrNull { it.sortOrder }
        if (existing?.id != null) {
            val previousImagePath = existing.imageUrl.toProductImageStoragePath()
            client.from("product_images")
                .update(
                    {
                        set("image_url", imageUrl)
                        set("sort_order", 0)
                    },
                ) {
                    filter {
                        eq("id", existing.id)
                    }
                }
            if (previousImagePath != null && previousImagePath != imageUrl.toProductImageStoragePath()) {
                runCatching { storageService.deleteImage(previousImagePath) }
            }
        } else {
            client.from("product_images")
                .insert(
                    ProductImageDto(
                        productId = productId,
                        imageUrl = imageUrl,
                        sortOrder = 0,
                    ),
                )
        }
    }

    internal suspend fun loadImagesFor(productIds: List<String>): Map<String, List<String>> {
        if (productIds.isEmpty()) return emptyMap()

        return client.from("product_images")
            .select {
                filter {
                    isIn("product_id", productIds)
                }
            }
            .decodeList<ProductImageDto>()
            .sortedBy { it.sortOrder }
            .groupBy(
                keySelector = { it.productId },
                valueTransform = { it.imageUrl },
            )
    }

    internal suspend fun loadImages(productId: String): List<ProductImageDto> =
        client.from("product_images")
            .select {
                filter {
                    eq("product_id", productId)
                }
            }
            .decodeList<ProductImageDto>()
            .sortedBy { it.sortOrder }

    internal suspend fun loadFavoriteProductIdsFor(productIds: List<String>): Set<String> {
        if (productIds.isEmpty()) return emptySet()

        val userId = currentUserId()
        return client.from("wishlists")
            .select {
                filter {
                    eq("user_id", userId)
                    isIn("product_id", productIds)
                }
            }
            .decodeList<WishlistDto>()
            .map { it.productId }
            .toSet()
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

    internal suspend fun loadSellerIdsByCity(city: String): List<String> =
        client.from("profiles")
            .select {
                filter {
                    eq("city", city)
                }
            }
            .decodeList<ProfileDto>()
            .map { it.id }

    internal suspend fun currentUserId(): String =
        client.auth.currentUserOrNull()?.id
            ?: error("Session login tidak tersedia.")

    private fun ProductDto.toSellerProduct(imageUrls: List<String>): SellerProduct =
        SellerProduct(
            id = id,
            sellerId = sellerId,
            name = name,
            price = price,
            description = description,
            category = category,
            status = status,
            imageUrls = imageUrls,
        )

    private fun ProductDto.toBuyerProduct(
        imageUrls: List<String>,
        seller: ProfileDto?,
        isFavorite: Boolean = false,
    ): BuyerProduct =
        BuyerProduct(
            id = id,
            sellerId = sellerId,
            name = name,
            price = price,
            description = description,
            category = category,
            imageUrls = imageUrls,
            sellerName = seller?.name,
            sellerPhone = seller?.phone,
            sellerCity = seller?.city,
            sellerRatingAvg = seller?.ratingAvg ?: 0.0,
            sellerRatingCount = seller?.ratingCount ?: 0,
            isFavorite = isFavorite,
        )

    private fun String?.cleanedOrNull(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String.escapePostgrestLike(): String =
        replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    private fun String.toDisplayCity(): String =
        trim()
            .lowercase()
            .split(Regex("\\s+"))
            .joinToString(" ") { word ->
                word.replaceFirstChar { char -> char.titlecase() }
            }

    private fun String.toProductImageStoragePath(): String? {
        val marker = "/storage/v1/object/public/product-images/"
        return substringAfter(marker, missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.substringBefore('?')
    }

}

private const val BUYER_CATALOG_PAGE_SIZE = 12
