package com.application.umkmshop.backend.oauth

import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Instant
import java.util.Base64

private val secureRandom = SecureRandom()

fun randomToken(byteCount: Int = 32): String {
    val bytes = ByteArray(byteCount)
    secureRandom.nextBytes(bytes)
    return base64Url(bytes)
}

fun sha256Base64Url(value: String): String =
    base64Url(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

fun tokenHash(value: String, pepper: String): String =
    sha256Base64Url("$pepper:$value")

fun constantTimeEquals(left: String, right: String): Boolean =
    MessageDigest.isEqual(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))

fun generateRsaKeyPair(): KeyPair =
    KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

fun base64Url(bytes: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

fun rsaSignSha256(privateKey: RSAPrivateKey, signingInput: String): String {
    val signature = Signature.getInstance("SHA256withRSA")
    signature.initSign(privateKey)
    signature.update(signingInput.toByteArray(Charsets.UTF_8))
    return base64Url(signature.sign())
}

fun rsaVerifySha256(publicKey: RSAPublicKey, signingInput: String, signatureValue: String): Boolean {
    val signature = Signature.getInstance("SHA256withRSA")
    signature.initVerify(publicKey)
    signature.update(signingInput.toByteArray(Charsets.UTF_8))
    return runCatching {
        signature.verify(Base64.getUrlDecoder().decode(signatureValue))
    }.getOrDefault(false)
}

fun RSAPublicKey.toJwk(kid: String): String {
    val modulus = unsignedBase64Url(modulus)
    val exponent = unsignedBase64Url(publicExponent)
    return """{"kty":"RSA","use":"sig","kid":"$kid","alg":"RS256","n":"$modulus","e":"$exponent"}"""
}

private fun unsignedBase64Url(value: BigInteger): String {
    val bytes = value.toByteArray().dropWhile { it.toInt() == 0 }.toByteArray()
    return base64Url(bytes)
}

class MutableClock(
    private var millis: Long,
) : Clock() {
    override fun getZone() = java.time.ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId?) = this

    override fun millis(): Long = millis

    override fun instant(): Instant = Instant.ofEpochMilli(millis)

    fun advanceMillis(delta: Long) {
        millis += delta
    }
}
