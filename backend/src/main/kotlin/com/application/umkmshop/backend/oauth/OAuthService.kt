package com.application.umkmshop.backend.oauth

import java.net.URLEncoder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.JsonObject

class OAuthService(
    private val issuer: String,
    private val store: OAuthStore,
    private val jwtService: JwtService,
    private val clock: Clock,
    private val codeTtl: Duration = Duration.ofSeconds(60), // RFC 6749 standard (short lived)
    private val tokenTtl: Duration = Duration.ofMinutes(15),
    private val refreshTokenTtl: Duration = Duration.ofDays(30),
) {
    fun discoveryJson(): String =
        """{"issuer":"$issuer","authorization_endpoint":"$issuer/oauth/authorize","token_endpoint":"$issuer/oauth/token","userinfo_endpoint":"$issuer/oauth/userinfo","jwks_uri":"$issuer/oauth/jwks.json","revocation_endpoint":"$issuer/oauth/revoke","response_types_supported":["code"],"grant_types_supported":["authorization_code","refresh_token"],"code_challenge_methods_supported":["S256"],"token_endpoint_auth_methods_supported":["none","client_secret_post"],"scopes_supported":["openid","email","profile"],"id_token_signing_alg_values_supported":["RS256"]}"""

    fun validateAuthorizationRequest(request: AuthorizationRequest): OAuthClient {
        if (request.responseType != "code") throw OAuthError("unsupported_response_type", "Only code is supported.")
        val client = store.findClient(request.clientId) ?: throw OAuthError("invalid_client", "Unknown client.")
        
        // SECURITY: Exact matching for redirect_uri
        if (request.redirectUri !in client.redirectUris) {
            throw OAuthError("invalid_request", "redirect_uri mismatch.")
        }
        
        // SECURITY: PKCE mandatory
        if (request.codeChallenge.isNullOrBlank() || request.codeChallengeMethod != "S256") {
            throw OAuthError("invalid_request", "PKCE with S256 is required.")
        }
        
        return client
    }

    fun approveAuthorization(request: AuthorizationRequest, user: OAuthUser): String {
        validateAuthorizationRequest(request)
        val code = randomToken()
        store.saveAuthorizationCode(
            StoredAuthorizationCode(
                code = code,
                clientId = request.clientId,
                userId = user.id,
                redirectUri = request.redirectUri,
                codeChallenge = request.codeChallenge!!,
                scope = request.scope,
                expiresAt = now().plus(codeTtl)
            )
        )
        return code
    }

    fun exchangeAuthorizationCode(
        clientId: String,
        clientSecret: String?,
        code: String,
        redirectUri: String,
        codeVerifier: String?
    ): TokenResponse {
        val client = authenticateClient(clientId, clientSecret)
        val stored = store.findAuthorizationCode(code) ?: throw OAuthError("invalid_grant", "Invalid code.")

        // SECURITY: Atomic Mark-as-Used
        if (!store.markCodeAsUsed(code)) {
            throw OAuthError("invalid_grant", "Code already used (potential replay attack).")
        }

        if (stored.clientId != client.clientId || stored.redirectUri != redirectUri) {
            throw OAuthError("invalid_grant", "Client or redirect_uri mismatch.")
        }
        if (stored.expiresAt <= now()) throw OAuthError("invalid_grant", "Code expired.")

        // PKCE Check
        val verifier = codeVerifier ?: throw OAuthError("invalid_request", "code_verifier required.")
        if (!constantTimeEquals(sha256Base64Url(verifier), stored.codeChallenge)) {
            throw OAuthError("invalid_grant", "PKCE verification failed.")
        }

        // Fetch User (Profiles and Auth Users already joined in store)
        // For learning, we assume user still exists if token issue succeeds
        return issueTokens(client.clientId, stored.userId, stored.scope)
    }

    fun refresh(clientId: String, clientSecret: String?, refreshToken: String): TokenResponse {
        val client = authenticateClient(clientId, clientSecret)
        val rtHash = tokenHash(refreshToken)
        val stored = store.findRefreshTokenByHash(rtHash) ?: throw OAuthError("invalid_grant", "Invalid refresh token.")

        if (stored.clientId != client.clientId) throw OAuthError("invalid_grant", "Client mismatch.")
        
        // SECURITY: Rotation & Theft Detection
        if (stored.revoked) {
            store.revokeRefreshTokenChain(rtHash) // Revoke entire chain
            throw OAuthError("invalid_grant", "Refresh token was revoked (theft detected).")
        }

        if (stored.expiresAt <= now()) throw OAuthError("invalid_grant", "Refresh token expired.")

        store.revokeRefreshToken(rtHash) // Revoke old one
        return issueTokens(client.clientId, stored.userId, stored.scope, rotatedFrom = rtHash)
    }

    private fun issueTokens(clientId: String, userId: String, scope: String, rotatedFrom: String? = null): TokenResponse {
        val user = store.findUserById(userId) ?: throw OAuthError("server_error", "User no longer exists.")
        
        val accessToken = jwtService.issueAccessToken(user, clientId, scope, tokenTtl)
        val refreshToken = randomToken()
        val rtHash = tokenHash(refreshToken)

        store.saveRefreshToken(
            StoredRefreshToken(
                tokenHash = rtHash,
                clientId = clientId,
                userId = userId,
                scope = scope,
                rotatedFrom = rotatedFrom,
                expiresAt = now().plus(refreshTokenTtl)
            )
        )

        val idToken = if ("openid" in scope) jwtService.issueIdToken(user, clientId, null, tokenTtl) else null
        
        return TokenResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            idToken = idToken,
            expiresIn = tokenTtl.seconds,
            scope = scope
        )
    }

    private fun authenticateClient(clientId: String, clientSecret: String?): OAuthClient {
        val client = store.findClient(clientId) ?: throw OAuthError("invalid_client", "Unknown client.")
        // Public clients don't have secrets, but PKCE (validated above) handles them.
        // Confidential clients MUST have secret.
        if (client.clientSecretHash.isNotEmpty()) {
            val secret = clientSecret ?: throw OAuthError("invalid_client", "Client secret required.")
            if (!constantTimeEquals(client.clientSecretHash, tokenHash(secret))) {
                throw OAuthError("invalid_client", "Invalid client secret.")
            }
        }
        return client
    }

    fun jwksJson(): String = jwtService.jwksJson()

    fun verifyAccessToken(token: String): JsonObject =
        jwtService.verify(token, expectedType = "access_token")

    fun findUserById(userId: String): OAuthUser? =
        store.findUserById(userId)

    fun enqueueTestNotification(userId: String) {
        val payload = """{"type":"new_message","to_user_id":"$userId","preview_text":"Haloo! Ini adalah test notifikasi dari OAuth Server UMKMShop."}"""
        store.enqueueNotification(userId, payload)
    }

    fun revoke(token: String): Boolean {
        val rtHash = tokenHash(token)
        val rt = store.findRefreshTokenByHash(rtHash)
        if (rt != null) {
            store.revokeRefreshToken(rtHash)
            return true
        }
        return false
    }

    fun redirectWithCode(redirectUri: String, code: String, state: String?): String =
        buildRedirect(redirectUri, mapOf("code" to code) + state?.let { mapOf("state" to it) }.orEmpty())

    fun redirectWithError(redirectUri: String, error: String, state: String?): String =
        buildRedirect(redirectUri, mapOf("error" to error) + state?.let { mapOf("state" to it) }.orEmpty())

    private fun now(): Instant = Instant.ofEpochMilli(clock.millis())
}

private fun buildRedirect(uri: String, params: Map<String, String>): String {
    val separator = if (uri.contains("?")) "&" else "?"
    val query = params.entries.joinToString("&") { (key, value) ->
        "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
    }
    return "$uri$separator$query"
}
