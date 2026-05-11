package com.chatflow.app.data.repo

import com.chatflow.app.crypto.CryptoManager
import com.chatflow.app.data.db.AppDatabase
import com.chatflow.app.data.db.MessageEntity
import com.chatflow.app.data.db.PeerEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val db: AppDatabase,
    val crypto: CryptoManager
) {
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        db.messages().observe(conversationId)

    fun observePeers(): Flow<List<PeerEntity>> = db.peers().observeAll()

    fun observeConversationIds(): Flow<List<String>> = db.messages().conversationIds()

    fun observeChannels() = db.channels().observeAll()

    suspend fun lastMessage(cid: String): MessageEntity? = db.messages().lastInConversation(cid)

    suspend fun saveMessage(m: MessageEntity) = db.messages().insert(m)
    suspend fun markDelivered(id: String) {
        db.messages().find(id)?.let { db.messages().update(it.copy(isDelivered = true)) }
    }

    suspend fun upsertPeer(p: PeerEntity) = db.peers().upsert(p)
    suspend fun findPeer(id: String): PeerEntity? = db.peers().find(id)
    suspend fun prunePeers(maxAgeMs: Long = 60 * 60 * 1000L) {
        db.peers().pruneOlderThan(System.currentTimeMillis() - maxAgeMs)
    }

    suspend fun joinChannel(name: String) =
        db.channels().insert(com.chatflow.app.data.db.ChannelEntity(name, System.currentTimeMillis()))
    suspend fun leaveChannel(name: String) = db.channels().delete(name)

    suspend fun clearAll() {
        db.messages().clear()
    }
}
