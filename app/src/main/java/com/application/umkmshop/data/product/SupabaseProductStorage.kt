package com.application.umkmshop.data.product

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.util.UUID

class SupabaseProductStorage(
    private val supabaseClient: SupabaseClient
) : ProductStorageService {

    override suspend fun uploadProductImage(
        sellerId: String,
        productId: String,
        upload: ProductImageUpload
    ): UploadedProductImage {
        val filename = "${UUID.randomUUID()}.${upload.extension}"
        val path = "products/$sellerId/$productId/$filename"
        supabaseClient.storage["product-images"].upload(path, upload.bytes) {
            upsert = false
            contentType = ContentType.parse(upload.mimeType)
        }
        return UploadedProductImage(
            path = path,
            url = supabaseClient.storage["product-images"].publicUrl(path),
        )
    }

    override suspend fun deleteImage(path: String) {
        supabaseClient.storage["product-images"].delete(path)
    }
}
