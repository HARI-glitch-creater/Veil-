package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VeilDao {

    // Backend Profiles
    @Query("SELECT * FROM backend_profiles ORDER BY isActive DESC, createdAt DESC")
    fun observeBackendProfiles(): Flow<List<BackendProfileEntity>>

    @Query("SELECT * FROM backend_profiles ORDER BY isActive DESC, createdAt DESC")
    suspend fun getAllBackendProfiles(): List<BackendProfileEntity>

    @Query("SELECT * FROM backend_profiles WHERE id = :id LIMIT 1")
    suspend fun getBackendProfileById(id: String): BackendProfileEntity?

    @Query("SELECT * FROM backend_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveBackendProfile(): BackendProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBackendProfile(profile: BackendProfileEntity)

    @Query("UPDATE backend_profiles SET isActive = CASE WHEN id = :profileId THEN 1 ELSE 0 END")
    suspend fun setActiveBackendProfile(profileId: String)

    @Query("DELETE FROM backend_profiles WHERE id = :profileId")
    suspend fun deleteBackendProfile(profileId: String)

    // Veil Rooms
    @Query("SELECT * FROM veil_rooms ORDER BY lastMessageTimestamp DESC")
    fun observeRooms(): Flow<List<VeilRoomEntity>>

    @Query("SELECT * FROM veil_rooms WHERE id = :roomId LIMIT 1")
    fun observeRoomById(roomId: String): Flow<VeilRoomEntity?>

    @Query("SELECT * FROM veil_rooms WHERE id = :roomId LIMIT 1")
    suspend fun getRoomById(roomId: String): VeilRoomEntity?

    @Query("SELECT * FROM veil_rooms WHERE veilCode = :veilCode LIMIT 1")
    suspend fun getRoomByCode(veilCode: String): VeilRoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoom(room: VeilRoomEntity)

    @Update
    suspend fun updateRoom(room: VeilRoomEntity)

    @Query("UPDATE veil_rooms SET isVerified = :verified WHERE id = :roomId")
    suspend fun setRoomVerified(roomId: String, verified: Boolean)

    @Query("UPDATE veil_rooms SET unreadCount = 0 WHERE id = :roomId")
    suspend fun clearUnreadCount(roomId: String)

    @Query("UPDATE veil_rooms SET lastMessagePreview = :preview, lastMessageTimestamp = :timestamp WHERE id = :roomId")
    suspend fun updateRoomLastMessage(roomId: String, preview: String, timestamp: Long)

    @Query("DELETE FROM veil_rooms WHERE id = :roomId")
    suspend fun deleteRoom(roomId: String)

    // Messages
    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    fun observeMessagesForRoom(roomId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE roomId = :roomId AND syncStatus IN ('PENDING', 'QUEUED_OFFLINE', 'FAILED') ORDER BY timestamp ASC")
    suspend fun getUnsyncedMessages(roomId: String): List<MessageEntity>

    @Query("SELECT id FROM messages WHERE roomId = :roomId")
    suspend fun getMessageIdsForRoom(roomId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessageIgnore(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("UPDATE messages SET syncStatus = :status, remoteMediaUrl = :remoteMediaUrl WHERE id = :messageId")
    suspend fun updateMessageSync(messageId: String, status: SyncStatus, remoteMediaUrl: String)

    @Query("DELETE FROM messages WHERE roomId = :roomId")
    suspend fun deleteMessagesForRoom(roomId: String)
}
