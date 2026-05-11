package com.chatflow.app.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.chatflow.app.crypto.CryptoManager
import com.chatflow.app.data.db.MessageEntity
import com.chatflow.app.data.db.PeerEntity
import com.chatflow.app.data.repo.ChatRepository
import com.chatflow.app.protocol.MeshRouter
import com.chatflow.app.protocol.Packet
import com.chatflow.app.protocol.PacketType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Orchestrates scanning, advertising, GATT server, and a pool of outbound
 * GATT client connections. Decodes incoming packets, decrypts/persists chat
 * messages, and relays forward per mesh-routing rules (TTL, dedup).
 */
class MeshEngine(
    private val context: Context,
    private val crypto: CryptoManager,
    private val repo: ChatRepository,
    private val nicknameProvider: () -> String
) {
    private val tag = "ChatFlow/Mesh"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val router = MeshRouter(crypto.publicKeyFingerprint)
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE)
        as android.bluetooth.BluetoothManager).adapter

    private val advertiser = BleAdvertiser(adapter)
    private val gattServer = BleGattServer(context) { data, dev -> onPacket(data, dev) }
    private val clientPool = BleClientPool(
        context,
        onPacketIn = { data, dev -> onPacket(data, dev) },
        onConnected = { dev ->
            scope.launch { sendAnnounce() }  // announce ourselves to new peer
            _status.value = _status.value.copy(connected = clientPool.connectedCount())
        },
        onDisconnected = {
            _status.value = _status.value.copy(connected = clientPool.connectedCount())
        }
    )
    private val scanner = BleScanner(adapter) { result ->
        val device = result.device
        if (!clientPool.isConnected(device)) {
            Log.d(tag, "Discovered ${device.address} rssi=${result.rssi}")
            clientPool.connect(device)
        }
    }

    data class Status(
        val running: Boolean = false,
        val connected: Int = 0,
        val selfId: String = "",
        val nickname: String = ""
    )
    private val _status = MutableStateFlow(Status(selfId = crypto.peerId))
    val status: StateFlow<Status> = _status.asStateFlow()

    fun start() {
        if (_status.value.running) return
        gattServer.start()
        advertiser.start()
        scanner.start()
        scope.launch {
            while (isActive) {
                delay(BleConstants.RESCAN_INTERVAL_MS)
                scanner.resetCache()
                scanner.stop(); scanner.start()
                sendAnnounce()
                repo.prunePeers()
            }
        }
        _status.value = Status(running = true, selfId = crypto.peerId, nickname = nicknameProvider())
        scope.launch { delay(1500); sendAnnounce() }
    }

    fun stop() {
        scanner.stop()
        advertiser.stop()
        clientPool.disconnectAll()
        gattServer.stop()
        scope.cancel()
        _status.value = _status.value.copy(running = false, connected = 0)
    }

    // -------------------- Outbound API --------------------

    /** Send a private, E2E-encrypted direct message to a known peer. */
    fun sendPrivate(peerId: String, content: String) = scope.launch {
        val peer = repo.findPeer(peerId) ?: run {
            Log.w(tag, "Unknown peer $peerId"); return@launch
        }
        val plaintext = content.toByteArray(Charsets.UTF_8)
        val ciphertext = crypto.encryptForPeer(peer.publicKey, plaintext)
        val packet = Packet(
            type = PacketType.MESSAGE,
            ttl = Packet.MAX_TTL,
            encrypted = true,
            senderId = crypto.publicKeyFingerprint,
            recipientId = hex8(peerId),
            messageId = UUID.randomUUID(),
            timestamp = System.currentTimeMillis(),
            channel = "",
            payload = ciphertext
        )
        repo.saveMessage(
            MessageEntity(
                id = packet.messageId.toString(),
                conversationId = peerId,
                senderId = "self",
                senderName = nicknameProvider(),
                content = content,
                timestamp = packet.timestamp,
                isOutgoing = true,
                isChannel = false
            )
        )
        router.markSeen(packet.messageId)
        broadcast(packet.encode())
    }

    /** Send a group message to a named channel (AES-GCM with channel key). */
    fun sendChannel(channel: String, content: String) = scope.launch {
        val ciphertext = crypto.encryptForChannel(channel, content.toByteArray(Charsets.UTF_8))
        val packet = Packet(
            type = PacketType.MESSAGE,
            ttl = Packet.MAX_TTL,
            encrypted = true,
            senderId = crypto.publicKeyFingerprint,
            recipientId = ByteArray(8),       // broadcast
            messageId = UUID.randomUUID(),
            timestamp = System.currentTimeMillis(),
            channel = channel,
            payload = ciphertext
        )
        repo.saveMessage(
            MessageEntity(
                id = packet.messageId.toString(),
                conversationId = "#$channel",
                senderId = "self",
                senderName = nicknameProvider(),
                content = content,
                timestamp = packet.timestamp,
                isOutgoing = true,
                isChannel = true
            )
        )
        router.markSeen(packet.messageId)
        broadcast(packet.encode())
    }

    private fun sendAnnounce() {
        val nickname = nicknameProvider().ifBlank { "peer-${crypto.peerId.take(4)}" }
        val nick = nickname.toByteArray(Charsets.UTF_8).take(64).toByteArray()
        val pub = crypto.publicKeyBytes
        // payload: [1 byte nickLen][nick][2 bytes pubLen][pubKey]
        val buf = ByteBuffer.allocate(1 + nick.size + 2 + pub.size)
        buf.put(nick.size.toByte()); buf.put(nick)
        buf.putShort(pub.size.toShort()); buf.put(pub)
        val packet = Packet(
            type = PacketType.ANNOUNCE,
            ttl = Packet.MAX_TTL,
            encrypted = false,
            senderId = crypto.publicKeyFingerprint,
            recipientId = ByteArray(8),
            messageId = UUID.randomUUID(),
            timestamp = System.currentTimeMillis(),
            channel = "",
            payload = buf.array()
        )
        router.markSeen(packet.messageId)
        broadcast(packet.encode())
    }

    private fun broadcast(data: ByteArray, except: BluetoothDevice? = null) {
        // Send to all GATT clients we have open (acting as central)
        clientPool.send(data)
        // Notify every central that subscribed to our GATT server characteristic
        gattServer.notifySubscribers(data)
    }

    // -------------------- Inbound --------------------

    private fun onPacket(raw: ByteArray, source: BluetoothDevice) {
        val packet = Packet.decode(raw) ?: return
        if (!router.markSeen(packet.messageId)) return        // duplicate → drop

        when (packet.type) {
            PacketType.ANNOUNCE -> handleAnnounce(packet)
            PacketType.MESSAGE -> handleMessage(packet)
            PacketType.ACK -> handleAck(packet)
            PacketType.KEY_REQUEST -> sendAnnounce()
        }

        // Mesh relay
        if (router.shouldRelay(packet)) {
            val forwarded = router.decrementTtl(packet)
            broadcast(forwarded.encode(), except = source)
        }
    }

    private fun handleAnnounce(packet: Packet) = scope.launch {
        try {
            val buf = ByteBuffer.wrap(packet.payload)
            val nickLen = buf.get().toInt() and 0xff
            val nick = ByteArray(nickLen).also { buf.get(it) }.toString(Charsets.UTF_8)
            val pubLen = buf.short.toInt() and 0xffff
            val pub = ByteArray(pubLen).also { buf.get(it) }
            val peerId = crypto.peerIdFromPublicKey(pub)
            repo.upsertPeer(
                PeerEntity(
                    peerId = peerId,
                    nickname = nick,
                    publicKey = pub,
                    lastSeen = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) { Log.w(tag, "Bad announce", e) }
    }

    private fun handleMessage(packet: Packet) = scope.launch {
        try {
            val senderIdHex = packet.senderId.toHex()
            val peer = repo.findPeer(senderIdHex)
            val isChannel = packet.channel.isNotBlank()

            val plaintext: String = if (isChannel) {
                if (packet.encrypted) {
                    crypto.decryptForChannel(packet.channel, packet.payload).toString(Charsets.UTF_8)
                } else packet.payload.toString(Charsets.UTF_8)
            } else {
                val toUs = packet.recipientId.contentEquals(crypto.publicKeyFingerprint)
                if (!toUs) return@launch      // only decrypt DMs addressed to us
                if (peer == null) {
                    // Ask sender to re-announce so we get their public key
                    requestKey(packet.senderId); return@launch
                }
                if (packet.encrypted) {
                    crypto.decryptFromPeer(peer.publicKey, packet.payload).toString(Charsets.UTF_8)
                } else packet.payload.toString(Charsets.UTF_8)
            }

            repo.saveMessage(
                MessageEntity(
                    id = packet.messageId.toString(),
                    conversationId = if (isChannel) "#${packet.channel}" else senderIdHex,
                    senderId = senderIdHex,
                    senderName = peer?.nickname ?: "peer-${senderIdHex.take(4)}",
                    content = plaintext,
                    timestamp = packet.timestamp,
                    isOutgoing = false,
                    isDelivered = true,
                    isChannel = isChannel
                )
            )

            // Send ACK for private messages
            if (!isChannel) sendAck(packet)
        } catch (e: Exception) {
            Log.w(tag, "Could not process message ${packet.messageId}", e)
        }
    }

    private fun sendAck(original: Packet) {
        // Carry the original message id inside the payload so the ACK packet
        // itself has a fresh id for mesh dedup.
        val payload = ByteBuffer.allocate(16)
            .putLong(original.messageId.mostSignificantBits)
            .putLong(original.messageId.leastSignificantBits)
            .array()
        val ack = Packet(
            type = PacketType.ACK,
            ttl = Packet.MAX_TTL,
            encrypted = false,
            senderId = crypto.publicKeyFingerprint,
            recipientId = original.senderId,
            messageId = UUID.randomUUID(),
            timestamp = System.currentTimeMillis(),
            channel = "",
            payload = payload
        )
        router.markSeen(ack.messageId)
        broadcast(ack.encode())
    }

    private fun handleAck(packet: Packet) {
        if (packet.payload.size < 16) return
        if (!packet.recipientId.contentEquals(crypto.publicKeyFingerprint)) return
        val buf = ByteBuffer.wrap(packet.payload)
        val id = UUID(buf.long, buf.long)
        scope.launch { repo.markDelivered(id.toString()) }
    }

    private fun requestKey(targetSenderId: ByteArray) {
        val req = Packet(
            type = PacketType.KEY_REQUEST,
            ttl = Packet.MAX_TTL,
            encrypted = false,
            senderId = crypto.publicKeyFingerprint,
            recipientId = targetSenderId,
            messageId = UUID.randomUUID(),
            timestamp = System.currentTimeMillis(),
            channel = "",
            payload = ByteArray(0)
        )
        router.markSeen(req.messageId)
        broadcast(req.encode())
    }

    private fun hex8(hex: String): ByteArray {
        val clean = hex.padStart(16, '0').take(16)
        val out = ByteArray(8)
        for (i in 0 until 8) {
            out[i] = ((Character.digit(clean[i * 2], 16) shl 4) or
                Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
