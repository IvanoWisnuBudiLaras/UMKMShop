package com.application.umkmshop.backend.oauth

interface OAuthStore {
    fun findClient(clientId: String): OAuthClient?
    fun registerClient(client: OAuthClient)
    
    fun saveAuthorizationCode(code: StoredAuthorizationCode)
    fun findAuthorizationCode(code: String): StoredAuthorizationCode?
    fun markCodeAsUsed(code: String): Boolean
    
    fun saveRefreshToken(token: StoredRefreshToken)
    fun findRefreshTokenByHash(tokenHash: String): StoredRefreshToken?
    fun revokeRefreshToken(tokenHash: String)
    fun revokeRefreshTokenChain(rotatedFromHash: String)
    
    fun getActiveSigningKey(): SigningKey?
    fun saveSigningKey(key: SigningKey)
    
    fun findUserById(userId: String): OAuthUser?
    
    fun enqueueNotification(userId: String, payload: String)
}

class InMemoryOAuthStore : OAuthStore {
    private val clients = mutableMapOf<String, OAuthClient>()
    private val authCodes = mutableMapOf<String, StoredAuthorizationCode>()
    private val refreshTokens = mutableMapOf<String, StoredRefreshToken>()
    private val keys = mutableListOf<SigningKey>()

    override fun findClient(clientId: String): OAuthClient? = clients[clientId]
    override fun registerClient(client: OAuthClient) { clients[client.clientId] = client }

    override fun saveAuthorizationCode(code: StoredAuthorizationCode) { authCodes[code.code] = code }
    override fun findAuthorizationCode(code: String): StoredAuthorizationCode? = authCodes[code]
    override fun markCodeAsUsed(code: String): Boolean {
        val existing = authCodes[code] ?: return false
        if (existing.used) return false
        authCodes[code] = existing.copy(used = true)
        return true
    }

    override fun saveRefreshToken(token: StoredRefreshToken) { refreshTokens[token.tokenHash] = token }
    override fun findRefreshTokenByHash(tokenHash: String): StoredRefreshToken? = refreshTokens[tokenHash]
    override fun revokeRefreshToken(tokenHash: String) {
        val existing = refreshTokens[tokenHash] ?: return
        refreshTokens[tokenHash] = existing.copy(revoked = true)
    }
    override fun revokeRefreshTokenChain(rotatedFromHash: String) {
        refreshTokens.values.filter { it.tokenHash == rotatedFromHash || it.rotatedFrom == rotatedFromHash }
            .forEach { refreshTokens[it.tokenHash] = it.copy(revoked = true) }
    }

    override fun getActiveSigningKey(): SigningKey? = keys.firstOrNull { it.active }
    override fun saveSigningKey(key: SigningKey) { keys.add(key) }

    override fun findUserById(userId: String): OAuthUser? = null
    override fun enqueueNotification(userId: String, payload: String) { println("TEST PUSH (IN-MEM): To User $userId with Payload $payload") }
}
