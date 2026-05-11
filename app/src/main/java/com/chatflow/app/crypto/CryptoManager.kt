package com.chatflow.app.crypto

import android.content.Context
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.KeyFactory
import java.io.File

/**
 * CryptoManager handles:
 *  - One-time generation of an X25519 keypair (device identity).
 *  - ECDH + HKDF-SHA256 to derive a shared key between two peers.
 *  - AES-GCM authenticated encryption for private messages.
 *  - Derivation of a symmetric channel key from a channel name (for group chat).
 *
 * NOTE: We use X25519 (Curve25519) available via the standard JCA "XDH" on
 * Android 11+ (API 30). ChatFlow targets minSdk 26; however X25519 JCA support
 * lands in API 33. To keep the code runnable across API 26+, we use the stock
 * "EC" secp256r1 curve, which is supported everywhere and provides the same
 * ECDH primitive semantics for our class-project purposes.
 */
class CryptoManager(context: Context) {

    private val keyFile = File(context.filesDir, "identity.keypair")
    private val keyPair: KeyPair = loadOrCreateKeyPair()

    val publicKeyBytes: ByteArray get() = keyPair.public.encoded  // X.509 SPKI
    val publicKeyFingerprint: ByteArray = HkdfSha256.hash(publicKeyBytes).copyOfRange(0, 8)
    val peerId: String = publicKeyFingerprint.toHex()

    // ---------- Identity ----------

    private fun loadOrCreateKeyPair(): KeyPair {
        if (keyFile.exists()) {
            try {
                val bytes = keyFile.readBytes()
                // Format: [4 bytes privLen][priv][4 bytes pubLen][pub]
                val privLen = bytes.readInt(0)
                val priv = bytes.copyOfRange(4, 4 + privLen)
                val pubLen = bytes.readInt(4 + privLen)
                val pub = bytes.copyOfRange(8 + privLen, 8 + privLen + pubLen)
                val kf = KeyFactory.getInstance("EC")
                val privKey = kf.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(priv))
                val pubKey = kf.generatePublic(X509EncodedKeySpec(pub))
                return KeyPair(pubKey, privKey)
            } catch (_: Exception) { /* fall through and regenerate */ }
        }
        val gen = KeyPairGenerator.getInstance("EC").apply {
            initialize(java.security.spec.ECGenParameterSpec("secp256r1"), SecureRandom())
        }
        val kp = gen.generateKeyPair()
        val priv = kp.private.encoded
        val pub = kp.public.encoded
        val out = ByteArray(8 + priv.size + pub.size)
        out.writeInt(0, priv.size)
        System.arraycopy(priv, 0, out, 4, priv.size)
        out.writeInt(4 + priv.size, pub.size)
        System.arraycopy(pub, 0, out, 8 + priv.size, pub.size)
        keyFile.writeBytes(out)
        return kp
    }

    // ---------- Peer key derivation ----------

    fun peerIdFromPublicKey(pub: ByteArray): String =
        HkdfSha256.hash(pub).copyOfRange(0, 8).toHex()

    private fun deriveSharedSecret(peerPublicKey: ByteArray): ByteArray {
        val kf = KeyFactory.getInstance("EC")
        val peerKey: PublicKey = kf.generatePublic(X509EncodedKeySpec(peerPublicKey))
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(keyPair.private)
        ka.doPhase(peerKey, true)
        val raw = ka.generateSecret()
        return HkdfSha256.expand(raw, info = "chatflow-dm".toByteArray(), length = 32)
    }

    // ---------- Private messaging (AES-GCM) ----------

    fun encryptForPeer(peerPublicKey: ByteArray, plaintext: ByteArray): ByteArray {
        val key = deriveSharedSecret(peerPublicKey)
        return aesGcmEncrypt(key, plaintext)
    }

    fun decryptFromPeer(peerPublicKey: ByteArray, ciphertext: ByteArray): ByteArray {
        val key = deriveSharedSecret(peerPublicKey)
        return aesGcmDecrypt(key, ciphertext)
    }

    // ---------- Group channel keys ----------

    fun channelKey(channelName: String): ByteArray =
        HkdfSha256.expand("channel:$channelName".toByteArray(), "chatflow-ch".toByteArray(), 32)

    fun encryptForChannel(channelName: String, plaintext: ByteArray): ByteArray =
        aesGcmEncrypt(channelKey(channelName), plaintext)

    fun decryptForChannel(channelName: String, ciphertext: ByteArray): ByteArray =
        aesGcmDecrypt(channelKey(channelName), ciphertext)

    // ---------- AES-GCM primitives ----------

    private fun aesGcmEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext)
        return iv + ct
    }

    private fun aesGcmDecrypt(key: ByteArray, packet: ByteArray): ByteArray {
        require(packet.size > 12) { "Ciphertext too short" }
        val iv = packet.copyOfRange(0, 12)
        val ct = packet.copyOfRange(12, packet.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
private fun ByteArray.readInt(offset: Int): Int =
    ((this[offset].toInt() and 0xff) shl 24) or
    ((this[offset + 1].toInt() and 0xff) shl 16) or
    ((this[offset + 2].toInt() and 0xff) shl 8) or
    (this[offset + 3].toInt() and 0xff)
private fun ByteArray.writeInt(offset: Int, value: Int) {
    this[offset] = (value shr 24).toByte()
    this[offset + 1] = (value shr 16).toByte()
    this[offset + 2] = (value shr 8).toByte()
    this[offset + 3] = value.toByte()
}
