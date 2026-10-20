package com.mymoney.app.core.util

import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.SecureRandom
import java.util.UUID

/** Generates stable unique ids for entities. */
object Ids {
    fun new(): String = UUID.randomUUID().toString()
}

/**
 * PIN hashing with PBKDF2-HMAC-SHA1 (pure JDK: works on every API level and
 * on ChromeOS without native code or Play Services). Random salt per PIN,
 * constant-time comparison; the PIN is never stored in plain text.
 */
object PinHasher {
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }

    fun verify(pin: String, salt: ByteArray, expectedHash: ByteArray): Boolean {
        if (pin.isBlank()) return false
        val actual = try {
            hash(pin, salt)
        } catch (e: Exception) {
            return false
        }
        return MessageDigest.isEqual(actual, expectedHash)
    }
}
