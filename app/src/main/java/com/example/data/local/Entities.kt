package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BackendType {
    SUPABASE,
    SELF_HOSTED
}

enum class AuthType {
    NONE,
    BEARER,
    API_KEY_HEADER,
    BASIC
}

enum class RoomRole {
    HOST,
    GUEST
}

enum class MessageContentType {
    TEXT,
    IMAGE,
    VIDEO,
    DOCUMENT,
    LOCATION,
    VOICE
}

enum class SyncStatus {
    PENDING,
    SENT,
    SYNCED,
    QUEUED_OFFLINE,
    FAILED
}

@Entity(tableName = "backend_profiles")
data class BackendProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val backendType: BackendType,
    val isActive: Boolean = false,
    val baseUrl: String,
    val encryptedApiKey: String = "",
    val supabaseBucket: String = "veil-media",
    val authType: AuthType = AuthType.BEARER,
    val authHeaderName: String = "X-API-Key",
    val encryptedCustomHeadersJson: String = "",
    val roomsPath: String = "/api/v1/rooms",
    val membersPath: String = "/api/v1/members",
    val messagesPath: String = "/api/v1/messages",
    val healthPath: String = "/api/v1/health",
    val webSocketUrl: String = "",
    val mediaUploadUrl: String = "",
    val webhookUrl: String = "",
    val pollIntervalSec: Int = 5,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "veil_rooms")
data class VeilRoomEntity(
    @PrimaryKey val id: String,
    val veilCode: String,
    val roomName: String,
    val role: RoomRole,
    val backendProfileId: String,
    val peerHandle: String = "",
    val peerPublicKey: String = "",
    val encryptedRoomKey: String = "",
    val safetyNumber: String = "",
    val isVerified: Boolean = false,
    val lastMessagePreview: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val isOutgoing: Boolean,
    val senderHandle: String,
    val senderPublicKey: String,
    val contentType: MessageContentType,
    val ciphertextIv: String,
    val ciphertextPayload: String,
    val decryptedText: String = "",
    val localMediaPath: String = "",
    val remoteMediaUrl: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val fileSizeBytes: Long = 0L,
    val audioDurationMs: Long = 0L,
    val waveformPeaks: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
