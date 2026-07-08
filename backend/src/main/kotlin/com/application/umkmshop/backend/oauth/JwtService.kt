package com.application.umkmshop.backend.oauth

import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class JwtService(
    private val issuer: String,
    private val store: OAuthStore,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun jwksJson(): String {
        val key = store.getActiveSigningKey() ?: throw IllegalStateException("No active signing key found")
        val publicKey = decodePublicKey(key.publicKeyPem)
        return """{"keys":[${publicKey.toJwk(key.kid)}]}"""
    }

    fun issueAccessToken(user: OAuthUser, clientId: String, scope: String, ttl: Duration): String {
        val now = Instant.ofEpochMilli(clock.millis())
        return sign(
            mapOf(
                "iss" to issuer,
                "sub" to user.id,
                "aud" to clientId,
                "client_id" to clientId,
                "scope" to scope,
                "email" to user.email,
                "name" to user.name,
                "iat" to now.epochSecond,
                "exp" to now.plus(ttl).epochSecond,
                "typ" to "access_token",
            ),
        )
    }

    fun issueIdToken(user: OAuthUser, clientId: String, nonce: String?, ttl: Duration): String {
        val now = Instant.ofEpochMilli(clock.millis())
        val claims = mutableMapOf<String, Any>(
            "iss" to issuer,
            "sub" to user.id,
            "aud" to clientId,
            "email" to user.email,
            "email_verified" to true,
            "name" to user.name,
            "iat" to now.epochSecond,
            "exp" to now.plus(ttl).epochSecond,
        )
        if (!nonce.isNullOrBlank()) claims["nonce"] = nonce
        return sign(claims)
    }

    fun verify(token: String, expectedType: String? = null): JsonObject {
        val parts = token.split(".")
        if (parts.size != 3) throw OAuthError("invalid_token", "JWT must have three parts.")
        
        val claims = json.parseToJsonElement(String(java.util.Base64.getUrlDecoder().decode(parts[1]))).jsonObject
        val kid = "local-rsa-1" // Simplify for now or parse from header
        
        val key = store.getActiveSigningKey() ?: throw OAuthError("server_error", "No active signing key")
        val publicKey = decodePublicKey(key.publicKeyPem)

        val signingInput = "${parts[0]}.${parts[1]}"
        if (!rsaVerifySha256(publicKey, signingInput, parts[2])) {
            throw OAuthError("invalid_token", "JWT signature is invalid.")
        }
        
        val exp = claims["exp"]?.jsonPrimitive?.longOrNull
            ?: throw OAuthError("invalid_token", "JWT expiry is missing.")
        if (exp <= Instant.ofEpochMilli(clock.millis()).epochSecond) {
            throw OAuthError("invalid_token", "JWT is expired.")
        }
        if (claims["iss"]?.jsonPrimitive?.contentOrNull != issuer) {
            throw OAuthError("invalid_token", "JWT issuer is invalid.")
        }
        if (expectedType != null && claims["typ"]?.jsonPrimitive?.contentOrNull != expectedType) {
            throw OAuthError("invalid_token", "JWT type is invalid.")
        }
        return claims
    }

    private fun sign(claims: Map<String, Any>): String {
        val key = store.getActiveSigningKey() ?: throw IllegalStateException("No active signing key found")
        val privateKey = decodePrivateKey(key.privateKeyPem)
        
        val header = """{"alg":"RS256","typ":"JWT","kid":"${key.kid}"}"""
        val payload = buildJsonObject(claims)
        val signingInput = "${base64Url(header.toByteArray(Charsets.UTF_8))}.${base64Url(payload.toByteArray(Charsets.UTF_8))}"
        return "$signingInput.${rsaSignSha256(privateKey, signingInput)}"
    }

    private fun buildJsonObject(values: Map<String, Any>): String =
        values.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            val encodedValue = when (value) {
                is Number -> value.toString()
                is Boolean -> value.toString()
                else -> "\"${escapeJson(value.toString())}\""
            }
            "\"${escapeJson(key)}\":$encodedValue"
        }
}

fun escapeJson(value: String): String =
    buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
