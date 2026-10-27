package com.etrisad.zenith.admin

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
object TamperPin {
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private val FORMAT = Regex("^[0-9]{4,8}$")

    fun isValid(pin: String): Boolean = FORMAT.matches(pin)

    fun newSaltHex(): String =
        ByteArray(SALT_BYTES)
            .also { SecureRandom().nextBytes(it) }
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

    fun hash(pin: String, saltHex: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), saltHex.toHexBytes(), ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
                .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
        } finally {
            spec.clearPassword()
        }
    }

    fun matches(pin: String, saltHex: String, expectedHashHex: String): Boolean {
        if (expectedHashHex.isEmpty()) return false
        val actual = hash(pin, saltHex)
        if (actual.length != expectedHashHex.length) return false
        var result = 0
        for (i in actual.indices) {
            result = result or (actual[i].code xor expectedHashHex[i].code)
        }
        return result == 0
    }

    private fun String.toHexBytes(): ByteArray =
        ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
}
