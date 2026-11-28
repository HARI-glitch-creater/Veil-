package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.crypto.LocalIdentity
import com.example.crypto.VeilCryptoEngine
import com.example.data.local.AuthType
import com.example.data.local.BackendProfileEntity
import com.example.data.local.BackendType
import com.example.data.local.CallDirection
import com.example.data.local.CallHistoryEntity
import com.example.data.local.ChannelType
import com.example.data.local.CommunityEntity
import com.example.data.local.CommunityMemberEntity
import com.example.data.local.MediaVaultStorage
import com.example.data.local.MessageContentType
import com.example.data.local.MessageEntity
import com.example.data.local.RoomMemberEntity
import com.example.data.local.RoomRole
import com.example.data.local.SyncStatus
import com.example.data.local.UserProfileEntity
import com.example.data.local.VeilDatabase
import com.example.data.local.VeilRoomEntity
import com.example.data.network.ConnectionTestResult
import com.example.data.network.RemoteCallSignal
import com.example.data.network.RemoteCommunityMemberRecord
import com.example.data.network.RemoteCommunityRecord
import com.example.data.network.RemoteDeviceBinding
import com.example.data.network.RemoteEncryptedMessage
import com.example.data.network.RemoteMemberRecord
import com.example.data.network.ResolvedBackendConfig
import com.example.data.network.VeilBackendClient
import com.example.device.DeviceBindingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class BootRoute {
    CREATE_PROFILE,
    ROOM_GATEWAY,
    HOME,
    REINSTALL_RESTORED
}

data class HostRoomOutcome(
    val room: VeilRoomEntity,
    val warningMessage: String? = null
)

data class BackendProfileDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val backendType: BackendType,
    val baseUrl: String,
    val apiKey: String,
    val supabaseBucket: String = "veil-media",
    val authType: AuthType = AuthType.BEARER,
    val authHeaderName: String = "X-API-Key",
    val customHeaders: Map<String, String> = emptyMap(),
    val roomsPath: String = "/api/v1/rooms",
    val membersPath: String = "/api/v1/members",
    val messagesPath: String = "/api/v1/messages",
    val healthPath: String = "/api/v1/health",
    val webSocketUrl: String = "",
    val mediaUploadUrl: String = "",
    val webhookUrl: String = "",
    val pollIntervalSec: Int = 5
)

data class IncomingMessageAlert(
    val roomId: String,
    val title: String,
    val body: String,
    val targetId: String,
    val isCommunity: Boolean
)

class VeilRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = VeilDatabase.getInstance(appContext)
    private val dao = db.veilDao()
    val crypto = VeilCryptoEngine(appContext)
    val deviceBinding = DeviceBindingManager(appContext)
    val backendClient = VeilBackendClient()
    val mediaStorage = MediaVaultStorage(appContext)

    private val lastSignalPollByRoom = mutableMapOf<String, Long>()

    // Reactive Flows
    fun observeUserProfile(): Flow<UserProfileEntity?> = dao.observeUserProfile()
    fun observeBackendProfiles(): Flow<List<BackendProfileEntity>> = dao.observeBackendProfiles()
    fun observeRooms(): Flow<List<VeilRoomEntity>> = dao.observeRooms()
    fun observeActiveRoom(): Flow<VeilRoomEntity?> = dao.observeActiveRoom()
    fun observeRoomById(roomId: String): Flow<VeilRoomEntity?> = dao.observeRoomById(roomId)
    fun observeRoomMembers(roomId: String): Flow<List<RoomMemberEntity>> = dao.observeRoomMembers(roomId)
    fun observeRoomMember(roomId: String, publicKey: String): Flow<RoomMemberEntity?> =
        dao.observeRoomMember(roomId, publicKey)
    fun observeCommunitiesForRoom(roomId: String): Flow<List<CommunityEntity>> =
        dao.observeCommunitiesForRoom(roomId)
    fun observeCommunityById(communityId: String): Flow<CommunityEntity?> =
        dao.observeCommunityById(communityId)
    fun observeCommunityMembers(communityId: String): Flow<List<CommunityMemberEntity>> =
        dao.observeCommunityMembers(communityId)
    fun observeMessagesForChannel(
        roomId: String,
        channelType: ChannelType,
        targetId: String
    ): Flow<List<MessageEntity>> = dao.observeMessagesForChannel(roomId, channelType, targetId)
    fun observeCallHistory(roomId: String): Flow<List<CallHistoryEntity>> = dao.observeCallHistory(roomId)

    fun getLocalIdentity(): LocalIdentity = crypto.getOrCreateIdentity()
    fun getDeviceBindingId(): String = deviceBinding.computeDeviceBindingId()

    suspend fun getAllRooms(): List<VeilRoomEntity> = dao.getAllRooms()
    suspend fun getRoomById(roomId: String): VeilRoomEntity? = dao.getRoomById(roomId)
    suspend fun getUserProfile(): UserProfileEntity? = dao.getUserProfile()

    /**
     * Evaluates the single-device boot state machine on every launch.
     */
    suspend fun evaluateBootState(): BootRoute = withContext(Dispatchers.IO) {
        val localProfile = dao.getUserProfile()
        val rooms = dao.getAllRooms()
        if (localProfile != null && localProfile.isBound) {
            if (rooms.isNotEmpty() && rooms.none { it.isActiveRoom }) {
                dao.setActiveRoom(rooms.first().id)
            }
            return@withContext if (rooms.isNotEmpty()) BootRoute.HOME else BootRoute.ROOM_GATEWAY
        }

        // Anti-duplicate reinstall recovery: check local AccountManager / MediaStore anchor
        val existingAnchor = deviceBinding.readExistingRecoveryAnchor()
        if (existingAnchor != null) {
            val identity = crypto.updateHandle(existingAnchor.username)
            val restored = UserProfileEntity(
                id = "local_user",
                deviceBindingId = existingAnchor.deviceBindingId,
                publicKey = identity.publicKeyBase64,
                username = existingAnchor.username,
                motto = VeilCryptoEngine.enforceMaxWords(existingAnchor.motto, 20),
                gender = existingAnchor.gender,
                isBound = true,
                updatedAt = System.currentTimeMillis()
            )
            dao.upsertUserProfile(restored)
            return@withContext BootRoute.REINSTALL_RESTORED
        }

        BootRoute.CREATE_PROFILE
    }

    /**
     * Permanently binds a single Veil profile to this physical phone.
     * Blocks duplicate creation if this device already has a recovery anchor or backend binding.
     */
    suspend fun confirmAndBindProfile(
        username: String,
        motto: String,
        gender: String,
        localPfpPath: String
    ): Result<Pair<UserProfileEntity, Boolean>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUser = VeilCryptoEngine.stripEmojis(username).trim()
            require(cleanUser.length in 3..24) { "Username must be between 3 and 24 characters." }
            val cleanMotto = VeilCryptoEngine.enforceMaxWords(motto, 20).trim()
            val cleanGender = gender.ifBlank { "Prefer not to say" }
            val deviceId = deviceBinding.computeDeviceBindingId()

            // Reinstall / anti-duplicate guard
            val existingAnchor = deviceBinding.readExistingRecoveryAnchor()
            if (existingAnchor != null && existingAnchor.deviceBindingId == deviceId) {
                val id = crypto.updateHandle(existingAnchor.username)
                val restored = UserProfileEntity(
                    id = "local_user",
                    deviceBindingId = deviceId,
                    publicKey = id.publicKeyBase64,
                    username = existingAnchor.username,
                    motto = VeilCryptoEngine.enforceMaxWords(existingAnchor.motto, 20),
                    gender = existingAnchor.gender,
                    localPfpPath = localPfpPath,
                    isBound = true,
                    updatedAt = System.currentTimeMillis()
                )
                dao.upsertUserProfile(restored)
                return@runCatching restored to true
            }

            // Also check active backend if already configured
            val activeBackend = dao.getActiveBackendProfile()?.let { resolveBackendConfig(it) }
            if (activeBackend != null) {
                val remoteBinding = backendClient.fetchDeviceBinding(activeBackend, deviceId).getOrNull()
                if (remoteBinding != null && remoteBinding.username.isNotBlank()) {
                    val id = crypto.updateHandle(remoteBinding.username)
                    var resolvedPfpPath = localPfpPath
                    if (resolvedPfpPath.isBlank() && remoteBinding.pfpCipherUrl.isNotBlank()) {
                        resolvedPfpPath = downloadAndDecryptPfp(
                            config = activeBackend,
                            cacheKey = deviceId,
                            pfpCipherUrl = remoteBinding.pfpCipherUrl,
                            legacyRoomKey = null
                        ).orEmpty()
                    }
                    val restored = UserProfileEntity(
                        id = "local_user",
                        deviceBindingId = deviceId,
                        publicKey = id.publicKeyBase64,
                        username = remoteBinding.username,
                        motto = VeilCryptoEngine.enforceMaxWords(remoteBinding.motto, 20),
                        gender = remoteBinding.gender,
                        localPfpPath = resolvedPfpPath,
                        remotePfpCipherUrl = remoteBinding.pfpCipherUrl,
                        isBound = true,
                        updatedAt = System.currentTimeMillis()
                    )
                    dao.upsertUserProfile(restored)
                    deviceBinding.registerLocalAccountBinding(
                        deviceBindingId = deviceId,
                        username = restored.username,
                        publicKey = restored.publicKey,
                        motto = restored.motto,
                        gender = restored.gender
                    )
                    return@runCatching restored to true
                }
            }

            val identity = crypto.updateHandle(cleanUser)
            val uploadedPfpUrl = if (activeBackend != null) {
                uploadEncryptedPfpIfPresent(activeBackend, deviceId, localPfpPath)
            } else ""

            val entity = UserProfileEntity(
                id = "local_user",
                deviceBindingId = deviceId,
                publicKey = identity.publicKeyBase64,
                username = cleanUser,
                motto = cleanMotto,
                gender = cleanGender,
                localPfpPath = localPfpPath,
                remotePfpCipherUrl = uploadedPfpUrl,
                isBound = true,
                updatedAt = System.currentTimeMillis()
            )
            dao.upsertUserProfile(entity)
            deviceBinding.registerLocalAccountBinding(
                deviceBindingId = deviceId,
                username = entity.username,
                publicKey = entity.publicKey,
                motto = entity.motto,
                gender = entity.gender
            )

            if (activeBackend != null) {
                backendClient.upsertDeviceBinding(
                    activeBackend,
                    RemoteDeviceBinding(
                        deviceBindingId = deviceId,
                        publicKey = entity.publicKey,
                        username = entity.username,
                        motto = entity.motto,
                        gender = entity.gender,
                        pfpCipherUrl = uploadedPfpUrl,
                        updatedAt = entity.updatedAt
                    )
                )
            }

            entity to false
        }
    }

    /**
     * Updates the local user profile and syncs Username, Motto, Gender, and encrypted PFP
     * across every joined Veil Room.
     */
    suspend fun updateLocalProfileAndSyncAllRooms(
        username: String,
        motto: String,
        gender: String,
        localPfpPath: String
    ): Result<UserProfileEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUser = VeilCryptoEngine.stripEmojis(username).trim()
            require(cleanUser.length in 3..24) { "Username must be between 3 and 24 characters." }
            val cleanMotto = VeilCryptoEngine.enforceMaxWords(motto, 20).trim()
            val cleanGender = gender.ifBlank { "Prefer not to say" }
            val existing = dao.getUserProfile()
            val deviceId = existing?.deviceBindingId ?: deviceBinding.computeDeviceBindingId()
            val identity = crypto.updateHandle(cleanUser)

            val updated = UserProfileEntity(
                id = "local_user",
                deviceBindingId = deviceId,
                publicKey = identity.publicKeyBase64,
                username = cleanUser,
                motto = cleanMotto,
                gender = cleanGender,
                localPfpPath = localPfpPath,
                remotePfpCipherUrl = existing?.remotePfpCipherUrl.orEmpty(),
                isBound = true,
                updatedAt = System.currentTimeMillis()
            )
            dao.upsertUserProfile(updated)
            deviceBinding.registerLocalAccountBinding(
                deviceBindingId = deviceId,
                username = updated.username,
                publicKey = updated.publicKey,
                motto = updated.motto,
                gender = updated.gender
            )

            val rooms = dao.getAllRooms()
            var latestCipherPfpUrl = updated.remotePfpCipherUrl
            val uploadedUrlByBackendId = mutableMapOf<String, String>()

            for (room in rooms) {
                val config = resolveBackendConfigForRoom(room) ?: continue
                val cipherPfpUrl = uploadedUrlByBackendId.getOrPut(config.profile.id) {
                    uploadEncryptedPfpIfPresent(config, deviceId, localPfpPath)
                        .ifBlank { latestCipherPfpUrl }
                }
                if (cipherPfpUrl.isNotBlank()) {
                    latestCipherPfpUrl = cipherPfpUrl
                }

                backendClient.upsertDeviceBinding(
                    config,
                    RemoteDeviceBinding(
                        deviceBindingId = deviceId,
                        publicKey = updated.publicKey,
                        username = updated.username,
                        motto = updated.motto,
                        gender = updated.gender,
                        pfpCipherUrl = cipherPfpUrl,
                        updatedAt = updated.updatedAt
                    )
                )
                backendClient.upsertMember(
                    config,
                    RemoteMemberRecord(
                        id = deterministicUuid("member:${room.veilCode}:$deviceId"),
                        roomCode = room.veilCode,
                        deviceBindingId = deviceId,
                        username = updated.username,
                        motto = updated.motto,
                        gender = updated.gender,
                        pfpCipherUrl = cipherPfpUrl,
                        publicKey = updated.publicKey,
                        joinedAt = System.currentTimeMillis()
                    )
                )
            }

            if (rooms.isEmpty()) {
                val activeBackend = dao.getActiveBackendProfile()?.let { resolveBackendConfig(it) }
                if (activeBackend != null) {
                    val cipherPfpUrl = uploadEncryptedPfpIfPresent(activeBackend, deviceId, localPfpPath)
                        .ifBlank { latestCipherPfpUrl }
                    if (cipherPfpUrl.isNotBlank()) {
                        latestCipherPfpUrl = cipherPfpUrl
                    }
                    backendClient.upsertDeviceBinding(
                        activeBackend,
                        RemoteDeviceBinding(
                            deviceBindingId = deviceId,
                            publicKey = updated.publicKey,
                            username = updated.username,
                            motto = updated.motto,
                            gender = updated.gender,
                            pfpCipherUrl = cipherPfpUrl,
                            updatedAt = updated.updatedAt
                        )
                    )
                }
            }

            if (latestCipherPfpUrl.isNotBlank() && latestCipherPfpUrl != updated.remotePfpCipherUrl) {
                dao.updateUserRemotePfpUrl(latestCipherPfpUrl)
                updated.copy(remotePfpCipherUrl = latestCipherPfpUrl)
            } else {
                updated
            }
        }
    }

    // Backend Profile Management
    suspend fun saveBackendProfile(draft: BackendProfileDraft, setActive: Boolean = true): Result<BackendProfileEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val validBaseUrl = backendClient.validateHttpUrl(draft.baseUrl).getOrThrow()
                val validWs = backendClient.validateWebSocketUrl(draft.webSocketUrl).getOrThrow()
                val validMedia = if (draft.mediaUploadUrl.isBlank()) "" else {
                    backendClient.validateHttpUrl(draft.mediaUploadUrl).getOrThrow()
                }
                val validWebhook = if (draft.webhookUrl.isBlank()) "" else {
                    backendClient.validateHttpUrl(draft.webhookUrl).getOrThrow()
                }

                val headersJson = JSONObject().apply {
                    draft.customHeaders.forEach { (k, v) ->
                        if (k.isNotBlank() && v.isNotBlank()) put(k.trim(), v.trim())
                    }
                }.toString()

                val entity = BackendProfileEntity(
                    id = draft.id,
                    name = VeilCryptoEngine.stripEmojis(draft.name).trim().ifBlank {
                        if (draft.backendType == BackendType.SUPABASE) "My Supabase" else "Self-Hosted Server"
                    },
                    backendType = draft.backendType,
                    isActive = setActive,
                    baseUrl = validBaseUrl,
                    encryptedApiKey = crypto.protectString(draft.apiKey.trim()),
                    supabaseBucket = draft.supabaseBucket.trim().ifBlank { "veil-media" },
                    authType = draft.authType,
                    authHeaderName = draft.authHeaderName.trim().ifBlank { "X-API-Key" },
                    encryptedCustomHeadersJson = crypto.protectString(headersJson),
                    roomsPath = draft.roomsPath.trim().ifBlank { "/api/v1/rooms" },
                    membersPath = draft.membersPath.trim().ifBlank { "/api/v1/members" },
                    messagesPath = draft.messagesPath.trim().ifBlank { "/api/v1/messages" },
                    healthPath = draft.healthPath.trim().ifBlank { "/api/v1/health" },
                    webSocketUrl = validWs,
                    mediaUploadUrl = validMedia,
                    webhookUrl = validWebhook,
                    pollIntervalSec = draft.pollIntervalSec.coerceIn(2, 60)
                )

                dao.upsertBackendProfile(entity)
                if (setActive) dao.setActiveBackendProfile(entity.id)
                entity
            }
        }

    suspend fun setActiveBackendProfile(profileId: String) = withContext(Dispatchers.IO) {
        dao.setActiveBackendProfile(profileId)
    }

    suspend fun deleteBackendProfile(profileId: String) = withContext(Dispatchers.IO) {
        dao.deleteBackendProfile(profileId)
    }

    suspend fun testBackendDraft(draft: BackendProfileDraft): ConnectionTestResult {
        val tempProfile = BackendProfileEntity(
            id = draft.id,
            name = draft.name,
            backendType = draft.backendType,
            baseUrl = draft.baseUrl,
            supabaseBucket = draft.supabaseBucket,
            authType = draft.authType,
            authHeaderName = draft.authHeaderName,
            roomsPath = draft.roomsPath,
            membersPath = draft.membersPath,
            messagesPath = draft.messagesPath,
            healthPath = draft.healthPath,
            webSocketUrl = draft.webSocketUrl,
            mediaUploadUrl = draft.mediaUploadUrl,
            webhookUrl = draft.webhookUrl,
            pollIntervalSec = draft.pollIntervalSec
        )
        return backendClient.testConnection(ResolvedBackendConfig(tempProfile, draft.apiKey, draft.customHeaders))
    }

    suspend fun testSavedBackendProfile(profile: BackendProfileEntity): ConnectionTestResult =
        backendClient.testConnection(resolveBackendConfig(profile))

    fun getDecryptedApiKey(profile: BackendProfileEntity): String =
        crypto.unprotectString(profile.encryptedApiKey)

    fun getDecryptedCustomHeaders(profile: BackendProfileEntity): Map<String, String> {
        val raw = crypto.unprotectString(profile.encryptedCustomHeadersJson)
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, obj.optString(key))
                }
            }
        }.getOrDefault(emptyMap())
    }

    // Host / Join / Switch Veil Room
    suspend fun hostRoom(
        roomName: String,
        backendProfileId: String
    ): Result<HostRoomOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanRoomName = VeilCryptoEngine.stripEmojis(roomName).trim()
            require(cleanRoomName.length >= 2) { "Room Name is required (at least 2 characters)." }

            val backend = dao.getBackendProfileById(backendProfileId)
                ?: dao.getActiveBackendProfile()
                ?: error("Configure a Supabase or Self-Hosted backend first.")
            val config = resolveBackendConfig(backend)
            val profile = ensureLocalProfile()
            val veilCode = crypto.generateVeilCode()
            val roomId = UUID.randomUUID().toString()

            val warnings = mutableListOf<String>()
            val cipherPfpUrl = uploadEncryptedPfpIfPresent(config, profile.deviceBindingId, profile.localPfpPath)
                .ifBlank { profile.remotePfpCipherUrl }
            if (cipherPfpUrl.isNotBlank() && cipherPfpUrl != profile.remotePfpCipherUrl) {
                dao.updateUserRemotePfpUrl(cipherPfpUrl)
            }

            backendClient.upsertDeviceBinding(
                config,
                RemoteDeviceBinding(
                    deviceBindingId = profile.deviceBindingId,
                    publicKey = profile.publicKey,
                    username = profile.username,
                    motto = profile.motto,
                    gender = profile.gender,
                    pfpCipherUrl = cipherPfpUrl,
                    updatedAt = System.currentTimeMillis()
                )
            )

            val createRes = backendClient.createRoom(
                config = config,
                roomCode = veilCode,
                roomName = cleanRoomName,
                hostPublicKey = profile.publicKey,
                hostDeviceId = profile.deviceBindingId
            )
            if (createRes.isFailure) {
                warnings.add("Room saved locally; sync pending (${createRes.exceptionOrNull()?.message})")
            }

            backendClient.upsertMember(
                config,
                RemoteMemberRecord(
                    id = deterministicUuid("member:$veilCode:${profile.deviceBindingId}"),
                    roomCode = veilCode,
                    deviceBindingId = profile.deviceBindingId,
                    username = profile.username,
                    motto = profile.motto,
                    gender = profile.gender,
                    pfpCipherUrl = cipherPfpUrl,
                    publicKey = profile.publicKey,
                    joinedAt = System.currentTimeMillis()
                )
            )

            val room = VeilRoomEntity(
                id = roomId,
                veilCode = veilCode,
                roomName = cleanRoomName,
                role = RoomRole.HOST,
                backendProfileId = backend.id,
                isActiveRoom = true,
                hostPublicKey = profile.publicKey,
                hostDeviceId = profile.deviceBindingId,
                peerHandle = "",
                peerPublicKey = "",
                encryptedRoomKey = "",
                safetyNumber = "",
                isVerified = false,
                lastMessagePreview = "Room ready. Share the 16-character Veil Code to invite profiles."
            )
            dao.upsertRoom(room)
            dao.setActiveRoom(room.id)

            // Automatically create Default Room Community (<Room Name> — General)
            ensureDefaultRoomCommunity(room, config, profile)

            HostRoomOutcome(
                room = room,
                warningMessage = warnings.firstOrNull()
            )
        }
    }

    suspend fun joinRoom(
        veilCodeRaw: String,
        roomNameHint: String,
        backendProfileId: String,
        hostPublicKey: String = "",
        hostHandle: String = "",
        roomKeyBase64: String = ""
    ): Result<VeilRoomEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanCode = crypto.normalizeVeilCode(veilCodeRaw)
            require(cleanCode.length == VeilCryptoEngine.VEIL_CODE_LENGTH) {
                "Veil Code must be exactly 16 characters."
            }

            val backend = dao.getBackendProfileById(backendProfileId)
                ?: dao.getActiveBackendProfile()
                ?: error("Configure a Supabase or Self-Hosted backend first.")
            val config = resolveBackendConfig(backend)
            var profile = ensureLocalProfile()

            // Check remote device binding for reinstall recovery consistency
            val remoteBinding = backendClient.fetchDeviceBinding(config, profile.deviceBindingId).getOrNull()
            if (remoteBinding != null && remoteBinding.username.isNotBlank() && remoteBinding.username != profile.username) {
                profile = profile.copy(
                    username = remoteBinding.username,
                    motto = VeilCryptoEngine.enforceMaxWords(remoteBinding.motto, 20),
                    gender = remoteBinding.gender,
                    remotePfpCipherUrl = remoteBinding.pfpCipherUrl
                )
                dao.upsertUserProfile(profile)
            }

            val remoteRoom = backendClient.fetchRoom(config, cleanCode).getOrNull()
            val resolvedRoomName = VeilCryptoEngine.stripEmojis(
                remoteRoom?.roomName?.ifBlank { roomNameHint } ?: roomNameHint
            ).trim().ifBlank { "Veil Room" }
            val resolvedHostKey = remoteRoom?.hostPublicKey?.ifBlank { hostPublicKey } ?: hostPublicKey
            val resolvedHostDeviceId = remoteRoom?.hostDeviceId.orEmpty()

            val existing = dao.getRoomByCode(cleanCode)
            val roomId = existing?.id ?: UUID.randomUUID().toString()
            val effectiveRoomKey = roomKeyBase64.ifBlank {
                existing?.let { crypto.unprotectString(it.encryptedRoomKey) }.orEmpty()
            }
            val isCreatorSelf = resolvedHostKey.isNotBlank() &&
                resolvedHostKey == profile.publicKey &&
                (resolvedHostDeviceId.isBlank() || resolvedHostDeviceId == profile.deviceBindingId)

            val room = VeilRoomEntity(
                id = roomId,
                veilCode = cleanCode,
                roomName = resolvedRoomName,
                role = if (isCreatorSelf) RoomRole.HOST else (existing?.role ?: RoomRole.GUEST),
                backendProfileId = backend.id,
                isActiveRoom = true,
                hostPublicKey = resolvedHostKey.ifBlank { existing?.hostPublicKey.orEmpty() },
                hostDeviceId = resolvedHostDeviceId.ifBlank { existing?.hostDeviceId.orEmpty() },
                peerHandle = hostHandle.ifBlank { existing?.peerHandle.orEmpty() },
                peerPublicKey = resolvedHostKey,
                encryptedRoomKey = if (effectiveRoomKey.isNotBlank()) crypto.protectString(effectiveRoomKey) else "",
                safetyNumber = if (resolvedHostKey.isNotBlank() && crypto.isValidPublicKey(resolvedHostKey)) {
                    crypto.computeSafetyNumber(profile.publicKey, resolvedHostKey)
                } else "",
                isVerified = existing?.isVerified ?: false,
                lastMessagePreview = existing?.lastMessagePreview ?: "Joined encrypted room",
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            dao.upsertRoom(room)
            dao.setActiveRoom(room.id)

            val cipherPfpUrl = uploadEncryptedPfpIfPresent(config, profile.deviceBindingId, profile.localPfpPath)
                .ifBlank { profile.remotePfpCipherUrl }
            if (cipherPfpUrl.isNotBlank() && cipherPfpUrl != profile.remotePfpCipherUrl) {
                dao.updateUserRemotePfpUrl(cipherPfpUrl)
            }

            backendClient.upsertDeviceBinding(
                config,
                RemoteDeviceBinding(
                    deviceBindingId = profile.deviceBindingId,
                    publicKey = profile.publicKey,
                    username = profile.username,
                    motto = profile.motto,
                    gender = profile.gender,
                    pfpCipherUrl = cipherPfpUrl,
                    updatedAt = System.currentTimeMillis()
                )
            )
            backendClient.upsertMember(
                config,
                RemoteMemberRecord(
                    id = deterministicUuid("member:$cleanCode:${profile.deviceBindingId}"),
                    roomCode = cleanCode,
                    deviceBindingId = profile.deviceBindingId,
                    username = profile.username,
                    motto = profile.motto,
                    gender = profile.gender,
                    pfpCipherUrl = cipherPfpUrl,
                    publicKey = profile.publicKey,
                    joinedAt = System.currentTimeMillis()
                )
            )

            ensureDefaultRoomCommunity(room, config, profile)
            syncRoom(room.id)
            dao.getRoomById(room.id) ?: room
        }
    }

    fun isUserRoomCreator(room: VeilRoomEntity?, profile: UserProfileEntity?): Boolean {
        if (room == null || profile == null) return false
        val keyMatches = room.hostPublicKey.isNotBlank() && room.hostPublicKey == profile.publicKey
        val deviceMatches = room.hostDeviceId.isBlank() || room.hostDeviceId == profile.deviceBindingId
        return keyMatches && deviceMatches
    }

    suspend fun removeMemberFromRoom(
        roomId: String,
        targetPublicKey: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val room = dao.getRoomById(roomId) ?: error("Room not found.")
            val me = ensureLocalProfile()
            require(isUserRoomCreator(room, me)) {
                "Only the room creator can remove profiles from this room."
            }
            require(targetPublicKey.isNotBlank() && targetPublicKey != me.publicKey) {
                "Cannot remove your own profile."
            }

            val communities = dao.getCommunitiesForRoom(roomId)
            val config = resolveBackendConfigForRoom(room)
            if (config != null) {
                backendClient.removeMemberFromRoom(
                    config = config,
                    roomCode = room.veilCode,
                    targetPublicKey = targetPublicKey,
                    roomCommunityIds = communities.map { it.id }
                ).getOrThrow()
            }

            // Purge from local room directory and community rosters immediately.
            dao.deleteRoomMember(roomId, targetPublicKey)
            for (comm in communities) {
                dao.deleteCommunityMemberByKey(comm.id, targetPublicKey)
                val updatedCount = if (comm.isDefault) {
                    dao.getRoomMembers(roomId).size + 1
                } else {
                    maxOf(1, dao.getCommunityMembers(comm.id).size)
                }
                dao.updateCommunityMemberCount(comm.id, updatedCount)
            }
        }
    }

    suspend fun setActiveRoom(roomId: String) = withContext(Dispatchers.IO) {
        dao.setActiveRoom(roomId)
    }

    suspend fun deleteRoom(roomId: String) = withContext(Dispatchers.IO) {
        dao.deleteMessagesForRoom(roomId)
        dao.deleteMembersForRoom(roomId)
        dao.deleteCommunitiesForRoom(roomId)
        dao.deleteCallHistoryForRoom(roomId)
        dao.deleteRoom(roomId)
        val remaining = dao.getAllRooms()
        if (remaining.isNotEmpty() && remaining.none { it.isActiveRoom }) {
            dao.setActiveRoom(remaining.first().id)
        }
    }

    // Moderation & Verification
    suspend fun setMemberMuted(roomId: String, peerPublicKey: String, muted: Boolean) = withContext(Dispatchers.IO) {
        dao.setMemberMuted(roomId, peerPublicKey, muted)
    }

    suspend fun setMemberBlocked(roomId: String, peerPublicKey: String, blocked: Boolean) = withContext(Dispatchers.IO) {
        dao.setMemberBlocked(roomId, peerPublicKey, blocked)
    }

    suspend fun setMemberVerified(roomId: String, peerPublicKey: String, verified: Boolean) = withContext(Dispatchers.IO) {
        dao.setMemberVerified(roomId, peerPublicKey, verified)
    }

    suspend fun clearDirectUnread(roomId: String, peerPublicKey: String) = withContext(Dispatchers.IO) {
        dao.clearMemberUnread(roomId, peerPublicKey)
    }

    suspend fun clearCommunityUnread(communityId: String) = withContext(Dispatchers.IO) {
        dao.clearCommunityUnread(communityId)
    }

    suspend fun reportMember(
        roomId: String,
        reportedPublicKey: String,
        reason: String,
        notes: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val room = dao.getRoomById(roomId) ?: error("Room not found")
            val config = resolveBackendConfigForRoom(room) ?: error("Backend not configured")
            val me = ensureLocalProfile()
            backendClient.submitReport(
                config = config,
                id = UUID.randomUUID().toString(),
                roomCode = room.veilCode,
                reporterPublicKey = me.publicKey,
                reportedPublicKey = reportedPublicKey,
                reason = VeilCryptoEngine.stripEmojis(reason).trim(),
                notes = VeilCryptoEngine.stripEmojis(notes).trim().take(400)
            ).getOrThrow()
        }
    }

    // Communities (Default + Private 3+ Profiles with Strict Isolation)
    suspend fun createPrivateCommunity(
        roomId: String,
        name: String,
        description: String,
        selectedPeerPublicKeys: List<String>
    ): Result<CommunityEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val distinctPeers = selectedPeerPublicKeys.distinct().filter { it.isNotBlank() }
            require(distinctPeers.size >= 2) {
                "Private communities require at least 3 profiles total (you plus at least 2 room peers)."
            }
            val cleanName = VeilCryptoEngine.stripEmojis(name).trim()
            require(cleanName.length >= 2) { "Community name is required." }
            val cleanDesc = VeilCryptoEngine.stripEmojis(description).trim().take(160)

            val room = dao.getRoomById(roomId) ?: error("Active room not found")
            val config = resolveBackendConfigForRoom(room)
            val me = ensureLocalProfile()

            val communityId = UUID.randomUUID().toString()
            val communityKeyB64 = crypto.generateRandomKeyBase64()
            val now = System.currentTimeMillis()

            if (config != null) {
                backendClient.createCommunity(
                    config,
                    RemoteCommunityRecord(
                        id = communityId,
                        roomCode = room.veilCode,
                        name = cleanName,
                        description = cleanDesc,
                        isDefault = false,
                        createdByPublicKey = me.publicKey,
                        createdAt = now
                    )
                )
                // Self entry
                backendClient.upsertCommunityMember(
                    config,
                    RemoteCommunityMemberRecord(
                        communityId = communityId,
                        memberPublicKey = me.publicKey,
                        encryptedCommunityKey = crypto.wrapCommunityKeyForPeer(communityKeyB64, me.publicKey, communityId)
                    )
                )
            }

            // Wrap community AES key individually via ECDH for every selected member
            val roomMembers = dao.getRoomMembers(roomId).associateBy { it.publicKey }
            val localMembers = mutableListOf(
                CommunityMemberEntity(
                    id = "${communityId}_${me.publicKey.take(16)}",
                    communityId = communityId,
                    memberPublicKey = me.publicKey,
                    memberUsername = me.username,
                    encryptedCommunityKey = crypto.protectString(communityKeyB64)
                )
            )

            for (peerPub in distinctPeers) {
                if (!crypto.isValidPublicKey(peerPub)) continue
                val wrapped = crypto.wrapCommunityKeyForPeer(communityKeyB64, peerPub, communityId)
                if (config != null) {
                    backendClient.upsertCommunityMember(
                        config,
                        RemoteCommunityMemberRecord(
                            communityId = communityId,
                            memberPublicKey = peerPub,
                            encryptedCommunityKey = wrapped
                        )
                    )
                }
                localMembers.add(
                    CommunityMemberEntity(
                        id = "${communityId}_${peerPub.take(16)}",
                        communityId = communityId,
                        memberPublicKey = peerPub,
                        memberUsername = roomMembers[peerPub]?.username ?: "Peer",
                        encryptedCommunityKey = wrapped
                    )
                )
            }

            val community = CommunityEntity(
                id = communityId,
                roomId = roomId,
                roomCode = room.veilCode,
                name = cleanName,
                description = cleanDesc,
                isDefault = false,
                createdByPublicKey = me.publicKey,
                encryptedCommunityKey = crypto.protectString(communityKeyB64),
                memberCount = localMembers.size,
                lastMessagePreview = "Private community created (${localMembers.size} profiles)",
                lastMessageTimestamp = now,
                unreadCount = 0,
                createdAt = now
            )
            dao.upsertCommunity(community)
            localMembers.forEach { dao.upsertCommunityMember(it) }
            community
        }
    }

    private fun deterministicUuid(seed: String): String =
        UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()

    private suspend fun ensureDefaultRoomCommunity(
        room: VeilRoomEntity,
        config: ResolvedBackendConfig?,
        me: UserProfileEntity
    ) {
        val existingDefault = dao.getCommunitiesForRoom(room.id).firstOrNull { it.isDefault }
        val defaultId = existingDefault?.id ?: deterministicUuid("default-community:${room.veilCode}")
        val existing = existingDefault ?: dao.getCommunityById(defaultId)
        val defaultKeyB64 = existing?.encryptedCommunityKey?.let { crypto.unprotectString(it) }
            ?.ifBlank { null }
            ?: Base64.encodeToString(deriveRoomKey(room), Base64.NO_WRAP)

        if (existing == null) {
            val defaultName = "${room.roomName} — General"
            val comm = CommunityEntity(
                id = defaultId,
                roomId = room.id,
                roomCode = room.veilCode,
                name = defaultName,
                description = "Default room community for all profiles in ${room.roomName}",
                isDefault = true,
                createdByPublicKey = room.peerPublicKey.ifBlank { me.publicKey },
                encryptedCommunityKey = crypto.protectString(defaultKeyB64),
                memberCount = maxOf(1, dao.getRoomMembers(room.id).size + 1),
                lastMessagePreview = "Encrypted room community ready",
                lastMessageTimestamp = room.createdAt,
                createdAt = room.createdAt
            )
            dao.upsertCommunity(comm)
            if (config != null && room.role == RoomRole.HOST) {
                backendClient.createCommunity(
                    config,
                    RemoteCommunityRecord(
                        id = defaultId,
                        roomCode = room.veilCode,
                        name = defaultName,
                        description = comm.description,
                        isDefault = true,
                        createdByPublicKey = me.publicKey,
                        createdAt = room.createdAt
                    )
                )
                backendClient.upsertCommunityMember(
                    config,
                    RemoteCommunityMemberRecord(
                        communityId = defaultId,
                        memberPublicKey = me.publicKey,
                        encryptedCommunityKey = crypto.wrapCommunityKeyForPeer(defaultKeyB64, me.publicKey, defaultId)
                    )
                )
            }
        }
    }

    // Messaging (Direct + Community + 1x / 2x Disappearing Views)
    suspend fun sendTextMessage(
        roomId: String,
        channelType: ChannelType,
        targetId: String,
        text: String,
        maxViews: Int = 0
    ): Result<MessageEntity> = withContext(Dispatchers.IO) {
        val cleanText = VeilCryptoEngine.stripEmojis(text).trim()
        if (cleanText.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Message cannot be empty"))
        val envelopeJson = JSONObject().apply {
            put("type", MessageContentType.TEXT.name)
            put("text", cleanText)
            put("max_views", maxViews.coerceIn(0, 2))
        }
        sendEncryptedEnvelope(
            roomId = roomId,
            channelType = channelType,
            targetId = targetId,
            contentType = MessageContentType.TEXT,
            envelopeJson = envelopeJson,
            previewText = if (maxViews > 0) disappearingLabel(MessageContentType.TEXT, maxViews) else cleanText,
            decryptedText = cleanText,
            maxViews = maxViews.coerceIn(0, 2)
        )
    }

    suspend fun sendMediaFromUri(
        roomId: String,
        channelType: ChannelType,
        targetId: String,
        uri: Uri,
        preferredType: MessageContentType,
        maxViews: Int = 0
    ): Result<MessageEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val stored = mediaStorage.copyFromUri(uri, preferredType.name.lowercase())
                ?: error("Unable to read selected file.")
            val resolvedType = when {
                stored.mimeType.startsWith("image/") -> MessageContentType.IMAGE
                stored.mimeType.startsWith("video/") -> MessageContentType.VIDEO
                stored.mimeType.startsWith("audio/") -> MessageContentType.VOICE
                else -> preferredType
            }
            sendStoredMediaFile(
                roomId = roomId,
                channelType = channelType,
                targetId = targetId,
                file = stored.file,
                displayName = stored.displayName,
                mimeType = stored.mimeType,
                sizeBytes = stored.sizeBytes,
                contentType = resolvedType,
                maxViews = maxViews.coerceIn(0, 2)
            ).getOrThrow()
        }
    }

    suspend fun sendCapturedCameraPhoto(
        roomId: String,
        channelType: ChannelType,
        targetId: String,
        capturedFile: File,
        maxViews: Int = 0
    ): Result<MessageEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val stored = mediaStorage.copyLocalFileToVault(
                capturedFile,
                "photo_${System.currentTimeMillis()}.jpg",
                "image/jpeg"
            ) ?: error("Unable to save captured photo")
            capturedFile.delete()
            sendStoredMediaFile(
                roomId = roomId,
                channelType = channelType,
                targetId = targetId,
                file = stored.file,
                displayName = stored.displayName,
                mimeType = stored.mimeType,
                sizeBytes = stored.sizeBytes,
                contentType = MessageContentType.IMAGE,
                maxViews = maxViews.coerceIn(0, 2)
            ).getOrThrow()
        }
    }

    suspend fun sendVoiceNote(
        roomId: String,
        channelType: ChannelType,
        targetId: String,
        voiceFile: File,
        durationMs: Long,
        peaks: List<Int>,
        maxViews: Int = 0
    ): Result<MessageEntity> = withContext(Dispatchers.IO) {
        sendStoredMediaFile(
            roomId = roomId,
            channelType = channelType,
            targetId = targetId,
            file = voiceFile,
            displayName = voiceFile.name,
            mimeType = "audio/mp4",
            sizeBytes = voiceFile.length(),
            contentType = MessageContentType.VOICE,
            audioDurationMs = durationMs,
            waveformPeaks = peaks.joinToString(","),
            maxViews = maxViews.coerceIn(0, 2)
        )
    }

    suspend fun sendLocation(
        roomId: String,
        channelType: ChannelType,
        targetId: String,
        latitude: Double,
        longitude: Double,
        label: String,
        maxViews: Int = 0
    ): Result<MessageEntity> = withContext(Dispatchers.IO) {
        val cleanLabel = VeilCryptoEngine.stripEmojis(label).trim().ifBlank { "Shared location" }
        val envelopeJson = JSONObject().apply {
            put("type", MessageContentType.LOCATION.name)
            put("lat", latitude)
            put("lng", longitude)
            put("label", cleanLabel)
            put("max_views", maxViews.coerceIn(0, 2))
        }
        sendEncryptedEnvelope(
            roomId = roomId,
            channelType = channelType,
            targetId = targetId,
            contentType = MessageContentType.LOCATION,
            envelopeJson = envelopeJson,
            previewText = "Location: $cleanLabel",
            latitude = latitude,
            longitude = longitude,
            locationLabel = cleanLabel,
            maxViews = maxViews.coerceIn(0, 2)
        )
    }

    private suspend fun sendStoredMediaFile(
        roomId: String,
        channelType: ChannelType,
        targetId: String,
        file: File,
        displayName: String,
        mimeType: String,
        sizeBytes: Long,
        contentType: MessageContentType,
        audioDurationMs: Long = 0L,
        waveformPeaks: String = "",
        maxViews: Int = 0
    ): Result<MessageEntity> {
        val room = dao.getRoomById(roomId) ?: return Result.failure(IllegalStateException("Room not found"))
        val messageId = UUID.randomUUID().toString()
        val rawBytes = file.readBytes()

        // Generate a dedicated per-attachment AES-256-GCM key & 96-bit IV embedded inside the E2EE JSON envelope
        val (mediaKeyB64, mediaIvB64) = crypto.generateMediaKeyAndIv()
        val encryptedBytes = crypto.encryptBytesWithKeyAndIv(mediaKeyB64, mediaIvB64, rawBytes)

        val config = resolveBackendConfigForRoom(room)
        var remoteUrl = ""
        var inlineBase64 = ""

        if (config != null) {
            val uploadRes = backendClient.uploadEncryptedBlob(config, room.veilCode, messageId, encryptedBytes)
            if (uploadRes.isSuccess) {
                remoteUrl = uploadRes.getOrThrow()
            } else if (encryptedBytes.size <= MAX_INLINE_CIPHER_BYTES) {
                inlineBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            }
        } else if (encryptedBytes.size <= MAX_INLINE_CIPHER_BYTES) {
            inlineBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        }

        val envelopeJson = JSONObject().apply {
            put("type", contentType.name)
            put("fileName", displayName)
            put("mimeType", mimeType)
            put("sizeBytes", sizeBytes)
            put("audioDurationMs", audioDurationMs)
            put("waveformPeaks", waveformPeaks)
            put("max_views", maxViews)
            put("mediaKey", mediaKeyB64)
            put("mediaIv", mediaIvB64)
            if (remoteUrl.isNotBlank()) {
                put("storageUrl", remoteUrl)
                put("mediaUrl", remoteUrl)
            }
            if (inlineBase64.isNotBlank()) put("inlineBlob", inlineBase64)
        }

        val preview = if (maxViews > 0) {
            disappearingLabel(contentType, maxViews)
        } else {
            when (contentType) {
                MessageContentType.IMAGE -> "Photo"
                MessageContentType.VIDEO -> "Video"
                MessageContentType.VOICE -> "Voice note"
                MessageContentType.DOCUMENT -> "File: $displayName"
                else -> displayName
            }
        }

        return sendEncryptedEnvelope(
            roomId = roomId,
            channelType = channelType,
            targetId = targetId,
            contentType = contentType,
            envelopeJson = envelopeJson,
            previewText = preview,
            localMediaPath = file.absolutePath,
            remoteMediaUrl = remoteUrl,
            mediaKey = mediaKeyB64,
            mediaIv = mediaIvB64,
            fileName = displayName,
            mimeType = mimeType,
            fileSizeBytes = sizeBytes,
            audioDurationMs = audioDurationMs,
            waveformPeaks = waveformPeaks,
            maxViews = maxViews,
            messageIdOverride = messageId
        )
    }

    private suspend fun sendEncryptedEnvelope(
        roomId: String,
        channelType: ChannelType,
        targetId: String,
        contentType: MessageContentType,
        envelopeJson: JSONObject,
        previewText: String,
        decryptedText: String = "",
        localMediaPath: String = "",
        remoteMediaUrl: String = "",
        mediaKey: String = "",
        mediaIv: String = "",
        fileName: String = "",
        mimeType: String = "",
        fileSizeBytes: Long = 0L,
        audioDurationMs: Long = 0L,
        waveformPeaks: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
        locationLabel: String = "",
        maxViews: Int = 0,
        messageIdOverride: String? = null
    ): Result<MessageEntity> = runCatching {
        val room = dao.getRoomById(roomId) ?: error("Room not found")
        val me = ensureLocalProfile()
        val keyBytes = deriveChannelKey(room, channelType, targetId)
        val encrypted = crypto.encryptText(keyBytes, envelopeJson.toString())
        val messageId = messageIdOverride ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val entity = MessageEntity(
            id = messageId,
            roomId = roomId,
            channelType = channelType,
            targetId = targetId,
            isOutgoing = true,
            senderHandle = me.username,
            senderPublicKey = me.publicKey,
            contentType = contentType,
            ciphertextIv = encrypted.ivBase64,
            ciphertextPayload = encrypted.ciphertextBase64,
            decryptedText = decryptedText,
            localMediaPath = localMediaPath,
            remoteMediaUrl = remoteMediaUrl,
            mediaKey = mediaKey,
            mediaIv = mediaIv,
            fileName = fileName,
            mimeType = mimeType,
            fileSizeBytes = fileSizeBytes,
            audioDurationMs = audioDurationMs,
            waveformPeaks = waveformPeaks,
            latitude = latitude,
            longitude = longitude,
            locationLabel = locationLabel,
            maxViews = maxViews,
            viewsConsumed = 0,
            isDisappeared = false,
            timestamp = now,
            syncStatus = SyncStatus.PENDING
        )
        dao.upsertMessage(entity)
        dao.updateRoomLastMessage(roomId, previewText, now)
        if (channelType == ChannelType.DIRECT) {
            dao.updateMemberLastMessage(roomId, targetId, previewText, now, 0)
        } else {
            dao.updateCommunityLastMessage(targetId, previewText, now, 0)
        }

        val config = resolveBackendConfigForRoom(room)
        if (config != null) {
            val remote = RemoteEncryptedMessage(
                id = messageId,
                roomCode = room.veilCode,
                channelType = channelType,
                targetId = targetId,
                senderPublicKey = me.publicKey,
                iv = encrypted.ivBase64,
                ciphertext = encrypted.ciphertextBase64,
                maxViews = maxViews,
                viewsConsumed = 0,
                createdAt = now
            )
            val sendRes = backendClient.sendEncryptedMessage(config, remote)
            val finalStatus = if (sendRes.isSuccess) SyncStatus.SENT else SyncStatus.QUEUED_OFFLINE
            dao.updateMessageSync(messageId, finalStatus, remoteMediaUrl)
            entity.copy(syncStatus = finalStatus)
        } else {
            dao.updateMessageSync(messageId, SyncStatus.QUEUED_OFFLINE, remoteMediaUrl)
            entity.copy(syncStatus = SyncStatus.QUEUED_OFFLINE)
        }
    }

    /**
     * On-demand encrypted media downloader & decryptor for IMAGE, DOCUMENT, VOICE, and VIDEO messages.
     * Downloads ciphertext from Supabase Storage (veil-media), decrypts with the message's mediaKey & mediaIv
     * (or legacy channel key fallback) into context.cacheDir/veil_media/<messageId>.<ext>, and updates Room.
     */
    suspend fun ensureMediaDownloaded(messageId: String): Result<MessageEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val msg = dao.getMessageById(messageId) ?: error("Message not found.")
            if (msg.isDisappeared || (msg.maxViews > 0 && msg.viewsConsumed >= msg.maxViews)) {
                error("This disappearing media has already expired.")
            }

            val existingLocal = msg.localMediaPath.takeIf { it.isNotBlank() }?.let { File(it) }
            if (existingLocal != null && existingLocal.exists() && existingLocal.length() > 0L) {
                return@runCatching msg
            }

            val room = dao.getRoomById(msg.roomId) ?: error("Room not found.")
            val config = resolveBackendConfigForRoom(room)
            val channelKey = deriveChannelKey(room, msg.channelType, msg.targetId)

            var effectiveUrl = msg.remoteMediaUrl
            var effectiveKey = msg.mediaKey
            var effectiveIv = msg.mediaIv
            var inlineBlob = ""

            // Recover storageUrl / mediaKey / mediaIv / inlineBlob from E2EE payload if missing on entity
            if ((effectiveUrl.isBlank() || effectiveKey.isBlank() || effectiveIv.isBlank()) &&
                msg.ciphertextPayload.isNotBlank() && msg.ciphertextIv.isNotBlank()
            ) {
                val envJson = decryptMessageEnvelope(room, msg.channelType, msg.targetId, msg.ciphertextIv, msg.ciphertextPayload)
                if (envJson != null) {
                    if (effectiveUrl.isBlank()) {
                        effectiveUrl = envJson.optString("storageUrl").orEmpty()
                            .ifBlank { envJson.optString("mediaUrl").orEmpty() }
                    }
                    if (effectiveKey.isBlank()) effectiveKey = envJson.optString("mediaKey").orEmpty()
                    if (effectiveIv.isBlank()) effectiveIv = envJson.optString("mediaIv").orEmpty()
                    inlineBlob = envJson.optString("inlineBlob").orEmpty()
                }
            }

            val cipherBytes: ByteArray = when {
                effectiveUrl.isNotBlank() && config != null -> {
                    backendClient.downloadEncryptedBlob(config, effectiveUrl).getOrThrow()
                }
                inlineBlob.isNotBlank() -> {
                    Base64.decode(inlineBlob, Base64.DEFAULT)
                }
                else -> error("Remote encrypted media URL is missing.")
            }

            val plainBytes = decryptAttachmentBytes(
                cipherBytes = cipherBytes,
                mediaKey = effectiveKey,
                mediaIv = effectiveIv,
                channelKey = channelKey,
                room = room
            )

            val cachedFile = mediaStorage.saveDecryptedMediaToCache(
                messageId = msg.id,
                bytes = plainBytes,
                displayName = msg.fileName.ifBlank { defaultMediaNameForType(msg.contentType) },
                mimeType = msg.mimeType.ifBlank { defaultMimeForType(msg.contentType) }
            )

            dao.updateMessageMediaCache(
                messageId = msg.id,
                localMediaPath = cachedFile.absolutePath,
                remoteMediaUrl = effectiveUrl,
                mediaKey = effectiveKey,
                mediaIv = effectiveIv
            )

            msg.copy(
                localMediaPath = cachedFile.absolutePath,
                remoteMediaUrl = effectiveUrl,
                mediaKey = effectiveKey,
                mediaIv = effectiveIv
            )
        }
    }

    /**
     * Consumes one view of a 1x or 2x disappearing message.
     * Once views_consumed >= max_views, securely wipes & deletes local decrypted media/plaintext
     * and purges the remote ciphertext row permanently.
     */
    suspend fun consumeEphemeralView(messageId: String) = withContext(Dispatchers.IO) {
        val msg = dao.getMessageById(messageId) ?: return@withContext
        if (msg.maxViews <= 0 || msg.isDisappeared) return@withContext

        val nextViews = msg.viewsConsumed + 1
        val expired = nextViews >= msg.maxViews

        if (expired && msg.localMediaPath.isNotBlank()) {
            mediaStorage.secureWipeAndDelete(msg.localMediaPath)
        }

        dao.updateMessageEphemeralState(
            messageId = messageId,
            viewsConsumed = nextViews,
            isDisappeared = expired,
            decryptedText = if (expired) "" else msg.decryptedText,
            localMediaPath = if (expired) "" else msg.localMediaPath,
            remoteMediaUrl = if (expired) "" else msg.remoteMediaUrl,
            mediaKey = if (expired) "" else msg.mediaKey,
            mediaIv = if (expired) "" else msg.mediaIv,
            ciphertextPayload = if (expired) "" else msg.ciphertextPayload
        )

        val room = dao.getRoomById(msg.roomId)
        val config = room?.let { resolveBackendConfigForRoom(it) }
        if (config != null) {
            backendClient.updateMessageViewsOrPurge(
                config = config,
                messageId = messageId,
                viewsConsumed = nextViews,
                purgeCompletely = expired
            )
        }
    }

    // Room Sync (Members, Communities with Strict Isolation, Messages, Voice Call Signals)
    suspend fun syncRoom(
        roomId: String,
        onNewMessageAlert: ((IncomingMessageAlert) -> Unit)? = null,
        onIncomingCallSignal: ((VeilRoomEntity, RemoteCallSignal, RoomMemberEntity?) -> Unit)? = null,
        onRemovedFromRoom: ((String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var room = dao.getRoomById(roomId) ?: return@runCatching 0
            val config = resolveBackendConfigForRoom(room) ?: return@runCatching 0
            val me = ensureLocalProfile()

            // Refresh stealth room creator metadata if not yet cached locally.
            if (room.hostPublicKey.isBlank() || room.hostDeviceId.isBlank()) {
                val remoteRoom = backendClient.fetchRoom(config, room.veilCode).getOrNull()
                if (remoteRoom != null && remoteRoom.hostPublicKey.isNotBlank()) {
                    dao.updateRoomHostInfo(room.id, remoteRoom.hostPublicKey, remoteRoom.hostDeviceId)
                    room = room.copy(
                        hostPublicKey = remoteRoom.hostPublicKey,
                        hostDeviceId = remoteRoom.hostDeviceId
                    )
                }
            }

            val roomKeyBytes = deriveRoomKey(room)

            // 1. Flush queued offline outgoing messages
            val unsynced = dao.getUnsyncedMessages(roomId)
            for (pending in unsynced) {
                val res = backendClient.sendEncryptedMessage(
                    config,
                    RemoteEncryptedMessage(
                        id = pending.id,
                        roomCode = room.veilCode,
                        channelType = pending.channelType,
                        targetId = pending.targetId,
                        senderPublicKey = pending.senderPublicKey,
                        iv = pending.ciphertextIv,
                        ciphertext = pending.ciphertextPayload,
                        maxViews = pending.maxViews,
                        viewsConsumed = pending.viewsConsumed,
                        createdAt = pending.timestamp
                    )
                )
                if (res.isSuccess) {
                    dao.updateMessageSync(pending.id, SyncStatus.SENT, pending.remoteMediaUrl)
                }
            }

            // 2. Sync room members (Profiles directory) & reconcile creator removals
            val existingMembers = dao.getRoomMembers(roomId).associateBy { it.publicKey }
            val membersResult = backendClient.fetchMembers(config, room.veilCode)
            val remoteMembers = membersResult.getOrDefault(emptyList())

            if (membersResult.isSuccess) {
                val remoteKeys = remoteMembers.map { it.publicKey }.toHashSet()
                // If the creator removed this device from veil_members (after initial join grace window), revoke local room.
                val pastGraceWindow = (System.currentTimeMillis() - room.createdAt) > 15_000L
                if (!isUserRoomCreator(room, me) &&
                    pastGraceWindow &&
                    remoteKeys.isNotEmpty() &&
                    me.publicKey !in remoteKeys
                ) {
                    val removedRoomName = room.roomName
                    deleteRoom(roomId)
                    onRemovedFromRoom?.invoke(removedRoomName)
                    return@runCatching 0
                }

                // Prune any peers who were removed from the room by the creator.
                for ((localPub, _) in existingMembers) {
                    if (localPub !in remoteKeys) {
                        dao.deleteRoomMember(roomId, localPub)
                    }
                }
            }

            for (rm in remoteMembers) {
                if (rm.publicKey == me.publicKey) continue
                val prev = existingMembers[rm.publicKey]
                val pfpCacheKey = rm.deviceBindingId.ifBlank { rm.publicKey.take(24) }
                val expectedCacheFile = mediaStorage.getPfpCacheFile(pfpCacheKey)
                var localPfpPath = prev?.localPfpPath.orEmpty()
                val existingLocalValid = localPfpPath.isNotBlank() && File(localPfpPath).let { it.exists() && it.length() > 0L }

                if (rm.pfpCipherUrl.isNotBlank() &&
                    (rm.pfpCipherUrl != prev?.remotePfpCipherUrl || !existingLocalValid || !expectedCacheFile.exists())
                ) {
                    val decryptedPath = downloadAndDecryptPfp(
                        config = config,
                        cacheKey = pfpCacheKey,
                        pfpCipherUrl = rm.pfpCipherUrl,
                        legacyRoomKey = roomKeyBytes
                    )
                    if (!decryptedPath.isNullOrBlank()) {
                        localPfpPath = decryptedPath
                    }
                } else if (rm.pfpCipherUrl.isBlank()) {
                    localPfpPath = ""
                }
                val safetyNum = if (crypto.isValidPublicKey(rm.publicKey)) {
                    crypto.computeSafetyNumber(me.publicKey, rm.publicKey)
                } else ""

                dao.upsertRoomMember(
                    RoomMemberEntity(
                        id = "${roomId}_${rm.publicKey.take(16)}",
                        roomId = roomId,
                        roomCode = room.veilCode,
                        deviceBindingId = rm.deviceBindingId,
                        publicKey = rm.publicKey,
                        username = VeilCryptoEngine.stripEmojis(rm.username).ifBlank { "Peer" },
                        motto = VeilCryptoEngine.enforceMaxWords(rm.motto, 20),
                        gender = rm.gender.ifBlank { "Prefer not to say" },
                        localPfpPath = localPfpPath,
                        remotePfpCipherUrl = rm.pfpCipherUrl,
                        safetyNumber = safetyNum,
                        isVerified = prev?.isVerified ?: false,
                        isMuted = prev?.isMuted ?: false,
                        isBlocked = prev?.isBlocked ?: false,
                        isOnline = true,
                        lastMessagePreview = prev?.lastMessagePreview.orEmpty(),
                        lastMessageTimestamp = prev?.lastMessageTimestamp ?: 0L,
                        unreadCount = prev?.unreadCount ?: 0,
                        joinedAt = rm.joinedAt
                    )
                )
            }

            val updatedMembersMap = dao.getRoomMembers(roomId).associateBy { it.publicKey }

            // 3. Sync Communities (Default Room Community + Strictly Isolated Private Communities)
            ensureDefaultRoomCommunity(room, config, me)
            val remoteCommunities = backendClient.fetchCommunitiesInRoom(config, room.veilCode).getOrDefault(emptyList())
            val myMemberships = backendClient.fetchMyCommunityMemberships(config, me.publicKey)
                .getOrDefault(emptyList())
                .associateBy { it.communityId }

            for (rc in remoteCommunities) {
                val membership = myMemberships[rc.id]
                // Strict Visibility Isolation: Private communities are completely invisible unless in myMemberships!
                if (!rc.isDefault && membership == null && rc.createdByPublicKey != me.publicKey) {
                    continue
                }
                val prevComm = dao.getCommunityById(rc.id)
                var protectedKey = prevComm?.encryptedCommunityKey.orEmpty()
                if (protectedKey.isBlank() && membership != null && crypto.isValidPublicKey(rc.createdByPublicKey)) {
                    val unwrapped = crypto.unwrapCommunityKeyFromPeer(
                        membership.encryptedCommunityKey,
                        rc.createdByPublicKey,
                        rc.id
                    )
                    if (!unwrapped.isNullOrBlank()) {
                        protectedKey = crypto.protectString(unwrapped)
                    }
                }
                if (protectedKey.isBlank() && rc.isDefault) {
                    protectedKey = crypto.protectString(Base64.encodeToString(roomKeyBytes, Base64.NO_WRAP))
                }

                dao.upsertCommunity(
                    CommunityEntity(
                        id = rc.id,
                        roomId = roomId,
                        roomCode = room.veilCode,
                        name = VeilCryptoEngine.stripEmojis(rc.name).ifBlank { "${room.roomName} — General" },
                        description = VeilCryptoEngine.stripEmojis(rc.description),
                        isDefault = rc.isDefault,
                        createdByPublicKey = rc.createdByPublicKey,
                        encryptedCommunityKey = protectedKey,
                        memberCount = if (rc.isDefault) updatedMembersMap.size + 1 else (prevComm?.memberCount ?: 3),
                        lastMessagePreview = prevComm?.lastMessagePreview ?: "Encrypted community",
                        lastMessageTimestamp = prevComm?.lastMessageTimestamp ?: rc.createdAt,
                        unreadCount = prevComm?.unreadCount ?: 0,
                        createdAt = rc.createdAt
                    )
                )
            }

            // 4. Sync Encrypted Messages
            val knownIds = dao.getMessageIdsForRoom(roomId).toHashSet()
            val myCommunityIds = dao.getCommunitiesForRoom(roomId).map { it.id }.toHashSet()
            val remoteMessages = backendClient.fetchEncryptedMessages(config, room.veilCode).getOrDefault(emptyList())
            var importedCount = 0

            for (rm in remoteMessages) {
                if (rm.id in knownIds) continue
                val isOutgoing = rm.senderPublicKey == me.publicKey
                val senderMember = updatedMembersMap[rm.senderPublicKey]

                // Enforce Block moderation on incoming direct messages
                if (!isOutgoing && senderMember?.isBlocked == true && rm.channelType == ChannelType.DIRECT) {
                    continue
                }

                // Enforce channel routing & private community isolation
                val localTargetId = when (rm.channelType) {
                    ChannelType.DIRECT -> {
                        if (isOutgoing) rm.targetId
                        else if (rm.targetId == me.publicKey || rm.targetId.isBlank()) rm.senderPublicKey
                        else continue
                    }
                    ChannelType.COMMUNITY -> {
                        if (rm.targetId !in myCommunityIds) continue
                        rm.targetId
                    }
                }

                val keyBytes = deriveChannelKey(room, rm.channelType, localTargetId)
                val isAlreadyDisappeared = rm.maxViews > 0 && rm.viewsConsumed >= rm.maxViews
                val decryptedJson = if (isAlreadyDisappeared) {
                    null
                } else {
                    decryptMessageEnvelope(room, rm.channelType, localTargetId, rm.iv, rm.ciphertext)
                }

                val type = decryptedJson?.optString("type")?.let {
                    runCatching { MessageContentType.valueOf(it) }.getOrDefault(MessageContentType.TEXT)
                } ?: MessageContentType.TEXT

                var localMediaPath = ""
                val remoteMediaUrl = decryptedJson?.optString("storageUrl").orEmpty()
                    .ifBlank { decryptedJson?.optString("mediaUrl").orEmpty() }
                val mediaKey = decryptedJson?.optString("mediaKey").orEmpty()
                val mediaIv = decryptedJson?.optString("mediaIv").orEmpty()
                val inlineBlob = decryptedJson?.optString("inlineBlob").orEmpty()
                val fileName = decryptedJson?.optString("fileName").orEmpty()
                val mimeType = decryptedJson?.optString("mimeType").orEmpty()

                if (!isAlreadyDisappeared && type in setOf(
                        MessageContentType.IMAGE,
                        MessageContentType.VIDEO,
                        MessageContentType.DOCUMENT,
                        MessageContentType.VOICE
                    )
                ) {
                    val cipherPacket = when {
                        remoteMediaUrl.isNotBlank() -> backendClient.downloadEncryptedBlob(config, remoteMediaUrl).getOrNull()
                        inlineBlob.isNotBlank() -> runCatching { Base64.decode(inlineBlob, Base64.DEFAULT) }.getOrNull()
                        else -> null
                    }
                    if (cipherPacket != null) {
                        runCatching {
                            val plainBytes = decryptAttachmentBytes(
                                cipherBytes = cipherPacket,
                                mediaKey = mediaKey,
                                mediaIv = mediaIv,
                                channelKey = keyBytes,
                                room = room
                            )
                            localMediaPath = mediaStorage.saveDecryptedMediaToCache(
                                messageId = rm.id,
                                bytes = plainBytes,
                                displayName = fileName.ifBlank { defaultMediaNameForType(type) },
                                mimeType = mimeType.ifBlank { defaultMimeForType(type) }
                            ).absolutePath
                        }
                    }
                }

                val decryptedText = if (isAlreadyDisappeared) {
                    ""
                } else {
                    decryptedJson?.optString("text").orEmpty()
                }
                val senderName = if (isOutgoing) me.username else (senderMember?.username ?: "Peer")

                val entity = MessageEntity(
                    id = rm.id,
                    roomId = roomId,
                    channelType = rm.channelType,
                    targetId = localTargetId,
                    isOutgoing = isOutgoing,
                    senderHandle = senderName,
                    senderPublicKey = rm.senderPublicKey,
                    contentType = type,
                    ciphertextIv = rm.iv,
                    ciphertextPayload = if (isAlreadyDisappeared) "" else rm.ciphertext,
                    decryptedText = decryptedText,
                    localMediaPath = localMediaPath,
                    remoteMediaUrl = remoteMediaUrl,
                    mediaKey = mediaKey,
                    mediaIv = mediaIv,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSizeBytes = decryptedJson?.optLong("sizeBytes", 0L) ?: 0L,
                    audioDurationMs = decryptedJson?.optLong("audioDurationMs", 0L) ?: 0L,
                    waveformPeaks = decryptedJson?.optString("waveformPeaks").orEmpty(),
                    latitude = if (decryptedJson?.has("lat") == true) decryptedJson.optDouble("lat") else null,
                    longitude = if (decryptedJson?.has("lng") == true) decryptedJson.optDouble("lng") else null,
                    locationLabel = decryptedJson?.optString("label").orEmpty(),
                    maxViews = rm.maxViews,
                    viewsConsumed = rm.viewsConsumed,
                    isDisappeared = isAlreadyDisappeared,
                    timestamp = rm.createdAt,
                    syncStatus = SyncStatus.SYNCED
                )
                dao.insertMessageIgnore(entity)
                importedCount++

                val preview = when {
                    isAlreadyDisappeared -> "Opened · Disappeared"
                    rm.maxViews > 0 -> disappearingLabel(type, rm.maxViews)
                    type == MessageContentType.TEXT -> decryptedText.ifBlank { "Encrypted message" }
                    type == MessageContentType.IMAGE -> "Photo"
                    type == MessageContentType.VIDEO -> "Video"
                    type == MessageContentType.VOICE -> "Voice note"
                    type == MessageContentType.LOCATION -> "Location"
                    else -> "File: ${fileName.ifBlank { "Attachment" }}"
                }

                val unreadDelta = if (isOutgoing) 0 else 1
                if (rm.channelType == ChannelType.DIRECT) {
                    dao.updateMemberLastMessage(roomId, localTargetId, preview, rm.createdAt, unreadDelta)
                } else {
                    dao.updateCommunityLastMessage(localTargetId, "$senderName: $preview", rm.createdAt, unreadDelta)
                }
                dao.updateRoomLastMessage(roomId, preview, rm.createdAt)

                if (!isOutgoing && senderMember?.isMuted != true && senderMember?.isBlocked != true) {
                    val commName = if (rm.channelType == ChannelType.COMMUNITY) {
                        dao.getCommunityById(localTargetId)?.name ?: room.roomName
                    } else null
                    onNewMessageAlert?.invoke(
                        IncomingMessageAlert(
                            roomId = roomId,
                            title = commName ?: senderName,
                            body = if (commName != null) "$senderName: $preview" else preview,
                            targetId = localTargetId,
                            isCommunity = rm.channelType == ChannelType.COMMUNITY
                        )
                    )
                }
            }

            // 5. Sync Incoming Encrypted Voice Call Signals (with 15s overlap to tolerate device clock skew)
            val baseSince = lastSignalPollByRoom[roomId] ?: (System.currentTimeMillis() - 45_000L)
            val querySince = (baseSince - 15_000L).coerceAtLeast(0L)
            val signals = backendClient.fetchIncomingCallSignals(config, room.veilCode, me.publicKey, querySince)
                .getOrDefault(emptyList())
            if (signals.isNotEmpty()) {
                val maxTs = signals.maxOf { it.createdAt }
                lastSignalPollByRoom[roomId] = maxOf(lastSignalPollByRoom[roomId] ?: 0L, maxTs)
                for (sig in signals) {
                    val peer = updatedMembersMap[sig.callerPublicKey]
                    onIncomingCallSignal?.invoke(room, sig, peer)
                }
            }

            importedCount
        }
    }

    /**
     * Lightweight, low-latency call-signal poller designed for 1-second WebRTC SDP/ICE
     * negotiation and background service listening without triggering full message/member sync.
     */
    suspend fun pollCallSignals(
        roomId: String,
        onIncomingCallSignal: (VeilRoomEntity, RemoteCallSignal, RoomMemberEntity?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val room = dao.getRoomById(roomId) ?: return@withContext
        val config = resolveBackendConfigForRoom(room) ?: return@withContext
        val me = crypto.getOrCreateIdentity()
        val baseSince = lastSignalPollByRoom[roomId] ?: (System.currentTimeMillis() - 45_000L)
        val querySince = (baseSince - 15_000L).coerceAtLeast(0L)
        val signals = backendClient.fetchIncomingCallSignals(config, room.veilCode, me.publicKeyBase64, querySince)
            .getOrDefault(emptyList())
        if (signals.isEmpty()) return@withContext

        val maxTs = signals.maxOf { it.createdAt }
        lastSignalPollByRoom[roomId] = maxOf(lastSignalPollByRoom[roomId] ?: 0L, maxTs)

        val membersByKey = dao.getRoomMembers(roomId).associateBy { it.publicKey }
        for (sig in signals) {
            val peer = membersByKey[sig.callerPublicKey]
            onIncomingCallSignal(room, sig, peer)
        }
    }

    fun rewindCallSignalCursor(roomId: String, lookbackMs: Long = 60_000L) {
        lastSignalPollByRoom[roomId] = (System.currentTimeMillis() - lookbackMs).coerceAtLeast(0L)
    }

    suspend fun sendEncryptedCallSignal(roomId: String, signal: RemoteCallSignal): Result<Unit> =
        withContext(Dispatchers.IO) {
            val room = dao.getRoomById(roomId) ?: return@withContext Result.failure(IllegalStateException("Room not found"))
            val config = resolveBackendConfigForRoom(room) ?: return@withContext Result.failure(IllegalStateException("Backend config missing"))
            backendClient.sendCallSignal(config, signal)
        }

    // Call History & 90-Day Retention
    suspend fun recordCallHistory(
        roomId: String,
        roomCode: String = "",
        peerPublicKey: String,
        peerUsername: String,
        peerPfpPath: String,
        direction: CallDirection,
        durationSec: Long
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val resolvedRoomCode = roomCode.ifBlank { dao.getRoomById(roomId)?.veilCode.orEmpty() }
        val resolvedPfp = peerPfpPath.ifBlank {
            dao.getRoomMember(roomId, peerPublicKey)?.localPfpPath.orEmpty()
        }
        dao.upsertCallHistory(
            CallHistoryEntity(
                id = id,
                roomId = roomId,
                roomCode = resolvedRoomCode,
                peerPublicKey = peerPublicKey,
                peerUsername = peerUsername.ifBlank { "Veil Peer" },
                peerPfpPath = resolvedPfp,
                direction = direction,
                durationSec = durationSec,
                timestamp = System.currentTimeMillis(),
                isSeen = direction != CallDirection.MISSED
            )
        )
        id
    }

    suspend fun updateCallHistory(
        id: String,
        direction: CallDirection,
        durationSec: Long
    ) = withContext(Dispatchers.IO) {
        dao.updateCallHistory(
            id = id,
            direction = direction,
            durationSec = durationSec,
            isSeen = direction != CallDirection.MISSED
        )
    }

    suspend fun markMissedCallsSeen(roomId: String) = withContext(Dispatchers.IO) {
        dao.markMissedCallsSeen(roomId)
    }

    suspend fun pruneExpiredCallHistory(): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - CALL_HISTORY_RETENTION_MS
        dao.deleteCallHistoryOlderThan(cutoff)
    }

    suspend fun resolveBackendConfigForRoom(room: VeilRoomEntity): ResolvedBackendConfig? {
        val profile = dao.getBackendProfileById(room.backendProfileId)
            ?: dao.getActiveBackendProfile()
            ?: return null
        return resolveBackendConfig(profile)
    }

    private fun resolveBackendConfig(profile: BackendProfileEntity): ResolvedBackendConfig =
        ResolvedBackendConfig(
            profile = profile,
            apiKey = getDecryptedApiKey(profile),
            customHeaders = getDecryptedCustomHeaders(profile)
        )

    private suspend fun ensureLocalProfile(): UserProfileEntity {
        val existing = dao.getUserProfile()
        if (existing != null) return existing
        val id = crypto.getOrCreateIdentity()
        val profile = UserProfileEntity(
            id = "local_user",
            deviceBindingId = deviceBinding.computeDeviceBindingId(),
            publicKey = id.publicKeyBase64,
            username = id.handle,
            isBound = false
        )
        dao.upsertUserProfile(profile)
        return profile
    }

    /**
     * Encrypts the local compressed 512x512 JPEG profile picture with a fresh AES-256-GCM key & IV,
     * uploads ciphertext to veil-media/pfp/<device_binding_id>.enc, and returns the full Storage URL
     * with the URL-fragment decryption key & IV (#k=...&iv=...) so any room member can decrypt it.
     */
    private suspend fun uploadEncryptedPfpIfPresent(
        config: ResolvedBackendConfig,
        deviceBindingId: String,
        localPfpPath: String
    ): String {
        if (localPfpPath.isBlank()) return ""
        val file = File(localPfpPath)
        if (!file.exists() || file.length() <= 0L) return ""
        return runCatching {
            val rawJpeg = file.readBytes()
            val (keyB64, ivB64) = crypto.generateMediaKeyAndIv()
            val encryptedBytes = crypto.encryptBytesWithKeyAndIv(keyB64, ivB64, rawJpeg)
            val storageUrl = backendClient.uploadEncryptedPfpBlob(config, deviceBindingId, encryptedBytes)
                .getOrThrow()
            "$storageUrl#k=$keyB64&iv=$ivB64"
        }.getOrDefault("")
    }

    /**
     * Downloads ciphertext from pfp_cipher_url via HTTP GET, decrypts it using the embedded #k=...&iv=...
     * fragment (or legacy room key fallback), and saves the JPEG into context.filesDir/pfp_cache/<cacheKey>.jpg.
     */
    private suspend fun downloadAndDecryptPfp(
        config: ResolvedBackendConfig,
        cacheKey: String,
        pfpCipherUrl: String,
        legacyRoomKey: ByteArray?
    ): String? {
        if (pfpCipherUrl.isBlank()) return null
        val downloaded = backendClient.downloadEncryptedBlob(config, pfpCipherUrl).getOrNull() ?: return null
        val fragment = pfpCipherUrl.substringAfter('#', "")
        val params = parseFragmentParams(fragment)
        val fragKey = params["k"].orEmpty()
        val fragIv = params["iv"].orEmpty()

        val plainJpeg = when {
            fragKey.isNotBlank() && fragIv.isNotBlank() -> {
                runCatching { crypto.decryptBytesWithKeyAndIv(fragKey, fragIv, downloaded) }.getOrNull()
            }
            legacyRoomKey != null -> {
                runCatching { crypto.decryptBlobPacket(legacyRoomKey, downloaded) }.getOrNull()
            }
            else -> null
        } ?: return null

        if (plainJpeg.isEmpty()) return null
        return runCatching {
            mediaStorage.saveDecryptedPfp(cacheKey, plainJpeg).absolutePath
        }.getOrNull()
    }

    private fun parseFragmentParams(fragment: String): Map<String, String> {
        if (fragment.isBlank()) return emptyMap()
        return fragment.split("&")
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null
                else pair.substring(0, idx) to pair.substring(idx + 1)
            }
            .toMap()
    }

    private suspend fun decryptMessageEnvelope(
        room: VeilRoomEntity,
        channelType: ChannelType,
        localTargetId: String,
        iv: String,
        ciphertext: String
    ): JSONObject? {
        val primaryKey = deriveChannelKey(room, channelType, localTargetId)
        val primary = runCatching { JSONObject(crypto.decryptText(primaryKey, iv, ciphertext)) }.getOrNull()
        if (primary != null) return primary
        // Fallback for legacy Default Community messages encrypted with deterministic room code key
        val deterministicKey = deriveDeterministicRoomKey(room.veilCode)
        return runCatching { JSONObject(crypto.decryptText(deterministicKey, iv, ciphertext)) }.getOrNull()
    }

    private fun decryptAttachmentBytes(
        cipherBytes: ByteArray,
        mediaKey: String,
        mediaIv: String,
        channelKey: ByteArray,
        room: VeilRoomEntity
    ): ByteArray {
        if (mediaKey.isNotBlank() && mediaIv.isNotBlank()) {
            return crypto.decryptBytesWithKeyAndIv(mediaKey, mediaIv, cipherBytes)
        }
        return runCatching { crypto.decryptBlobPacket(channelKey, cipherBytes) }.getOrElse {
            crypto.decryptBlobPacket(deriveDeterministicRoomKey(room.veilCode), cipherBytes)
        }
    }

    private fun defaultMediaNameForType(type: MessageContentType): String = when (type) {
        MessageContentType.IMAGE -> "photo.jpg"
        MessageContentType.VIDEO -> "video.mp4"
        MessageContentType.VOICE -> "voice.m4a"
        MessageContentType.DOCUMENT -> "document.pdf"
        else -> "attachment.bin"
    }

    private fun defaultMimeForType(type: MessageContentType): String = when (type) {
        MessageContentType.IMAGE -> "image/jpeg"
        MessageContentType.VIDEO -> "video/mp4"
        MessageContentType.VOICE -> "audio/mp4"
        MessageContentType.DOCUMENT -> "application/pdf"
        else -> "application/octet-stream"
    }

    private suspend fun deriveChannelKey(
        room: VeilRoomEntity,
        channelType: ChannelType,
        targetId: String
    ): ByteArray {
        if (channelType == ChannelType.COMMUNITY) {
            val comm = dao.getCommunityById(targetId)
            if (comm?.isDefault == true) {
                return deriveRoomKey(room)
            }
            val commKeyB64 = comm?.encryptedCommunityKey?.let { crypto.unprotectString(it) }.orEmpty()
            if (commKeyB64.isNotBlank()) {
                return crypto.deriveSymmetricKey(commKeyB64, "community:$targetId")
            }
        } else if (targetId.isNotBlank() && crypto.isValidPublicKey(targetId)) {
            return runCatching {
                crypto.deriveEcdhRoomKey(targetId, "dm:${room.veilCode}")
            }.getOrElse { deriveRoomKey(room) }
        }
        return deriveRoomKey(room)
    }

    private fun deriveDeterministicRoomKey(veilCode: String): ByteArray {
        return crypto.deriveSymmetricRoomKey(
            Base64.encodeToString(veilCode.toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
            "veil-room-v1:$veilCode"
        )
    }

    private fun deriveRoomKey(room: VeilRoomEntity): ByteArray {
        val storedKey = crypto.unprotectString(room.encryptedRoomKey)
        if (storedKey.isNotBlank()) {
            return crypto.deriveSymmetricRoomKey(storedKey, room.veilCode)
        }
        return deriveDeterministicRoomKey(room.veilCode)
    }

    private fun disappearingLabel(type: MessageContentType, maxViews: Int): String {
        val noun = when (type) {
            MessageContentType.IMAGE -> "Photo"
            MessageContentType.VIDEO -> "Video"
            MessageContentType.VOICE -> "Voice Note"
            MessageContentType.DOCUMENT -> "File"
            MessageContentType.LOCATION -> "Location"
            MessageContentType.TEXT -> "Message"
        }
        return if (maxViews == 1) "View-Once $noun" else "$maxViews-View $noun"
    }

    companion object {
        private const val MAX_INLINE_CIPHER_BYTES = 240_000
        const val CALL_HISTORY_RETENTION_MS = 90L * 24L * 60L * 60L * 1000L
    }
}
