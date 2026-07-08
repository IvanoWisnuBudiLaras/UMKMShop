package com.application.umkmshop.backend.oauth

import java.time.Clock
import javax.sql.DataSource

data class OAuthServerConfig(
    val issuer: String,
    val tokenPepper: String,
    val demoUser: OAuthUser,
    val demoClient: OAuthClient,
    val useJdbcStore: Boolean = false,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv(), defaultPort: Int = 8090): OAuthServerConfig {
            val issuer = env["UMKMSHOP_OAUTH_ISSUER"] ?: "http://127.0.0.1:$defaultPort"
            val tokenPepper = env["UMKMSHOP_OAUTH_TOKEN_PEPPER"]
                ?: error("UMKMSHOP_OAUTH_TOKEN_PEPPER is required for hashing authorization codes and tokens.")
            val redirectUris = (env["UMKMSHOP_OAUTH_DEMO_REDIRECT_URI"] ?: "$issuer/demo/callback")
                .split(",")
                .map { it.trim() }
                .toSet()

            val useJdbcStore = env["UMKMSHOP_OAUTH_USE_JDBC"]?.toBoolean() ?: false

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
                    clientSecretHash = "", // Public client for demo
                    redirectUris = redirectUris,
                ),
                useJdbcStore = useJdbcStore
            )
        }
    }
}

fun createOAuthService(
    config: OAuthServerConfig, 
    dataSource: DataSource? = null,
    clock: Clock = Clock.systemUTC()
): OAuthService {
    val store = if (config.useJdbcStore && dataSource != null) {
        JdbcOAuthStore(dataSource).also { 
            it.registerClient(config.demoClient)
        }
    } else {
        InMemoryOAuthStore().also { 
            it.registerClient(config.demoClient)
        }
    }

    val jwtService = JwtService(
        issuer = config.issuer,
        store = store,
        clock = clock,
    )

    return OAuthService(
        issuer = config.issuer,
        store = store,
        jwtService = jwtService,
        clock = clock,
    )
}

fun createDemoOAuthService(config: OAuthServerConfig, clock: Clock = Clock.systemUTC()): OAuthService =
    createOAuthService(config, null, clock)
