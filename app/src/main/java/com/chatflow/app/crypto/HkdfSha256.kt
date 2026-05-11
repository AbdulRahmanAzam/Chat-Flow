package com.chatflow.app.crypto

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Minimal HKDF-SHA256 implementation (RFC 5869). */
object HkdfSha256 {
    fun hash(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun expand(ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val salt = ByteArray(32) // all-zero salt
        val prk = hmac(salt, ikm)
        val n = (length + 31) / 32
        val t = ByteArray(n * 32)
        var prev = ByteArray(0)
        for (i in 1..n) {
            val mac = hmac(prk, prev + info + byteArrayOf(i.toByte()))
            System.arraycopy(mac, 0, t, (i - 1) * 32, 32)
            prev = mac
        }
        return t.copyOfRange(0, length)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
