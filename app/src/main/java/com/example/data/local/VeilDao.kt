package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VeilDao {

    // Local Device-Bound User Profile
    @Query("SELECT * FROM user_profile WHERE id = 'local_user' LIMIT 1")
    fun observeUserProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 'local_user' LIMIT 1")
    suspend fun getUserProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProfile(profile: UserProfileEntity)

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
    @Query("SELECT * FROM veil_rooms ORDER BY isActiveRoom DESC, lastMessageTimestamp DESC")
    fun observeRooms(): Flow<List<VeilRoomEntity>>

    @Query("SELECT * FROM veil_rooms ORDER BY isActiveRoom DESC, lastMessageTimestamp DESC")
    suspend fun getAllRooms(): List<VeilRoomEntity>

    @Query("SELECT * FROM veil_rooms WHERE isActiveRoom = 1 LIMIT 1")
    fun observeActiveRoom(): Flow<VeilRoomEntity?>

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

    @Query("UPDATE veil_rooms SET isActiveRoom = CASE WHEN id = :roomId THEN 1 ELSE 0 END")
    suspend fun setActiveRoom(roomId: String)

    @Query("UPDATE veil_rooms SET isVerified = :verified WHERE id = :roomId")
    suspend fun setRoomVerified(roomId: String, verified: Boolean)

    @Query("UPDATE veil_rooms SET unreadCount = 0 WHERE id = :roomId")
    suspend fun clearUnreadCount(roomId: String)

    @Query("UPDATE veil_rooms SET lastMessagePreview = :preview, lastMessageTimestamp = :timestamp WHERE id = :roomId")
    suspend fun updateRoomLastMessage(roomId: String, preview: String, timestamp: Long)

    @Query("UPDATE veil_rooms SET hostPublicKey = :hostPublicKey, hostDeviceId = :hostDeviceId WHERE id = :roomId")
    suspend fun updateRoomHostInfo(roomId: String, hostPublicKey: String, hostDeviceId: String)

    @Query("DELETE FROM veil_rooms WHERE id = :roomId")
    suspend fun deleteRoom(roomId: String)

    // Room Members (Profiles Directory in Active Room)
    @Query("SELECT * FROM room_members WHERE roomId = :roomId ORDER BY lastMessageTimestamp DESC, username ASC")
    fun observeRoomMembers(roomId: String): Flow<List<RoomMemberEntity>>

    @Query("SELECT * FROM room_members WHERE roomId = :roomId")
    suspend fun getRoomMembers(roomId: String): List<RoomMemberEntity>

    @Query("SELECT * FROM room_members WHERE roomId = :roomId AND publicKey = :publicKey LIMIT 1")
    fun observeRoomMember(roomId: String, publicKey: String): Flow<RoomMemberEntity?>

    @Query("SELECT * FROM room_members WHERE roomId = :roomId AND publicKey = :publicKey LIMIT 1")
    suspend fun getRoomMember(roomId: String, publicKey: String): RoomMemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoomMember(member: RoomMemberEntity)

    @Query("UPDATE room_members SET isMuted = :muted WHERE roomId = :roomId AND publicKey = :publicKey")
    suspend fun setMemberMuted(roomId: String, publicKey: String, muted: Boolean)

    @Query("UPDATE room_members SET isBlocked = :blocked WHERE roomId = :roomId AND publicKey = :publicKey")
    suspend fun setMemberBlocked(roomId: String, publicKey: String, blocked: Boolean)

    @Query("UPDATE room_members SET isVerified = :verified WHERE roomId = :roomId AND publicKey = :publicKey")
    suspend fun setMemberVerified(roomId: String, publicKey: String, verified: Boolean)

    @Query("UPDATE room_members SET lastMessagePreview = :preview, lastMessageTimestamp = :timestamp, unreadCount = unreadCount + :unreadDelta WHERE roomId = :roomId AND publicKey = :publicKey")
    suspend fun updateMemberLastMessage(roomId: String, publicKey: String, preview: String, timestamp: Long, unreadDelta: Int)

    @Query("UPDATE room_members SET unreadCount = 0 WHERE roomId = :roomId AND publicKey = :publicKey")
    suspend fun clearMemberUnread(roomId: String, publicKey: String)

    @Query("DELETE FROM room_members WHERE roomId = :roomId AND publicKey = :publicKey")
    suspend fun deleteRoomMember(roomId: String, publicKey: String)

    @Query("DELETE FROM room_members WHERE roomId = :roomId")
    suspend fun deleteMembersForRoom(roomId: String)

    // Communities (Default + Private 3+ Member)
    @Query("SELECT * FROM communities WHERE roomId = :roomId ORDER BY isDefault DESC, lastMessageTimestamp DESC")
    fun observeCommunitiesForRoom(roomId: String): Flow<List<CommunityEntity>>

    @Query("SELECT * FROM communities WHERE roomId = :roomId")
    suspend fun getCommunitiesForRoom(roomId: String): List<CommunityEntity>

    @Query("SELECT * FROM communities WHERE id = :communityId LIMIT 1")
    fun observeCommunityById(communityId: String): Flow<CommunityEntity?>

    @Query("SELECT * FROM communities WHERE id = :communityId LIMIT 1")
    suspend fun getCommunityById(communityId: String): CommunityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCommunity(community: CommunityEntity)

    @Query("UPDATE communities SET lastMessagePreview = :preview, lastMessageTimestamp = :timestamp, unreadCount = unreadCount + :unreadDelta WHERE id = :communityId")
    suspend fun updateCommunityLastMessage(communityId: String, preview: String, timestamp: Long, unreadDelta: Int)

    @Query("UPDATE communities SET unreadCount = 0 WHERE id = :communityId")
    suspend fun clearCommunityUnread(communityId: String)

    @Query("DELETE FROM communities WHERE roomId = :roomId")
    suspend fun deleteCommunitiesForRoom(roomId: String)

    @Query("SELECT * FROM community_members WHERE communityId = :communityId")
    fun observeCommunityMembers(communityId: String): Flow<List<CommunityMemberEntity>>

    @Query("SELECT * FROM community_members WHERE communityId = :communityId")
    suspend fun getCommunityMembers(communityId: String): List<CommunityMemberEntity>

    @Query("UPDATE communities SET memberCount = :memberCount WHERE id = :communityId")
    suspend fun updateCommunityMemberCount(communityId: String, memberCount: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCommunityMember(member: CommunityMemberEntity)

    @Query("DELETE FROM community_members WHERE communityId = :communityId AND memberPublicKey = :memberPublicKey")
    suspend fun deleteCommunityMemberByKey(communityId: String, memberPublicKey: String)

    // Messages (Scoped to Direct Peer or Community)
    @Query("SELECT * FROM messages WHERE roomId = :roomId AND channelType = :channelType AND targetId = :targetId ORDER BY timestamp ASC")
    fun observeMessagesForChannel(roomId: String, channelType: ChannelType, targetId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE roomId = :roomId AND syncStatus IN ('PENDING', 'QUEUED_OFFLINE', 'FAILED') ORDER BY timestamp ASC")
    suspend fun getUnsyncedMessages(roomId: String): List<MessageEntity>

    @Query("SELECT id FROM messages WHERE roomId = :roomId")
    suspend fun getMessageIdsForRoom(roomId: String): List<String>

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessageIgnore(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("UPDATE messages SET syncStatus = :status, remoteMediaUrl = :remoteMediaUrl WHERE id = :messageId")
    suspend fun updateMessageSync(messageId: String, status: SyncStatus, remoteMediaUrl: String)

    @Query("UPDATE messages SET viewsConsumed = :viewsConsumed, isDisappeared = :isDisappeared, decryptedText = :decryptedText, localMediaPath = :localMediaPath, remoteMediaUrl = :remoteMediaUrl, ciphertextPayload = :ciphertextPayload WHERE id = :messageId")
    suspend fun updateMessageEphemeralState(
        messageId: String,
        viewsConsumed: Int,
        isDisappeared: Boolean,
        decryptedText: String,
        localMediaPath: String,
        remoteMediaUrl: String,
        ciphertextPayload: String
    )

    @Query("DELETE FROM messages WHERE roomId = :roomId")
    suspend fun deleteMessagesForRoom(roomId: String)

    // Call History (90-Day Auto-Pruned)
    @Query("SELECT * FROM call_history WHERE roomId = :roomId ORDER BY timestamp DESC")
    fun observeCallHistory(roomId: String): Flow<List<CallHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCallHistory(entry: CallHistoryEntity)

    @Query("UPDATE call_history SET isSeen = 1 WHERE roomId = :roomId")
    suspend fun markMissedCallsSeen(roomId: String)

    @Query("DELETE FROM call_history WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteCallHistoryOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM call_history WHERE roomId = :roomId")
    suspend fun deleteCallHistoryForRoom(roomId: String)
}
