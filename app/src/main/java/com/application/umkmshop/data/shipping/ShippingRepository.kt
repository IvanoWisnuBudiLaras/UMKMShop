package com.application.umkmshop.data.shipping

import com.application.umkmshop.BuildConfig
import com.application.umkmshop.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ShippingRepository(
    private val httpClient: HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = SHIPPING_TIMEOUT_MS
            connectTimeoutMillis = SHIPPING_TIMEOUT_MS
            socketTimeoutMillis = SHIPPING_TIMEOUT_MS
        }
    },
    private val supabaseClient: SupabaseClient = SupabaseClientProvider.client
) {
    private val client
        get() = supabaseClient

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getProvinces(): List<WilayahItem> =
        withContext(Dispatchers.IO) {
            val response = httpClient.get("https://wilayah.id/api/provinces.json")
            if (response.status == HttpStatusCode.OK) {
                json.decodeFromString<WilayahResponse<WilayahItem>>(response.bodyAsText()).data
            } else emptyList()
        }

    suspend fun getRegencies(provinceCode: String): List<WilayahItem> =
        withContext(Dispatchers.IO) {
            val response = httpClient.get("https://wilayah.id/api/regencies/$provinceCode.json")
            if (response.status == HttpStatusCode.OK) {
                json.decodeFromString<WilayahResponse<WilayahItem>>(response.bodyAsText()).data
            } else emptyList()
        }

    suspend fun getDistricts(regencyCode: String): List<WilayahItem> =
        withContext(Dispatchers.IO) {
            val response = httpClient.get("https://wilayah.id/api/districts/$regencyCode.json")
            if (response.status == HttpStatusCode.OK) {
                json.decodeFromString<WilayahResponse<WilayahItem>>(response.bodyAsText()).data
            } else emptyList()
        }

    suspend fun getVillages(districtCode: String): List<WilayahItem> =
        withContext(Dispatchers.IO) {
            val response = httpClient.get("https://wilayah.id/api/villages/$districtCode.json")
            if (response.status == HttpStatusCode.OK) {
                json.decodeFromString<WilayahResponse<WilayahItem>>(response.bodyAsText()).data
            } else emptyList()
        }

    suspend fun searchVillages(query: String): List<VillageSearchResult> =
        withContext(Dispatchers.IO) {
            val cleaned = query.trim()
            require(cleaned.length >= 3) { "Ketik minimal 3 huruf kelurahan." }

            val response = callShippingFunction(
                ShippingFunctionRequest(
                    action = ACTION_SEARCH_VILLAGES,
                    query = cleaned,
                ),
            )
            json.decodeFromString<VillageSearchResponse>(response)
                .villages
                .map { it.toVillageSearchResult() }
        }

    suspend fun estimateShipping(
        originVillageCode: String,
        destinationVillageCode: String,
        weightGrams: Int,
    ): ShippingEstimate =
        withContext(Dispatchers.IO) {
            require(originVillageCode.matches(Regex("\\d{10}"))) {
                "Alamat penjual belum lengkap."
            }
            require(destinationVillageCode.matches(Regex("\\d{10}"))) {
                "Alamat pembeli belum lengkap."
            }
            require(weightGrams > 0) {
                "Berat barang harus lebih dari 0 gram."
            }

            val response = callShippingFunction(
                ShippingFunctionRequest(
                    action = ACTION_ESTIMATE_SHIPPING,
                    originVillageCode = originVillageCode,
                    destinationVillageCode = destinationVillageCode,
                    weightGrams = weightGrams,
                ),
            )
            json.decodeFromString<ShippingEstimateResponse>(response)
                .estimate
                .toShippingEstimate()
        }

    internal suspend fun callShippingFunction(request: ShippingFunctionRequest): String {
        val session = client.auth.currentSessionOrNull()
            ?: error("Session login tidak tersedia.")
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/shipping"

        val response = try {
            httpClient.post(endpoint) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
                header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                setBody(TextContent(json.encodeToString(request), ContentType.Application.Json))
            }
        } catch (_: HttpRequestTimeoutException) {
            error("Layanan ongkir lambat. Isi ongkir manual untuk lanjut.")
        }

        val body = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            error(body.extractMessage() ?: "Layanan ongkir belum tersedia. Isi ongkir manual untuk lanjut.")
        }
        return body
    }

    private fun String.extractMessage(): String? =
        runCatching {
            json.parseToJsonElement(this)
                .jsonObject["message"]
                ?.jsonPrimitive
                ?.content
        }.getOrNull()

    private companion object {
        const val ACTION_SEARCH_VILLAGES = "search_villages"
        const val ACTION_ESTIMATE_SHIPPING = "estimate_shipping"
        const val SHIPPING_TIMEOUT_MS = 6_000L
    }
}
