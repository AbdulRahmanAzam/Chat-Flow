package com.chatflow.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,              // UUID string (= packet.messageId)
    val conversationId: String,              // peer id (private) or "#channel" (group)
    val senderId: String,                    // peer hex id; "self" if we sent it
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val isDelivered: Boolean = false,
    val isChannel: Boolean = false
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY timestamp ASC")
    fun observe(cid: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun find(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(m: MessageEntity)

    @Update
    suspend fun update(m: MessageEntity)

    @Query("SELECT DISTINCT conversationId FROM messages ORDER BY timestamp DESC")
    fun conversationIds(): Flow<List<String>>

    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastInConversation(cid: String): MessageEntity?

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val peerId: String,
    val nickname: String,
    val publicKey: ByteArray,
    val lastSeen: Long,
    val rssi: Int = 0
) {
    override fun equals(other: Any?): Boolean = other is PeerEntity && other.peerId == peerId
    override fun hashCode(): Int = peerId.hashCode()
}

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun observeAll(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE peerId = :id LIMIT 1")
    suspend fun find(id: String): PeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: PeerEntity)

    @Query("DELETE FROM peers WHERE lastSeen < :before")
    suspend fun pruneOlderThan(before: Long)
}

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val name: String,
    val joinedAt: Long
)

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY joinedAt DESC")
    fun observeAll(): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(c: ChannelEntity)

    @Query("DELETE FROM channels WHERE name = :name")
    suspend fun delete(name: String)
}

@Database(
    entities = [MessageEntity::class, PeerEntity::class, ChannelEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun peers(): PeerDao
    abstract fun channels(): ChannelDao
}
