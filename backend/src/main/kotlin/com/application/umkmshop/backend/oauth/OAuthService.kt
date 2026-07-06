package com.application.umkmshop.backend.oauth

import java.net.URLEncoder
import java.time.Clock
import java.time.Duration
import java.time.Instant

class OAuthService(
    private val issuer: String,
    private val store: OAuthStore,
    private val jwtService: JwtService,
    private val clock: Clock,
    private val tokenPepper: String,
    private val codeTtl: Duration = Duration.ofMinutes(5),
    private val tokenTtl: Duration = Duration.ofMinutes(15),
    private val refreshTokenTtl: Duration = Duration.ofDays(30),
) {
    fun discoveryJson(): String =
        """{"issuer":"$issuer","authorization_endpoint":"$issuer/oauth/authorize","token_endpoint":"$issuer/oauth/token","userinfo_endpoint":"$issuer/oauth/userinfo","jwks_uri":"$issuer/oauth/jwks.json","revocation_endpoint":"$issuer/oauth/revoke","response_types_supported":["code"],"grant_types_supported":["authorization_code","refresh_token"],"code_challenge_methods_supported":["S256"],"token_endpoint_auth_methods_supported":["none","client_secret_post"],"scopes_supported":["openid","email","profile"],"id_token_signing_alg_values_supported":["RS256"]}"""

    fun validateAuthorizationRequest(request: AuthorizationRequest): OAuthClient {
        if (request.responseType != "code") {
            throw OAuthError("unsupported_response_type", "Only response_type=code is supported.")
        }
        val client = store.findClient(request.clientId)
            ?: throw OAuthError("invalid_client", "Unknown client_id.")
        if (request.redirectUri !in client.redirectUris) {
            throw OAuthError("invalid_request", "redirect_uri must exactly match a registered URI.")
        }
        val requestedScopes = parseScopes(request.scope)
        if (!client.allowedScopes.containsAll(requestedScopes)) {
            throw OAuthError("invalid_scope", "Requested scope is not allowed for this client.")
        }
        if (request.codeChallenge.isNullOrBlank() || request.codeChallengeMethod != "S256") {
            throw OAuthError("invalid_request", "Public clients must use PKCE S256.")
        }
        return client
    }

    fun approveAuthorization(request: AuthorizationRequest, user: OAuthUser): String {
        validateAuthorizationRequest(request)
        val code = randomToken()
        store.saveAuthorizationCode(
            StoredAuthorizationCode(
                codeHash = tokenHash(code, tokenPepper),
                clientId = request.clientId,
                user = user,
                redirectUri = request.redirectUri,
                scope = parseScopes(request.scope),
                codeChallenge = requireNotNull(request.codeChallenge),
                nonce = request.nonce,
                expiresAt = now().plus(codeTtl),
            ),
        )
        return code
    }

    fun exchangeAuthorizationCode(
        clientId: String,
        clientSecret: String?,
        code: String,
        redirectUri: String,
        codeVerifier: String?,
    ): TokenResponse {
        val client = authenticateClient(clientId, clientSecret)
        val storedCode = store.findAuthorizationCodeByHash(tokenHash(code, tokenPepper))
            ?: throw OAuthError("invalid_grant", "Authorization code is invalid.")
        if (storedCode.clientId != client.clientId || storedCode.redirectUri != redirectUri) {
            throw OAuthError("invalid_grant", "Authorization code does not belong to this client or redirect URI.")
        }
        if (storedCode.consumedAt != null) {
            throw OAuthError("invalid_grant", "Authorization code was already used.")
        }
        if (storedCode.expiresAt <= now()) {
            throw OAuthError("invalid_grant", "Authorization code is expired.")
        }
        val verifier = codeVerifier ?: throw OAuthError("invalid_request", "code_verifier is required.")
        if (!constantTimeEquals(sha256Base64Url(verifier), storedCode.codeChallenge)) {
            throw OAuthError("invalid_grant", "PKCE verifier does not match.")
        }
        storedCode.consumedAt = now()
        return issueTokens(client.clientId, storedCode.user, storedCode.scope, storedCode.nonce)
    }

    fun refresh(clientId: String, clientSecret: String?, refreshToken: String): TokenResponse {
        val client = authenticateClient(clientId, clientSecret)
        val oldToken = store.findRefreshTokenByHash(tokenHash(refreshToken, tokenPepper))
            ?: throw OAuthError("invalid_grant", "Refresh token is invalid.")
        if (oldToken.clientId != client.clientId) {
            throw OAuthError("invalid_grant", "Refresh token does not belong to this client.")
        }
        if (oldToken.revokedAt != null || oldToken.replacedBy != null) {
            throw OAuthError("invalid_grant", "Refresh token is revoked.")
        }
        if (oldToken.expiresAt <= now()) {
            throw OAuthError("invalid_grant", "Refresh token is expired.")
        }
        oldToken.revokedAt = now()
        return issueTokens(client.clientId, oldToken.user, oldToken.scope, nonce = null).also { response ->
            oldToken.replacedBy = store.findRefreshTokenByHash(tokenHash(response.refreshToken, tokenPepper))?.id
        }
    }

    fun userInfo(accessToken: String): String {
        val claims = jwtService.verify(accessToken, expectedType = "access_token")
        val grant = store.findAccessGrant(tokenHash(accessToken, tokenPepper))
            ?: throw OAuthError("invalid_token", "Access token grant is not active.")
        if (grant.expiresAt <= now()) {
            throw OAuthError("invalid_token", "Access token grant is expired.")
        }
        val scope = grant.scope
        val fields = mutableListOf("\"sub\":\"${escapeJson(grant.user.id)}\"")
        if ("email" in scope) fields += "\"email\":\"${escapeJson(grant.user.email)}\""
        if ("profile" in scope) {
            fields += "\"name\":\"${escapeJson(grant.user.name)}\""
            grant.user.avatarUrl?.let { fields += "\"picture\":\"${escapeJson(it)}\"" }
        }
        return "{${fields.joinToString(",")}}"
    }

    fun revoke(token: String): Boolean {
        val refresh = store.findRefreshTokenByHash(tokenHash(token, tokenPepper)) ?: return false
        refresh.revokedAt = now()
        return true
    }

    fun jwksJson(): String = jwtService.jwksJson()

    fun redirectWithCode(redirectUri: String, code: String, state: String?): String =
        buildRedirect(redirectUri, mapOf("code" to code) + state?.let { mapOf("state" to it) }.orEmpty())

    fun redirectWithError(redirectUri: String, error: String, state: String?): String =
        buildRedirect(redirectUri, mapOf("error" to error) + state?.let { mapOf("state" to it) }.orEmpty())

    private fun issueTokens(clientId: String, user: OAuthUser, scope: Set<String>, nonce: String?): TokenResponse {
        val accessToken = jwtService.issueAccessToken(user, clientId, scope, tokenTtl)
        val refreshToken = randomToken()
        store.saveAccessGrant(
            tokenHash(accessToken, tokenPepper),
            AccessGrant(clientId, user, scope, now().plus(tokenTtl)),
        )
        store.saveRefreshToken(
            StoredRefreshToken(
                id = randomToken(16),
                tokenHash = tokenHash(refreshToken, tokenPepper),
                clientId = clientId,
                user = user,
                scope = scope,
                expiresAt = now().plus(refreshTokenTtl),
            ),
        )
        val idToken = if ("openid" in scope) jwtService.issueIdToken(user, clientId, nonce, tokenTtl) else null
        return TokenResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            idToken = idToken,
            expiresIn = tokenTtl.seconds,
            scope = scope.sorted().joinToString(" "),
        )
    }

    private fun authenticateClient(clientId: String, clientSecret: String?): OAuthClient {
        val client = store.findClient(clientId) ?: throw OAuthError("invalid_client", "Unknown client_id.")
        if (client.clientType == ClientType.Public) {
            if (!clientSecret.isNullOrBlank()) {
                throw OAuthError("invalid_client", "Public client must not send a client secret.")
            }
            return client
        }
        val expected = client.clientSecretHash ?: throw OAuthError("invalid_client", "Confidential client has no secret hash.")
        val provided = clientSecret ?: throw OAuthError("invalid_client", "client_secret is required.")
        if (!constantTimeEquals(expected, tokenHash(provided, tokenPepper))) {
            throw OAuthError("invalid_client", "client_secret is invalid.")
        }
        return client
    }

    private fun parseScopes(scope: String): Set<String> =
        scope.split(" ").map { it.trim() }.filter { it.isNotBlank() }.toSet().ifEmpty { setOf("openid") }

    private fun now(): Instant = Instant.ofEpochMilli(clock.millis())
}

private fun buildRedirect(uri: String, params: Map<String, String>): String {
    val separator = if (uri.contains("?")) "&" else "?"
    val query = params.entries.joinToString("&") { (key, value) ->
        "${urlEncode(key)}=${urlEncode(value)}"
    }
    return "$uri$separator$query"
}

fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
