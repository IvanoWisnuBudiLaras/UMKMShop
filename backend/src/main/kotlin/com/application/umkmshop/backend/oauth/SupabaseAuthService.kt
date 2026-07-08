package com.application.umkmshop.backend.oauth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SupabaseAuthService(
    private val supabaseUrl: String,
    private val supabaseKey: String,
    private val jwtSecret: String
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun signIn(email: String, pass: String): String? {
        try {
            val response = httpClient.post("$supabaseUrl/auth/v1/token?grant_type=password") {
                header("apikey", supabaseKey)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("email", email)
                    put("password", pass)
                })
            }
            if (response.status != HttpStatusCode.OK) {
                println("Sign In Failed: ${response.status} - ${response.body<String>()}")
                return null
            }
            val body = response.body<JsonObject>()
            return body["access_token"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            println("Sign In Exception: ${e.message}")
            return null
        }
    }

    suspend fun signUp(email: String, pass: String, name: String): Boolean {
        try {
            val response = httpClient.post("$supabaseUrl/auth/v1/signup") {
                header("apikey", supabaseKey)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("email", email)
                    put("password", pass)
                    put("data", buildJsonObject { put("name", name) })
                })
            }
            if (response.status != HttpStatusCode.OK) {
                println("Sign Up Failed: ${response.status} - ${response.body<String>()}")
                return false
            }
            return true
        } catch (e: Exception) {
            println("Sign Up Exception: ${e.message}")
            return false
        }
    }

    fun verify(token: String): OAuthUser? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        if (!verifySignature("${parts[0]}.${parts[1]}", parts[2])) return null
        
        return try {
            val decodedPayload = String(Base64.getUrlDecoder().decode(parts[1]))
            val claims = json.parseToJsonElement(decodedPayload).jsonObject
            val userId = claims["sub"]?.jsonPrimitive?.contentOrNull ?: return null
            val email = claims["email"]?.jsonPrimitive?.contentOrNull ?: ""
            val metadata = claims["user_metadata"]?.jsonObject
            val name = metadata?.get("name")?.jsonPrimitive?.contentOrNull ?: "User"
            OAuthUser(id = userId, email = email, name = name)
        } catch (e: Exception) { null }
    }

    private fun verifySignature(data: String, signature: String): Boolean {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(jwtSecret.toByteArray(), "HmacSHA256"))
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.toByteArray()))
        return signature == expected
    }
}
