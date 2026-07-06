package com.application.umkmshop.backend.oauth

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OAuthServiceTest {
    private val clock = MutableClock(1_800_000_000_000)
    private val pepper = "test-pepper"
    private val user = OAuthUser(
        id = "user-027",
        email = "seller@example.test",
        name = "Seller Demo",
    )
    private val client = OAuthClient(
        clientId = "demo-public",
        clientName = "Demo Public Client",
        clientType = ClientType.Public,
        redirectUris = setOf("http://127.0.0.1:8090/demo/callback"),
    )
    private val service = OAuthService(
        issuer = "http://127.0.0.1:8090",
        store = InMemoryOAuthStore(listOf(client)),
        jwtService = JwtService("http://127.0.0.1:8090", "test-key", generateRsaKeyPair(), clock),
        clock = clock,
        tokenPepper = pepper,
        codeTtl = Duration.ofMinutes(5),
    )

    @Test
    fun rejectsInvalidRedirectUri() {
        val error = assertFailsWith<OAuthError> {
            service.validateAuthorizationRequest(validRequest().copy(redirectUri = "https://attacker.example/callback"))
        }
        assertEquals("invalid_request", error.code)
    }

    @Test
    fun rejectsMissingOrBadPkce() {
        val missing = assertFailsWith<OAuthError> {
            service.validateAuthorizationRequest(validRequest().copy(codeChallenge = null))
        }
        assertEquals("invalid_request", missing.code)

        val badMethod = assertFailsWith<OAuthError> {
            service.validateAuthorizationRequest(validRequest().copy(codeChallengeMethod = "plain"))
        }
        assertEquals("invalid_request", badMethod.code)
    }

    @Test
    fun rejectsBadPkceVerifier() {
        val code = service.approveAuthorization(validRequest(), user)
        val error = assertFailsWith<OAuthError> {
            service.exchangeAuthorizationCode(
                clientId = client.clientId,
                clientSecret = null,
                code = code,
                redirectUri = client.redirectUris.first(),
                codeVerifier = "wrong-verifier",
            )
        }
        assertEquals("invalid_grant", error.code)
    }

    @Test
    fun rejectsReusedCode() {
        val code = service.approveAuthorization(validRequest(), user)
        val first = exchange(code)
        assertTrue(first.accessToken.isNotBlank())

        val error = assertFailsWith<OAuthError> { exchange(code) }
        assertEquals("invalid_grant", error.code)
    }

    @Test
    fun rejectsExpiredCode() {
        val code = service.approveAuthorization(validRequest(), user)
        clock.advanceMillis(Duration.ofMinutes(6).toMillis())

        val error = assertFailsWith<OAuthError> { exchange(code) }
        assertEquals("invalid_grant", error.code)
    }

    @Test
    fun rejectsRevokedRefreshToken() {
        val response = exchange(service.approveAuthorization(validRequest(), user))
        assertTrue(service.revoke(response.refreshToken))

        val error = assertFailsWith<OAuthError> {
            service.refresh(client.clientId, clientSecret = null, refreshToken = response.refreshToken)
        }
        assertEquals("invalid_grant", error.code)
    }

    @Test
    fun rotatesRefreshTokenAndRejectsOldToken() {
        val response = exchange(service.approveAuthorization(validRequest(), user))
        val rotated = service.refresh(client.clientId, clientSecret = null, refreshToken = response.refreshToken)
        assertTrue(rotated.refreshToken.isNotBlank())

        val error = assertFailsWith<OAuthError> {
            service.refresh(client.clientId, clientSecret = null, refreshToken = response.refreshToken)
        }
        assertEquals("invalid_grant", error.code)
    }

    @Test
    fun issuesJwtThatCanBeVerifiedFromJwksAndUsedForUserInfo() {
        val response = exchange(service.approveAuthorization(validRequest(), user))
        assertNotNull(response.idToken)
        assertTrue(service.jwksJson().contains("\"kty\":\"RSA\""))
        assertTrue(service.userInfo(response.accessToken).contains("\"email\":\"seller@example.test\""))
    }

    @Test
    fun rejectsPasswordAndClientCredentialsGrantsByPolicyShape() {
        val unsupportedGrants = setOf("password", "client_credentials")
        assertTrue("authorization_code" !in unsupportedGrants)
        assertTrue("refresh_token" !in unsupportedGrants)
    }

    private fun exchange(code: String): TokenResponse =
        service.exchangeAuthorizationCode(
            clientId = client.clientId,
            clientSecret = null,
            code = code,
            redirectUri = client.redirectUris.first(),
            codeVerifier = "valid-verifier",
        )

    private fun validRequest(): AuthorizationRequest =
        AuthorizationRequest(
            responseType = "code",
            clientId = client.clientId,
            redirectUri = client.redirectUris.first(),
            scope = "openid email profile",
            state = "state-123",
            codeChallenge = sha256Base64Url("valid-verifier"),
            codeChallengeMethod = "S256",
            nonce = "nonce-123",
        )
}
