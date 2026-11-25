package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.crypto.LocalIdentity
import com.example.crypto.ParsedRoomInvite
import com.example.crypto.VeilCryptoEngine
import com.example.data.local.AuthType
import com.example.data.local.BackendProfileEntity
import com.example.data.local.BackendType
import com.example.data.local.MediaVaultStorage
import com.example.data.local.MessageContentType
import com.example.data.local.MessageEntity
import com.example.data.local.RoomRole
import com.example.data.local.SyncStatus
import com.example.data.local.VeilDatabase
import com.example.data.local.VeilRoomEntity
import com.example.data.network.ConnectionTestResult
import com.example.data.network.RemoteEncryptedMessage
import com.example.data.network.ResolvedBackendConfig
import com.example.data.network.VeilBackendClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class BackendProfileDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val backendType: BackendType,
    val baseUrl: String,
    val apiKey: String,
    val supabaseBucket: String = "veil-media",
    val authType: AuthType = AuthType.BEARER,
    val authHeaderName: String = "X-API-Key",
    val customHeadersRaw: String = "",
    val roomsPath: String = "/api/v1/rooms",
    val membersPath: String = "/api/v1/members",
    val messagesPath: String = "/api/v1/messages",
    val healthPath: String = "/api/v1/health",
    val webSocketUrl: String = "",
    val mediaUploadUrl: String = "",
    val webhookUrl: String = "",
    val pollIntervalSec: Int = 5
)

data class HostRoomOutcome(
    val room: VeilRoomEntity,
    val inviteUri: String,
    val remoteSynced: Boolean,
    val note: String
)

class VeilRepository(context: Context) {

    private val database = VeilDatabase.getInstance(context)
    private val dao = database.veilDao()
    val crypto = VeilCryptoEngine(context)
    val mediaStorage = MediaVaultStorage(context)
    val backendClient = VeilBackendClient()

    val roomsFlow: Flow<List<VeilRoomEntity>> = dao.observeRooms()
    val backendProfilesFlow: Flow<List<BackendProfileEntity>> = dao.observeBackendProfiles()

    fun observeRoom(roomId: String): Flow<VeilRoomEntity?> = dao.observeRoomById(roomId)
    fun observeMessages(roomId: String): Flow<List<MessageEntity>> = dao.observeMessagesForRoom(roomId)

    fun getIdentity(): LocalIdentity = crypto.getOrCreateIdentity()
    fun updateHandle(handle: String): LocalIdentity = crypto.updateHandle(handle)
    fun buildIdentityUri(): String = crypto.buildIdentityUri(getIdentity())

    suspend fun getAllProfiles(): List<BackendProfileEntity> = dao.getAllBackendProfiles()

    suspend fun resolveBackendConfig(profileId: String): ResolvedBackendConfig? {
        val profile = dao.getBackendProfileById(profileId) ?: dao.getActiveBackendProfile() ?: return null
        return resolveEntity(profile)
    }

    fun resolveEntity(profile: BackendProfileEntity): ResolvedBackendConfig {
        val apiKey = crypto.unprotectString(profile.encryptedApiKey)
        val headersJson = crypto.unprotectString(profile.encryptedCustomHeadersJson)
        val headersMap = parseHeadersMap(headersJson)
        return ResolvedBackendConfig(
            profile = profile,
            apiKey = apiKey,
            customHeaders = headersMap
        )
    }

    suspend fun saveBackendProfile(draft: BackendProfileDraft, makeActive: Boolean = true): Result<BackendProfileEntity> {
        val validUrl = backendClient.validateHttpUrl(draft.baseUrl).getOrElse {
            return Result.failure(it)
        }
        if (draft.webSocketUrl.isNotBlank()) {
            backendClient.validateWebSocketUrl(draft.webSocketUrl).getOrElse {
                return Result.failure(it)
            }
        }
        if (draft.mediaUploadUrl.isNotBlank()) {
            backendClient.validateHttpUrl(draft.mediaUploadUrl).getOrElse {
                return Result.failure(IllegalArgumentException("Media URL: ${it.message}"))
            }
        }
        if (draft.webhookUrl.isNotBlank()) {
            backendClient.validateHttpUrl(draft.webhookUrl).getOrElse {
                return Result.failure(IllegalArgumentException("Webhook URL: ${it.message}"))
            }
        }

        val entity = BackendProfileEntity(
            id = draft.id,
            name = draft.name.trim().ifBlank {
                if (draft.backendType == BackendType.SUPABASE) "My Supabase" else "Homelab Node"
            },
            backendType = draft.backendType,
            isActive = makeActive,
            baseUrl = validUrl,
            encryptedApiKey = crypto.protectString(draft.apiKey.trim()),
            supabaseBucket = draft.supabaseBucket.trim().ifBlank { "veil-media" },
            authType = draft.authType,
            authHeaderName = draft.authHeaderName.trim().ifBlank { "X-API-Key" },
            encryptedCustomHeadersJson = crypto.protectString(serializeHeadersMap(draft.customHeadersRaw)),
            roomsPath = draft.roomsPath.trim().ifBlank { "/api/v1/rooms" },
            membersPath = draft.membersPath.trim().ifBlank { "/api/v1/members" },
            messagesPath = draft.messagesPath.trim().ifBlank { "/api/v1/messages" },
            healthPath = draft.healthPath.trim().ifBlank { "/api/v1/health" },
            webSocketUrl = draft.webSocketUrl.trim(),
            mediaUploadUrl = draft.mediaUploadUrl.trim(),
            webhookUrl = draft.webhookUrl.trim(),
            pollIntervalSec = draft.pollIntervalSec.coerceIn(2, 60)
        )
        dao.upsertBackendProfile(entity)
        if (makeActive) {
            dao.setActiveBackendProfile(entity.id)
        }
        return Result.success(entity)
    }

    suspend fun setActiveBackendProfile(profileId: String) {
        dao.setActiveBackendProfile(profileId)
    }

    suspend fun deleteBackendProfile(profileId: String) {
        dao.deleteBackendProfile(profileId)
    }

    suspend fun testDraftConnection(draft: BackendProfileDraft): ConnectionTestResult {
        val tempEntity = BackendProfileEntity(
            id = draft.id,
            name = draft.name,
            backendType = draft.backendType,
            baseUrl = draft.baseUrl.trim(),
            supabaseBucket = draft.supabaseBucket,
            authType = draft.authType,
            authHeaderName = draft.authHeaderName,
            roomsPath = draft.roomsPath,
            membersPath = draft.membersPath,
            messagesPath = draft.messagesPath,
            healthPath = draft.healthPath,
            webSocketUrl = draft.webSocketUrl,
            mediaUploadUrl = draft.mediaUploadUrl,
            webhookUrl = draft.webhookUrl
        )
        return backendClient.testConnection(
            ResolvedBackendConfig(
                profile = tempEntity,
                apiKey = draft.apiKey.trim(),
                customHeaders = parseHeadersMap(serializeHeadersMap(draft.customHeadersRaw))
            )
        )
    }

    suspend fun testSavedProfile(profile: BackendProfileEntity): ConnectionTestResult {
        return backendClient.testConnection(resolveEntity(profile))
    }

    /**
     * Connects the host's Supabase or Self-Hosted database, creates a Veil Room,
     * and generates a 12-digit Veil Code + shareable Room Invite URI.
     */
    suspend fun hostVeilRoom(
        draft: BackendProfileDraft,
        roomName: String
    ): Result<HostRoomOutcome> = withContext(Dispatchers.IO) {
        val savedProfile = saveBackendProfile(draft, makeActive = true).getOrElse {
            return@withContext Result.failure(it)
        }
        val config = resolveEntity(savedProfile)
        val identity = getIdentity()
        val veilCode = crypto.generateVeilCode()
        val roomKeyB64 = crypto.generateRandomRoomKeyBase64()
        val cleanName = roomName.trim().ifBlank { "Veil Room ${veilCode.takeLast(4)}" }

        val remoteResult = backendClient.createRoom(
            config = config,
            roomCode = veilCode,
            hostPublicKey = identity.publicKeyBase64,
            hostHandle = identity.handle
        )

        val roomEntity = VeilRoomEntity(
            id = UUID.randomUUID().toString(),
            veilCode = veilCode,
            roomName = cleanName,
            role = RoomRole.HOST,
            backendProfileId = savedProfile.id,
            peerHandle = "",
            peerPublicKey = "",
            encryptedRoomKey = crypto.protectString(roomKeyB64),
            safetyNumber = "",
            isVerified = false,
            lastMessagePreview = "Room created · Waiting for peer",
            lastMessageTimestamp = System.currentTimeMillis()
        )
        dao.upsertRoom(roomEntity)

        val inviteUri = crypto.buildRoomInviteUri(
            veilCode = veilCode,
            roomName = cleanName,
            backendType = savedProfile.backendType,
            baseUrl = savedProfile.baseUrl,
            apiKey = config.apiKey,
            authType = savedProfile.authType,
            authHeaderName = savedProfile.authHeaderName,
            hostPublicKey = identity.publicKeyBase64,
            hostHandle = identity.handle,
            roomKeyBase64 = roomKeyB64,
            webSocketUrl = savedProfile.webSocketUrl,
            mediaUploadUrl = savedProfile.mediaUploadUrl
        )

        val note = if (remoteResult.isSuccess) {
            "Connected to ${savedProfile.name} · 12-digit Veil Code ready"
        } else {
            "Room created locally — remote sync will retry when ${savedProfile.name} is reachable"
        }

        Result.success(
            HostRoomOutcome(
                room = roomEntity,
                inviteUri = inviteUri,
                remoteSynced = remoteResult.isSuccess,
                note = note
            )
        )
    }

    /**
     * Joins an existing Veil Room using a 12-digit Veil Code (either against an already
     * configured backend, an inline Supabase/Self-Hosted connection, or a scanned QR/Invite URI).
     */
    suspend fun joinVeilRoom(
        rawVeilCode: String,
        existingProfileId: String?,
        inlineDraft: BackendProfileDraft?,
        parsedInvite: ParsedRoomInvite? = null
    ): Result<VeilRoomEntity> = withContext(Dispatchers.IO) {
        val code = (parsedInvite?.veilCode ?: rawVeilCode).filter { it.isDigit() }
        if (code.length != 12) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid 12-digit Veil Code"))
        }

        val profile: BackendProfileEntity = when {
            parsedInvite != null && parsedInvite.baseUrl.isNotBlank() -> {
                val inviteDraft = BackendProfileDraft(
                    name = "${parsedInvite.roomName} Backend",
                    backendType = parsedInvite.backendType,
                    baseUrl = parsedInvite.baseUrl,
                    apiKey = parsedInvite.apiKey,
                    authType = parsedInvite.authType,
                    authHeaderName = parsedInvite.authHeaderName,
                    webSocketUrl = parsedInvite.webSocketUrl,
                    mediaUploadUrl = parsedInvite.mediaUploadUrl
                )
                saveBackendProfile(inviteDraft, makeActive = true).getOrElse {
                    return@withContext Result.failure(it)
                }
            }
            inlineDraft != null && inlineDraft.baseUrl.isNotBlank() -> {
                saveBackendProfile(inlineDraft, makeActive = true).getOrElse {
                    return@withContext Result.failure(it)
                }
            }
            !existingProfileId.isNullOrBlank() -> {
                dao.getBackendProfileById(existingProfileId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Selected backend profile not found"))
            }
            else -> {
                dao.getActiveBackendProfile()
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Scan the Host's QR / Invite or enter the host's Supabase or server URL")
                    )
            }
        }

        val config = resolveEntity(profile)
        val identity = getIdentity()

        // Discover host public key from backend if not already carried in invite
        val remoteRoom = backendClient.fetchRoom(config, code).getOrNull()
        val hostPublicKey = when {
            !parsedInvite?.hostPublicKey.isNullOrBlank() && crypto.isValidPublicKey(parsedInvite!!.hostPublicKey) ->
                parsedInvite.hostPublicKey
            !remoteRoom?.hostPublicKey.isNullOrBlank() && crypto.isValidPublicKey(remoteRoom!!.hostPublicKey) ->
                remoteRoom.hostPublicKey
            else -> ""
        }
        val hostHandle = parsedInvite?.hostHandle?.ifBlank { null }
            ?: remoteRoom?.hostHandle?.ifBlank { null }
            ?: "Host"

        // Register our public key with the room so the Host discovers us & upgrades to ECDH
        backendClient.registerMember(config, code, identity.handle, identity.publicKeyBase64)

        val safetyNumber = if (hostPublicKey.isNotBlank() && hostPublicKey != identity.publicKeyBase64) {
            crypto.computeSafetyNumber(identity.publicKeyBase64, hostPublicKey)
        } else {
            ""
        }

        val existingRoom = dao.getRoomByCode(code)
        val roomEntity = VeilRoomEntity(
            id = existingRoom?.id ?: UUID.randomUUID().toString(),
            veilCode = code,
            roomName = parsedInvite?.roomName?.ifBlank { null }
                ?: existingRoom?.roomName
                ?: "Veil Room ${code.takeLast(4)}",
            role = existingRoom?.role ?: RoomRole.GUEST,
            backendProfileId = profile.id,
            peerHandle = hostHandle,
            peerPublicKey = hostPublicKey,
            encryptedRoomKey = if (!parsedInvite?.roomKeyBase64.isNullOrBlank()) {
                crypto.protectString(parsedInvite!!.roomKeyBase64)
            } else {
                existingRoom?.encryptedRoomKey.orEmpty()
            },
            safetyNumber = safetyNumber,
            isVerified = existingRoom?.isVerified ?: false,
            lastMessagePreview = existingRoom?.lastMessagePreview ?: "Connected via E2EE",
            lastMessageTimestamp = System.currentTimeMillis()
        )
        dao.upsertRoom(roomEntity)
        syncRoom(roomEntity.id)
        Result.success(roomEntity)
    }

    suspend fun buildInviteUriForRoom(room: VeilRoomEntity): String? {
        val config = resolveBackendConfig(room.backendProfileId) ?: return null
        val identity = getIdentity()
        val roomKey = crypto.unprotectString(room.encryptedRoomKey)
        return crypto.buildRoomInviteUri(
            veilCode = room.veilCode,
            roomName = room.roomName,
            backendType = config.profile.backendType,
            baseUrl = config.profile.baseUrl,
            apiKey = config.apiKey,
            authType = config.profile.authType,
            authHeaderName = config.profile.authHeaderName,
            hostPublicKey = if (room.role == RoomRole.HOST) identity.publicKeyBase64 else room.peerPublicKey,
            hostHandle = if (room.role == RoomRole.HOST) identity.handle else room.peerHandle.ifBlank { "Host" },
            roomKeyBase64 = roomKey,
            webSocketUrl = config.profile.webSocketUrl,
            mediaUploadUrl = config.profile.mediaUploadUrl
        )
    }

    suspend fun setRoomVerified(roomId: String, verified: Boolean) {
        dao.setRoomVerified(roomId, verified)
    }

    suspend fun clearUnread(roomId: String) {
        dao.clearUnreadCount(roomId)
    }

    suspend fun deleteRoom(roomId: String) {
        dao.deleteMessagesForRoom(roomId)
        dao.deleteRoom(roomId)
    }

    suspend fun sendTextMessage(roomId: String, text: String): Result<Unit> {
        val clean = text.trim()
        if (clean.isEmpty()) return Result.success(Unit)
        return sendRichMessage(
            roomId = roomId,
            contentType = MessageContentType.TEXT,
            decryptedText = clean
        )
    }

    suspend fun sendMediaFromUri(
        roomId: String,
        uri: Uri,
        contentType: MessageContentType
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val stored = mediaStorage.copyFromUri(uri, contentType.name.lowercase())
            ?: return@withContext Result.failure(IllegalStateException("Could not read selected file"))
        sendRichMessage(
            roomId = roomId,
            contentType = contentType,
            decryptedText = stored.displayName,
            localMediaPath = stored.file.absolutePath,
            fileName = stored.displayName,
            mimeType = stored.mimeType,
            fileSizeBytes = stored.sizeBytes
        )
    }

    suspend fun sendVoiceNote(
        roomId: String,
        audioFile: File,
        durationMs: Long,
        waveformPeaks: List<Int>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("Voice recording was empty"))
        }
        sendRichMessage(
            roomId = roomId,
            contentType = MessageContentType.VOICE,
            decryptedText = "Voice note",
            localMediaPath = audioFile.absolutePath,
            fileName = audioFile.name,
            mimeType = "audio/mp4",
            fileSizeBytes = audioFile.length(),
            audioDurationMs = durationMs,
            waveformPeaks = waveformPeaks.joinToString(",")
        )
    }

    suspend fun sendLocation(
        roomId: String,
        latitude: Double,
        longitude: Double,
        label: String
    ): Result<Unit> {
        val formatted = label.trim().ifBlank {
            "%.5f, %.5f".format(latitude, longitude)
        }
        return sendRichMessage(
            roomId = roomId,
            contentType = MessageContentType.LOCATION,
            decryptedText = formatted,
            latitude = latitude,
            longitude = longitude,
            locationLabel = formatted
        )
    }

    private suspend fun sendRichMessage(
        roomId: String,
        contentType: MessageContentType,
        decryptedText: String,
        localMediaPath: String = "",
        fileName: String = "",
        mimeType: String = "",
        fileSizeBytes: Long = 0L,
        audioDurationMs: Long = 0L,
        waveformPeaks: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
        locationLabel: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val room = dao.getRoomById(roomId)
            ?: return@withContext Result.failure(IllegalArgumentException("Room not found"))
        val config = resolveBackendConfig(room.backendProfileId)
        val identity = getIdentity()
        val keyBytes = resolvePrimaryRoomKey(room)
        val msgId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // 1. If attachment exists, encrypt raw file bytes with AES-256-GCM before upload or inline embed
        var remoteMediaUrl = ""
        var inlineMediaB64 = ""
        if (localMediaPath.isNotBlank()) {
            val file = File(localMediaPath)
            if (file.exists()) {
                val rawBytes = file.readBytes()
                val encryptedPacket = crypto.encryptBlobPacket(keyBytes, rawBytes)
                if (config != null) {
                    val uploadResult = backendClient.uploadEncryptedBlob(
                        config = config,
                        roomCode = room.veilCode,
                        blobId = msgId,
                        encryptedBytes = encryptedPacket
                    )
                    if (uploadResult.isSuccess) {
                        remoteMediaUrl = uploadResult.getOrDefault("")
                    } else if (rawBytes.size <= MAX_INLINE_MEDIA_BYTES) {
                        // Fallback: embed inside the encrypted AES-GCM JSON envelope so media works
                        // even when a user only created Supabase tables without a Storage bucket.
                        inlineMediaB64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
                    }
                } else if (rawBytes.size <= MAX_INLINE_MEDIA_BYTES) {
                    inlineMediaB64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
                }
            }
        }

        // 2. Build inner plaintext JSON and encrypt EVERYTHING into opaque ciphertext
        val innerJson = JSONObject().apply {
            put("t", contentType.name)
            put("h", identity.handle)
            put("txt", decryptedText)
            if (fileName.isNotBlank()) put("fn", fileName)
            if (mimeType.isNotBlank()) put("mt", mimeType)
            if (fileSizeBytes > 0L) put("sz", fileSizeBytes)
            if (remoteMediaUrl.isNotBlank()) put("mu", remoteMediaUrl)
            if (inlineMediaB64.isNotBlank()) put("mb", inlineMediaB64)
            if (audioDurationMs > 0L) put("dur", audioDurationMs)
            if (waveformPeaks.isNotBlank()) put("wf", waveformPeaks)
            if (latitude != null && longitude != null) {
                put("lat", latitude)
                put("lon", longitude)
                put("loc", locationLabel)
            }
        }.toString()

        val envelope = crypto.encryptText(keyBytes, innerJson)

        val entity = MessageEntity(
            id = msgId,
            roomId = room.id,
            isOutgoing = true,
            senderHandle = identity.handle,
            senderPublicKey = identity.publicKeyBase64,
            contentType = contentType,
            ciphertextIv = envelope.ivBase64,
            ciphertextPayload = envelope.ciphertextBase64,
            decryptedText = decryptedText,
            localMediaPath = localMediaPath,
            remoteMediaUrl = remoteMediaUrl,
            fileName = fileName,
            mimeType = mimeType,
            fileSizeBytes = fileSizeBytes,
            audioDurationMs = audioDurationMs,
            waveformPeaks = waveformPeaks,
            latitude = latitude,
            longitude = longitude,
            locationLabel = locationLabel,
            timestamp = now,
            syncStatus = SyncStatus.PENDING
        )
        dao.upsertMessage(entity)
        dao.updateRoomLastMessage(room.id, previewFor(contentType, decryptedText, fileName), now)

        // 3. Dispatch ONLY the opaque ciphertext envelope to Supabase / Self-Hosted Server
        if (config != null) {
            val dispatch = backendClient.sendEncryptedMessage(
                config = config,
                message = RemoteEncryptedMessage(
                    id = msgId,
                    roomCode = room.veilCode,
                    senderPublicKey = identity.publicKeyBase64,
                    iv = envelope.ivBase64,
                    ciphertext = envelope.ciphertextBase64,
                    createdAt = now
                )
            )
            val status = if (dispatch.isSuccess) SyncStatus.SENT else SyncStatus.QUEUED_OFFLINE
            dao.updateMessageSync(msgId, status, remoteMediaUrl)
        } else {
            dao.updateMessageSync(msgId, SyncStatus.QUEUED_OFFLINE, remoteMediaUrl)
        }

        Result.success(Unit)
    }

    /**
     * Syncs peer public keys, flushes queued offline messages, and pulls/decrypts new messages
     * from the room's configured Supabase or Self-Hosted backend.
     */
    suspend fun syncRoom(roomId: String) = withContext(Dispatchers.IO) {
        var room = dao.getRoomById(roomId) ?: return@withContext
        val config = resolveBackendConfig(room.backendProfileId) ?: return@withContext
        val identity = getIdentity()

        // 1. Discover any new peer public keys from veil_members so ECDH & 60-digit safety number update
        backendClient.fetchMembers(config, room.veilCode).onSuccess { members ->
            val peer = members.firstOrNull {
                it.publicKey.isNotBlank() &&
                    it.publicKey != identity.publicKeyBase64 &&
                    crypto.isValidPublicKey(it.publicKey)
            }
            if (peer != null && peer.publicKey != room.peerPublicKey) {
                val safety = crypto.computeSafetyNumber(identity.publicKeyBase64, peer.publicKey)
                room = room.copy(
                    peerHandle = peer.memberHandle.ifBlank { room.peerHandle },
                    peerPublicKey = peer.publicKey,
                    safetyNumber = safety
                )
                dao.updateRoom(room)
            }
        }

        // 2. Retry any locally queued outgoing messages
        val unsynced = dao.getUnsyncedMessages(room.id)
        for (pending in unsynced) {
            val res = backendClient.sendEncryptedMessage(
                config = config,
                message = RemoteEncryptedMessage(
                    id = pending.id,
                    roomCode = room.veilCode,
                    senderPublicKey = pending.senderPublicKey,
                    iv = pending.ciphertextIv,
                    ciphertext = pending.ciphertextPayload,
                    createdAt = pending.timestamp
                )
            )
            if (res.isSuccess) {
                dao.updateMessageSync(pending.id, SyncStatus.SENT, pending.remoteMediaUrl)
            }
        }

        // 3. Pull encrypted messages from Supabase / Self-Hosted server and decrypt locally
        val knownIds = dao.getMessageIdsForRoom(room.id).toHashSet()
        backendClient.fetchEncryptedMessages(config, room.veilCode).onSuccess { remoteMessages ->
            for (remote in remoteMessages) {
                if (knownIds.contains(remote.id)) continue
                ingestRemoteEncryptedMessage(room, config, remote)
            }
        }
    }

    suspend fun ingestRemoteEncryptedMessage(
        room: VeilRoomEntity,
        config: ResolvedBackendConfig,
        remote: RemoteEncryptedMessage
    ) = withContext(Dispatchers.IO) {
        val identity = getIdentity()
        val candidateKeys = buildCandidateKeys(room, remote.senderPublicKey)
        var decryptedJson: String? = null
        var matchedKey: ByteArray? = null

        for (candidate in candidateKeys) {
            val attempt = runCatching {
                crypto.decryptText(candidate, remote.iv, remote.ciphertext)
            }.getOrNull()
            if (attempt != null) {
                decryptedJson = attempt
                matchedKey = candidate
                break
            }
        }

        val isOutgoing = remote.senderPublicKey == identity.publicKeyBase64
        if (decryptedJson == null || matchedKey == null) {
            // Store undecryptable envelope safely without crashing
            dao.insertMessageIgnore(
                MessageEntity(
                    id = remote.id,
                    roomId = room.id,
                    isOutgoing = isOutgoing,
                    senderHandle = if (isOutgoing) identity.handle else room.peerHandle.ifBlank { "Peer" },
                    senderPublicKey = remote.senderPublicKey,
                    contentType = MessageContentType.TEXT,
                    ciphertextIv = remote.iv,
                    ciphertextPayload = remote.ciphertext,
                    decryptedText = "Encrypted message (awaiting key sync)",
                    timestamp = remote.createdAt,
                    syncStatus = SyncStatus.SYNCED
                )
            )
            return@withContext
        }

        val obj = JSONObject(decryptedJson)
        val contentType = runCatching {
            MessageContentType.valueOf(obj.optString("t", "TEXT"))
        }.getOrDefault(MessageContentType.TEXT)
        val senderHandle = obj.optString("h", if (isOutgoing) identity.handle else room.peerHandle.ifBlank { "Peer" })
        val text = obj.optString("txt", "")
        val fileName = obj.optString("fn", "")
        val mimeType = obj.optString("mt", "")
        val fileSize = obj.optLong("sz", 0L)
        val mediaUrl = obj.optString("mu", "")
        val inlineB64 = obj.optString("mb", "")
        val durationMs = obj.optLong("dur", 0L)
        val waveform = obj.optString("wf", "")
        val lat = if (obj.has("lat")) obj.optDouble("lat") else null
        val lon = if (obj.has("lon")) obj.optDouble("lon") else null
        val locLabel = obj.optString("loc", "")

        // Restore encrypted media locally if present
        var localPath = ""
        if (inlineB64.isNotBlank()) {
            runCatching {
                val bytes = Base64.decode(inlineB64, Base64.DEFAULT)
                val saved = mediaStorage.saveDecryptedMedia(
                    bytes = bytes,
                    displayName = fileName.ifBlank { "media" },
                    mimeType = mimeType
                )
                localPath = saved.absolutePath
            }
        } else if (mediaUrl.isNotBlank()) {
            backendClient.downloadEncryptedBlob(config, mediaUrl).onSuccess { packet ->
                runCatching {
                    val decryptedBytes = crypto.decryptBlobPacket(matchedKey, packet)
                    val saved = mediaStorage.saveDecryptedMedia(
                        bytes = decryptedBytes,
                        displayName = fileName.ifBlank { "media" },
                        mimeType = mimeType
                    )
                    localPath = saved.absolutePath
                }
            }
        }

        val message = MessageEntity(
            id = remote.id,
            roomId = room.id,
            isOutgoing = isOutgoing,
            senderHandle = senderHandle,
            senderPublicKey = remote.senderPublicKey,
            contentType = contentType,
            ciphertextIv = remote.iv,
            ciphertextPayload = remote.ciphertext,
            decryptedText = text,
            localMediaPath = localPath,
            remoteMediaUrl = mediaUrl,
            fileName = fileName,
            mimeType = mimeType,
            fileSizeBytes = fileSize,
            audioDurationMs = durationMs,
            waveformPeaks = waveform,
            latitude = lat,
            longitude = lon,
            locationLabel = locLabel,
            timestamp = remote.createdAt,
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertMessageIgnore(message)
        dao.updateRoomLastMessage(
            roomId = room.id,
            preview = previewFor(contentType, text, fileName),
            timestamp = remote.createdAt
        )
    }

    private fun resolvePrimaryRoomKey(room: VeilRoomEntity): ByteArray {
        val identity = getIdentity()
        if (room.peerPublicKey.isNotBlank() &&
            room.peerPublicKey != identity.publicKeyBase64 &&
            crypto.isValidPublicKey(room.peerPublicKey)
        ) {
            return crypto.deriveEcdhRoomKey(room.peerPublicKey, room.veilCode)
        }
        val unwrappedRoomKey = crypto.unprotectString(room.encryptedRoomKey)
        if (unwrappedRoomKey.isNotBlank()) {
            return crypto.deriveSymmetricRoomKey(unwrappedRoomKey, room.veilCode)
        }
        return crypto.deriveEcdhRoomKey(identity.publicKeyBase64, room.veilCode)
    }

    private fun buildCandidateKeys(room: VeilRoomEntity, senderPublicKey: String): List<ByteArray> {
        val list = mutableListOf<ByteArray>()
        val identity = getIdentity()
        if (senderPublicKey.isNotBlank() &&
            senderPublicKey != identity.publicKeyBase64 &&
            crypto.isValidPublicKey(senderPublicKey)
        ) {
            runCatching { list.add(crypto.deriveEcdhRoomKey(senderPublicKey, room.veilCode)) }
        }
        if (room.peerPublicKey.isNotBlank() &&
            room.peerPublicKey != identity.publicKeyBase64 &&
            crypto.isValidPublicKey(room.peerPublicKey)
        ) {
            runCatching { list.add(crypto.deriveEcdhRoomKey(room.peerPublicKey, room.veilCode)) }
        }
        val unwrappedRoomKey = crypto.unprotectString(room.encryptedRoomKey)
        if (unwrappedRoomKey.isNotBlank()) {
            runCatching { list.add(crypto.deriveSymmetricRoomKey(unwrappedRoomKey, room.veilCode)) }
        }
        runCatching { list.add(crypto.deriveEcdhRoomKey(identity.publicKeyBase64, room.veilCode)) }
        return list
    }

    private fun previewFor(type: MessageContentType, text: String, fileName: String): String {
        return when (type) {
            MessageContentType.TEXT -> text.take(64)
            MessageContentType.IMAGE -> "Photo"
            MessageContentType.VIDEO -> "Video"
            MessageContentType.DOCUMENT -> if (fileName.isNotBlank()) "Document · $fileName" else "Document"
            MessageContentType.LOCATION -> "Location · $text"
            MessageContentType.VOICE -> "Voice note"
        }
    }

    private fun serializeHeadersMap(rawLines: String): String {
        val json = JSONObject()
        rawLines.lines().forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val k = line.substring(0, idx).trim()
                val v = line.substring(idx + 1).trim()
                if (k.isNotBlank() && v.isNotBlank()) {
                    json.put(k, v)
                }
            }
        }
        return if (json.length() == 0) "" else json.toString()
    }

    private fun parseHeadersMap(jsonStr: String): Map<String, String> {
        if (jsonStr.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(jsonStr)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, obj.optString(k))
                }
            }
        }.getOrDefault(emptyMap())
    }

    companion object {
        private const val MAX_INLINE_MEDIA_BYTES = 1_800_000 // 1.8 MB safe JSON envelope fallback
    }
}
