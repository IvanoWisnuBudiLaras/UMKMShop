package com.application.umkmshop.backend.oauth

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

class JdbcOAuthStore(private val dataSource: DataSource) : OAuthStore {

    override fun findClient(clientId: String): OAuthClient? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "select client_id, client_secret_hash, redirect_uris, client_name from oauth_clients where client_id = ?"
            ).use { statement ->
                statement.setString(1, clientId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        OAuthClient(
                            clientId = rs.getString("client_id"),
                            clientSecretHash = rs.getString("client_secret_hash"),
                            redirectUris = (rs.getArray("redirect_uris").array as Array<*>).map { it.toString() }.toSet(),
                            clientName = rs.getString("client_name")
                        )
                    } else null
                }
            }
        }

    override fun registerClient(client: OAuthClient) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                insert into oauth_clients (client_id, client_secret_hash, redirect_uris, client_name)
                values (?, ?, ?, ?)
                on conflict (client_id) do update set
                    client_secret_hash = excluded.client_secret_hash,
                    redirect_uris = excluded.redirect_uris,
                    client_name = excluded.client_name
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, client.clientId)
                statement.setString(2, client.clientSecretHash)
                statement.setArray(3, connection.createArrayOf("text", client.redirectUris.toTypedArray()))
                statement.setString(4, client.clientName)
                statement.executeUpdate()
            }
        }
    }

    override fun saveAuthorizationCode(code: StoredAuthorizationCode) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                insert into oauth_authorization_codes 
                (code, client_id, user_id, redirect_uri, code_challenge, code_challenge_method, scope, expires_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, code.code)
                statement.setString(2, code.clientId)
                statement.setObject(3, UUID.fromString(code.userId))
                statement.setString(4, code.redirectUri)
                statement.setString(5, code.codeChallenge)
                statement.setString(6, code.codeChallengeMethod)
                statement.setString(7, code.scope)
                statement.setObject(8, code.expiresAt.atOffset(ZoneOffset.UTC))
                statement.executeUpdate()
            }
        }
    }

    override fun markCodeAsUsed(code: String): Boolean =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "update oauth_authorization_codes set used = true where code = ? and used = false"
            ).use { statement ->
                statement.setString(1, code)
                statement.executeUpdate() > 0
            }
        }

    override fun findAuthorizationCode(code: String): StoredAuthorizationCode? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "select * from oauth_authorization_codes where code = ?"
            ).use { statement ->
                statement.setString(1, code)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        StoredAuthorizationCode(
                            code = rs.getString("code"),
                            clientId = rs.getString("client_id"),
                            userId = rs.getObject("user_id", UUID::class.java).toString(),
                            redirectUri = rs.getString("redirect_uri"),
                            codeChallenge = rs.getString("code_challenge"),
                            codeChallengeMethod = rs.getString("code_challenge_method"),
                            scope = rs.getString("scope"),
                            expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java).toInstant(),
                            used = rs.getBoolean("used")
                        )
                    } else null
                }
            }
        }

    override fun saveRefreshToken(token: StoredRefreshToken) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                insert into oauth_refresh_tokens (token_hash, client_id, user_id, scope, rotated_from, expires_at)
                values (?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, token.tokenHash)
                statement.setString(2, token.clientId)
                statement.setObject(3, UUID.fromString(token.userId))
                statement.setString(4, token.scope)
                statement.setString(5, token.rotatedFrom)
                statement.setObject(6, token.expiresAt.atOffset(ZoneOffset.UTC))
                statement.executeUpdate()
            }
        }
    }

    override fun findRefreshTokenByHash(tokenHash: String): StoredRefreshToken? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "select * from oauth_refresh_tokens where token_hash = ?"
            ).use { statement ->
                statement.setString(1, tokenHash)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        StoredRefreshToken(
                            tokenHash = rs.getString("token_hash"),
                            clientId = rs.getString("client_id"),
                            userId = rs.getObject("user_id", UUID::class.java).toString(),
                            scope = rs.getString("scope"),
                            rotatedFrom = rs.getString("rotated_from"),
                            revoked = rs.getBoolean("revoked"),
                            expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java).toInstant()
                        )
                    } else null
                }
            }
        }

    override fun revokeRefreshToken(tokenHash: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("update oauth_refresh_tokens set revoked = true where token_hash = ?")
                .use { it.setString(1, tokenHash); it.executeUpdate() }
        }
    }

    override fun revokeRefreshTokenChain(rotatedFromHash: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "update oauth_refresh_tokens set revoked = true where token_hash = ? or rotated_from = ?"
            ).use { 
                it.setString(1, rotatedFromHash)
                it.setString(2, rotatedFromHash)
                it.executeUpdate() 
            }
        }
    }

    override fun getActiveSigningKey(): SigningKey? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "select kid, private_key_pem, public_key_pem from oauth_signing_keys where active = true limit 1"
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        SigningKey(
                            kid = rs.getString("kid"),
                            privateKeyPem = rs.getString("private_key_pem"),
                            publicKeyPem = rs.getString("public_key_pem")
                        )
                    } else null
                }
            }
        }

    override fun saveSigningKey(key: SigningKey) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "insert into oauth_signing_keys (kid, private_key_pem, public_key_pem, active) values (?, ?, ?, ?)"
            ).use { statement ->
                statement.setString(1, key.kid)
                statement.setString(2, key.privateKeyPem)
                statement.setString(3, key.publicKeyPem)
                statement.setBoolean(4, key.active)
                statement.executeUpdate()
            }
        }
    }

    override fun findUserById(userId: String): OAuthUser? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select p.id, p.name, p.avatar_url, u.email
                from public.profiles p
                join auth.users u on u.id = p.id
                where p.id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, UUID.fromString(userId))
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        OAuthUser(
                            id = rs.getObject("id", UUID::class.java).toString(),
                            name = rs.getString("name"),
                            email = rs.getString("email"),
                            avatarUrl = rs.getString("avatar_url")
                        )
                    } else null
                }
            }
        }

    override fun enqueueNotification(userId: String, payload: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "select pgmq.send('notifications', ?::jsonb)"
            ).use { statement ->
                statement.setString(1, payload)
                statement.execute()
            }
        }
    }
}
