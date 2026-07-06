package com.application.umkmshop.data.product

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class ProductImageUpload(
    val bytes: ByteArray,
    val mimeType: String,
    val extension: String,
)

data class UploadedProductImage(
    val path: String,
    val url: String,
)

data class SellerProductInput(
    val name: String,
    val price: Double,
    val description: String?,
    val category: String?,
    val imageUpload: ProductImageUpload?,
)

data class SellerProduct(
    val id: String,
    val sellerId: String,
    val name: String,
    val price: Double,
    val description: String?,
    val category: String?,
    val status: String,
    val imageUrls: List<String>,
) {
    val isActive: Boolean = status == PRODUCT_STATUS_ACTIVE
}

data class BuyerCatalogFilter(
    val searchQuery: String = "",
    val category: String = "",
    val city: String = "",
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
)

data class BuyerProductPage(
    val products: List<BuyerProduct>,
    val page: Int,
    val hasPreviousPage: Boolean,
    val hasNextPage: Boolean,
)

data class BuyerProduct(
    val id: String,
    val sellerId: String,
    val name: String,
    val price: Double,
    val description: String?,
    val category: String?,
    val imageUrls: List<String>,
    val sellerName: String? = null,
    val sellerPhone: String? = null,
    val sellerCity: String? = null,
    val sellerRatingAvg: Double = 0.0,
    val sellerRatingCount: Int = 0,
    val isFavorite: Boolean = false,
)

@Serializable
data class ProfileCityUpdateDto(
    @SerialName("city")
    val city: String?,
)

@Serializable
data class ProductDto(
    @SerialName("id")
    val id: String,
    @SerialName("seller_id")
    val sellerId: String,
    @SerialName("name")
    val name: String,
    @SerialName("price")
    val price: Double,
    @SerialName("description")
    val description: String? = null,
    @SerialName("category")
    val category: String? = null,
    @SerialName("status")
    val status: String = PRODUCT_STATUS_ACTIVE,
)

@Serializable
data class ProductInsertDto(
    @SerialName("seller_id")
    val sellerId: String,
    @SerialName("name")
    val name: String,
    @SerialName("price")
    val price: Double,
    @SerialName("description")
    val description: String? = null,
    @SerialName("category")
    val category: String? = null,
    @SerialName("status")
    val status: String = PRODUCT_STATUS_ACTIVE,
)

@Serializable
data class ProductImageDto(
    @SerialName("id")
    val id: String? = null,
    @SerialName("product_id")
    val productId: String,
    @SerialName("image_url")
    val imageUrl: String,
    @SerialName("sort_order")
    val sortOrder: Int = 0,
)

@Serializable
data class WishlistDto(
    @SerialName("id")
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("product_id")
    val productId: String,
    @SerialName("created_at")
    val createdAt: String? = null,
)

@Serializable
data class ProductReportInsertDto(
    @SerialName("product_id")
    val productId: String,
    @SerialName("reporter_id")
    val reporterId: String,
    @SerialName("reason")
    val reason: String,
)

const val PRODUCT_STATUS_ACTIVE = "active"
const val PRODUCT_STATUS_INACTIVE = "inactive"
