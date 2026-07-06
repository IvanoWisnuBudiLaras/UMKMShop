package com.application.umkmshop.backend.oauth

import java.time.Instant

enum class ClientType {
    Public,
    Confidential,
}

data class OAuthClient(
    val clientId: String,
    val clientName: String,
    val clientType: ClientType,
    val clientSecretHash: String? = null,
    val redirectUris: Set<String>,
    val allowedScopes: Set<String> = setOf("openid", "email", "profile"),
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
    val codeHash: String,
    val clientId: String,
    val user: OAuthUser,
    val redirectUri: String,
    val scope: Set<String>,
    val codeChallenge: String,
    val nonce: String?,
    val expiresAt: Instant,
    var consumedAt: Instant? = null,
)

data class StoredRefreshToken(
    val id: String,
    val tokenHash: String,
    val clientId: String,
    val user: OAuthUser,
    val scope: Set<String>,
    val expiresAt: Instant,
    var revokedAt: Instant? = null,
    var replacedBy: String? = null,
)

data class AccessGrant(
    val clientId: String,
    val user: OAuthUser,
    val scope: Set<String>,
    val expiresAt: Instant,
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
