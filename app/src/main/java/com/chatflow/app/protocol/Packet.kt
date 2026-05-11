package com.chatflow.app.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Binary packet format used on the wire (BLE GATT writes / notifications).
 *
 *   offset  bytes  field
 *   0       1      version (=1)
 *   1       1      type (see [PacketType])
 *   2       1      ttl   (hops remaining; start at 7)
 *   3       1      flags (bit0=encrypted)
 *   4       8      senderId   (first 8 bytes of SHA-256(pubKey))
 *   12      8      recipientId (zero = broadcast/channel)
 *   20      16     messageId  (UUID — used for dedup)
 *   36      8      timestamp (unix millis, big-endian)
 *   44      1      channelLen
 *   45      N      channel name (UTF-8, may be empty)
 *   ...     2      payloadLen (uint16 big-endian)
 *   ...     M      payload (opaque; may be AES-GCM ciphertext)
 *
 * Total header is 45 + channelLen + 2 bytes.
 */
data class Packet(
    val type: PacketType,
    val ttl: Int,
    val encrypted: Boolean,
    val senderId: ByteArray,      // 8 bytes
    val recipientId: ByteArray,   // 8 bytes (all-zero = broadcast)
    val messageId: UUID,
    val timestamp: Long,
    val channel: String,
    val payload: ByteArray
) {
    fun encode(): ByteArray {
        val channelBytes = channel.toByteArray(Charsets.UTF_8)
        require(channelBytes.size <= 255) { "Channel name too long" }
        require(payload.size <= 65_535) { "Payload too large" }
        val size = 45 + channelBytes.size + 2 + payload.size
        val buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buf.put(1)                                  // version
        buf.put(type.code)
        buf.put(ttl.toByte())
        buf.put(if (encrypted) 0x01 else 0x00)
        buf.put(senderId.copyOf(8))
        buf.put(recipientId.copyOf(8))
        val msb = messageId.mostSignificantBits
        val lsb = messageId.leastSignificantBits
        buf.putLong(msb); buf.putLong(lsb)
        buf.putLong(timestamp)
        buf.put(channelBytes.size.toByte())
        buf.put(channelBytes)
        buf.putShort(payload.size.toShort())
        buf.put(payload)
        return buf.array()
    }

    companion object {
        const val MAX_TTL: Int = 7

        fun decode(bytes: ByteArray): Packet? = runCatching {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val version = buf.get().toInt()
            if (version != 1) return@runCatching null
            val type = PacketType.fromCode(buf.get()) ?: return@runCatching null
            val ttl = buf.get().toInt() and 0xff
            val flags = buf.get().toInt() and 0xff
            val encrypted = flags and 0x01 != 0
            val sender = ByteArray(8).also { buf.get(it) }
            val recipient = ByteArray(8).also { buf.get(it) }
            val msb = buf.long; val lsb = buf.long
            val ts = buf.long
            val chLen = buf.get().toInt() and 0xff
            val chBytes = ByteArray(chLen).also { buf.get(it) }
            val channel = chBytes.toString(Charsets.UTF_8)
            val pLen = buf.short.toInt() and 0xffff
            val payload = ByteArray(pLen).also { buf.get(it) }
            Packet(type, ttl, encrypted, sender, recipient,
                UUID(msb, lsb), ts, channel, payload)
        }.getOrNull()
    }
}

enum class PacketType(val code: Byte) {
    ANNOUNCE(1),      // Advertise our identity + public key
    MESSAGE(2),       // A chat message (encrypted or plain)
    ACK(3),           // Delivery acknowledgement
    KEY_REQUEST(4);   // Ask a peer to re-announce

    companion object {
        fun fromCode(c: Byte): PacketType? = values().firstOrNull { it.code == c }
    }
}
