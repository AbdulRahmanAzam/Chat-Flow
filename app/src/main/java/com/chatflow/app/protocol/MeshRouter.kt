package com.chatflow.app.protocol

import android.util.LruCache
import java.util.UUID

/**
 * Tracks seen message IDs so we never re-broadcast a packet, and provides
 * a decision helper for whether to relay an incoming packet.
 */
class MeshRouter(private val selfId: ByteArray) {

    private val seen = LruCache<UUID, Long>(4096)

    /** Returns true the first time this packet is seen (and records it). */
    fun markSeen(id: UUID): Boolean {
        if (seen.get(id) != null) return false
        seen.put(id, System.currentTimeMillis())
        return true
    }

    /** Should we forward this packet onward? */
    fun shouldRelay(packet: Packet): Boolean {
        if (packet.ttl <= 1) return false
        if (packet.senderId.contentEquals(selfId)) return false
        // Don't relay unicast messages addressed to us — delivered, end of the line
        if (packet.recipientId.any { it != 0.toByte() } &&
            packet.recipientId.contentEquals(selfId)) return false
        return true
    }

    fun decrementTtl(packet: Packet): Packet = packet.copy(ttl = packet.ttl - 1)
}
