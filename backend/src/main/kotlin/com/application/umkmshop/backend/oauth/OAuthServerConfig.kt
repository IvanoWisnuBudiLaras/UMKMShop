package com.application.umkmshop.backend.oauth

import java.time.Clock

data class OAuthServerConfig(
    val issuer: String,
    val tokenPepper: String,
    val demoUser: OAuthUser,
    val demoClient: OAuthClient,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv(), defaultPort: Int = 8090): OAuthServerConfig {
            val issuer = env["UMKMSHOP_OAUTH_ISSUER"] ?: "http://127.0.0.1:$defaultPort"
            val tokenPepper = env["UMKMSHOP_OAUTH_TOKEN_PEPPER"]
                ?: error("UMKMSHOP_OAUTH_TOKEN_PEPPER is required for hashing authorization codes and tokens.")
            val redirectUri = env["UMKMSHOP_OAUTH_DEMO_REDIRECT_URI"] ?: "$issuer/demo/callback"
            return OAuthServerConfig(
                issuer = issuer.trimEnd('/'),
                tokenPepper = tokenPepper,
                demoUser = OAuthUser(
                    id = env["UMKMSHOP_OAUTH_DEMO_USER_ID"] ?: "00000000-0000-0000-0000-000000000027",
                    email = env["UMKMSHOP_OAUTH_DEMO_USER_EMAIL"] ?: "demo@umkmshop.local",
                    name = env["UMKMSHOP_OAUTH_DEMO_USER_NAME"] ?: "Demo UMKMShop User",
                ),
                demoClient = OAuthClient(
                    clientId = env["UMKMSHOP_OAUTH_DEMO_CLIENT_ID"] ?: "umkmshop-demo-public",
                    clientName = env["UMKMSHOP_OAUTH_DEMO_CLIENT_NAME"] ?: "UMKMShop Demo Public Client",
                    clientType = ClientType.Public,
                    redirectUris = setOf(redirectUri),
                ),
            )
        }
    }
}

fun createDemoOAuthService(config: OAuthServerConfig, clock: Clock = Clock.systemUTC()): OAuthService {
    val keyPair = generateRsaKeyPair()
    val jwtService = JwtService(
        issuer = config.issuer,
        keyId = "local-demo-rsa-1",
        keyPair = keyPair,
        clock = clock,
    )
    return OAuthService(
        issuer = config.issuer,
        store = InMemoryOAuthStore(listOf(config.demoClient)),
        jwtService = jwtService,
        clock = clock,
        tokenPepper = config.tokenPepper,
    )
}
