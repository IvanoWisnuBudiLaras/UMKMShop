package com.application.umkmshop.data.product

interface ProductStorageService {
    suspend fun uploadProductImage(
        sellerId: String,
        productId: String,
        upload: ProductImageUpload,
    ): UploadedProductImage

    suspend fun deleteImage(path: String)
}
