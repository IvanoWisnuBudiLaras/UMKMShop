package com.application.umkmshop.backend.oauth

import java.time.Instant

data class OAuthClient(
    val clientId: String,
    val clientSecretHash: String,
    val redirectUris: Set<String>,
    val clientName: String,
)

data class OAuthUser(
    val id: String,
    val email: String,
    val name: String,
    val avatarUrl: String? = null,
)

data class AuthorizationRequest(
    val responseType: String,
    val clientId: String,
    val redirectUri: String,
    val scope: String,
    val state: String?,
    val codeChallenge: String?,
    val codeChallengeMethod: String?,
    val nonce: String?,
)

data class StoredAuthorizationCode(
    val code: String,
    val clientId: String,
    val userId: String,
    val redirectUri: String,
    val codeChallenge: String,
    val codeChallengeMethod: String = "S256",
    val scope: String,
    val expiresAt: Instant,
    val used: Boolean = false,
)

data class StoredRefreshToken(
    val tokenHash: String,
    val clientId: String,
    val userId: String,
    val scope: String,
    val rotatedFrom: String? = null,
    val revoked: Boolean = false,
    val expiresAt: Instant,
)

data class SigningKey(
    val kid: String,
    val privateKeyPem: String,
    val publicKeyPem: String,
    val active: Boolean = true,
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String?,
    val expiresIn: Long,
    val scope: String,
    val tokenType: String = "Bearer",
) {
    fun toJson(): String {
        val idTokenPart = idToken?.let { ""","id_token":"$it"""" } ?: ""
        return """{"access_token":"$accessToken","token_type":"$tokenType","expires_in":$expiresIn,"refresh_token":"$refreshToken","scope":"$scope"$idTokenPart}"""
    }
}

class OAuthError(
    val code: String,
    override val message: String,
) : RuntimeException(message)
