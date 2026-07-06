package com.application.umkmshop.backend.oauth

interface OAuthStore {
    fun findClient(clientId: String): OAuthClient?
    fun saveAuthorizationCode(code: StoredAuthorizationCode)
    fun findAuthorizationCodeByHash(codeHash: String): StoredAuthorizationCode?
    fun saveRefreshToken(token: StoredRefreshToken)
    fun findRefreshTokenByHash(tokenHash: String): StoredRefreshToken?
    fun saveAccessGrant(tokenHash: String, grant: AccessGrant)
    fun findAccessGrant(tokenHash: String): AccessGrant?
}

class InMemoryOAuthStore(
    clients: Collection<OAuthClient>,
) : OAuthStore {
    private val clientsById = clients.associateBy { it.clientId }.toMutableMap()
    private val authorizationCodes = mutableMapOf<String, StoredAuthorizationCode>()
    private val refreshTokens = mutableMapOf<String, StoredRefreshToken>()
    private val accessGrants = mutableMapOf<String, AccessGrant>()

    override fun findClient(clientId: String): OAuthClient? = clientsById[clientId]

    override fun saveAuthorizationCode(code: StoredAuthorizationCode) {
        authorizationCodes[code.codeHash] = code
    }

    override fun findAuthorizationCodeByHash(codeHash: String): StoredAuthorizationCode? =
        authorizationCodes[codeHash]

    override fun saveRefreshToken(token: StoredRefreshToken) {
        refreshTokens[token.tokenHash] = token
    }

    override fun findRefreshTokenByHash(tokenHash: String): StoredRefreshToken? =
        refreshTokens[tokenHash]

    override fun saveAccessGrant(tokenHash: String, grant: AccessGrant) {
        accessGrants[tokenHash] = grant
    }

    override fun findAccessGrant(tokenHash: String): AccessGrant? = accessGrants[tokenHash]
}
