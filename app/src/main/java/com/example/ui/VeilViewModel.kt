package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.call.ActiveCallState
import com.example.call.IncomingCallBridge
import com.example.call.VoiceCallController
import com.example.crypto.LocalIdentity
import com.example.data.BackendProfileDraft
import com.example.data.BootRoute
import com.example.data.HostRoomOutcome
import com.example.data.VeilRepository
import com.example.data.local.BackendProfileEntity
import com.example.data.local.CallHistoryEntity
import com.example.data.local.ChannelType
import com.example.data.local.CommunityEntity
import com.example.data.local.CommunityMemberEntity
import com.example.data.local.MessageContentType
import com.example.data.local.MessageEntity
import com.example.data.local.RoomMemberEntity
import com.example.data.local.UserProfileEntity
import com.example.data.local.VeilRoomEntity
import com.example.data.network.ConnectionTestResult
import com.example.media.VoiceNoteManager
import com.example.media.VoicePlaybackState
import com.example.media.VoiceRecordingState
import com.example.notifications.VeilNotificationHelper
import com.example.ui.theme.LauncherIconManager
import com.example.ui.theme.ThemePreferences
import com.example.ui.theme.VeilAccentPresets
import com.example.update.ReleaseUpdateInfo
import com.example.update.UpdateManager
import com.example.workers.VeilWorkScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class OnboardingDraft(
    val username: String = "",
    val motto: String = "",
    val gender: String = "Prefer not to say",
    val localPfpPath: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class VeilViewModel(application: Application) : AndroidViewModel(application) {

    val repository = VeilRepository(application)
    val themePreferences = ThemePreferences(application)
    val notificationHelper = VeilNotificationHelper(application)
    val updateManager = UpdateManager(application)
    val voiceManager = VoiceNoteManager(application, viewModelScope)

    val voiceCallController = VoiceCallController(
        context = application,
        scope = viewModelScope,
        crypto = repository.crypto,
        notificationHelper = notificationHelper,
        onSendEncryptedSignal = { roomId, signal ->
            repository.sendEncryptedCallSignal(roomId, signal)
        },
        onRecordCallHistory = { roomId, peerPub, peerName, peerPfp, dir, dur ->
            repository.recordCallHistory(roomId, peerPub, peerName, peerPfp, dir, dur)
        }
    )

    private val _bootRoute = MutableStateFlow<BootRoute?>(null)
    val bootRoute: StateFlow<BootRoute?> = _bootRoute.asStateFlow()

    private val _onboardingDraft = MutableStateFlow(OnboardingDraft())
    val onboardingDraft: StateFlow<OnboardingDraft> = _onboardingDraft.asStateFlow()

    val userProfile: StateFlow<UserProfileEntity?> = repository.observeUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val backendProfiles: StateFlow<List<BackendProfileEntity>> = repository.observeBackendProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rooms: StateFlow<List<VeilRoomEntity>> = repository.observeRooms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeRoom: StateFlow<VeilRoomEntity?> = repository.observeActiveRoom()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Stealth Room Creator flag: true ONLY when the current device-bound user matches
     * veil_rooms.host_public_key (and host_device_id) for the active room.
     */
    val isActiveRoomCreator: StateFlow<Boolean> = combine(activeRoom, userProfile) { room, profile ->
        repository.isUserRoomCreator(room, profile)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val activeRoomMembers: StateFlow<List<RoomMemberEntity>> = activeRoom.flatMapLatest { room ->
        if (room != null) repository.observeRoomMembers(room.id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeRoomCommunities: StateFlow<List<CommunityEntity>> = activeRoom.flatMapLatest { room ->
        if (room != null) repository.observeCommunitiesForRoom(room.id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeRoomCallHistory: StateFlow<List<CallHistoryEntity>> = activeRoom.flatMapLatest { room ->
        if (room != null) repository.observeCallHistory(room.id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accentColor: StateFlow<Color> = themePreferences.accentColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VeilAccentPresets.DefaultAccent)

    val activeCallState: StateFlow<ActiveCallState> = voiceCallController.callState
    val recordingState: StateFlow<VoiceRecordingState> = voiceManager.recordingState
    val playbackState: StateFlow<VoicePlaybackState> = voiceManager.playbackState

    private val _availableUpdate = MutableStateFlow<ReleaseUpdateInfo?>(null)
    val availableUpdate: StateFlow<ReleaseUpdateInfo?> = _availableUpdate.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var activeSyncJob: Job? = null
    private var realtimeJob: Job? = null
    private var launcherIconJob: Job? = null
    private var notifCounter = 1000

    init {
        notificationHelper.ensureChannels()
        VeilWorkScheduler.scheduleAll(application)

        IncomingCallBridge.onDeclineFromLockScreen = { _, _, _, _ ->
            voiceCallController.declineIncomingCall()
        }

        viewModelScope.launch {
            repository.pruneExpiredCallHistory()
            val route = repository.evaluateBootState()
            _bootRoute.value = route
            if (route == BootRoute.REINSTALL_RESTORED) {
                postBanner("Existing Veil profile detected on this device and restored.")
            }
            checkForUpdates(silent = true)
        }

        // Automatically keep the active room synced with WebSocket realtime + polling fallback
        viewModelScope.launch {
            activeRoom.collect { room ->
                if (room != null) {
                    startRoomSyncLoop(room.id)
                } else {
                    stopRoomSyncLoop()
                }
            }
        }
    }

    fun getLocalIdentity(): LocalIdentity = repository.getLocalIdentity()
    fun getDeviceBindingId(): String = repository.getDeviceBindingId()

    fun postBanner(message: String) {
        _statusBanner.value = message
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun updateOnboardingDraft(transform: (OnboardingDraft) -> OnboardingDraft) {
        _onboardingDraft.value = transform(_onboardingDraft.value)
    }

    fun compressAndStoreAvatar(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val file = repository.mediaStorage.compressAvatarToJpeg512(uri)
            if (file != null) {
                onResult(file.absolutePath)
            } else {
                postBanner("Unable to process selected profile image.")
            }
        }
    }

    fun createCameraCaptureTarget(): Pair<File, Uri> = repository.mediaStorage.createCameraPhotoTarget()

    fun confirmAndBindProfile(onResult: (Result<Boolean>) -> Unit) {
        val draft = _onboardingDraft.value
        viewModelScope.launch {
            val res = repository.confirmAndBindProfile(
                username = draft.username,
                motto = draft.motto,
                gender = draft.gender,
                localPfpPath = draft.localPfpPath
            )
            res.onSuccess { (_, wasRestored) ->
                if (wasRestored) {
                    postBanner("Account already bound to this phone. Redirected to your existing profile.")
                }
                val roomsNow = repository.getAllRooms()
                _bootRoute.value = if (roomsNow.isNotEmpty()) BootRoute.HOME else BootRoute.ROOM_GATEWAY
                onResult(Result.success(wasRestored))
            }.onFailure { err ->
                postBanner(err.message ?: "Unable to bind profile.")
                onResult(Result.failure(err))
            }
        }
    }

    fun updateProfileAndSync(
        username: String,
        motto: String,
        gender: String,
        localPfpPath: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.updateLocalProfileAndSyncAllRooms(username, motto, gender, localPfpPath)
            res.onSuccess {
                postBanner("Profile updated and synced across your Veil rooms.")
                onComplete(Result.success(Unit))
            }.onFailure { err ->
                postBanner(err.message ?: "Failed to update profile.")
                onComplete(Result.failure(err))
            }
        }
    }

    // Theme customization & Dynamic Home Screen Launcher Icon switching
    fun setAccentColor(color: Color) {
        viewModelScope.launch {
            themePreferences.saveAccentColor(color)
        }
        // Debounce PackageManager alias switching so dragging the HSV color wheel remains smooth.
        launcherIconJob?.cancel()
        launcherIconJob = viewModelScope.launch {
            delay(500L)
            LauncherIconManager.applyLauncherIconForColor(getApplication(), color)
        }
    }

    // Backend management
    fun saveBackendProfile(draft: BackendProfileDraft, onComplete: (Result<BackendProfileEntity>) -> Unit) {
        viewModelScope.launch {
            val res = repository.saveBackendProfile(draft, setActive = true)
            onComplete(res)
        }
    }

    fun testBackendDraft(draft: BackendProfileDraft, onResult: (ConnectionTestResult) -> Unit) {
        viewModelScope.launch {
            onResult(repository.testBackendDraft(draft))
        }
    }

    fun testSavedBackendProfile(profile: BackendProfileEntity, onResult: (ConnectionTestResult) -> Unit) {
        viewModelScope.launch {
            onResult(repository.testSavedBackendProfile(profile))
        }
    }

    fun setActiveBackendProfile(profileId: String) {
        viewModelScope.launch {
            repository.setActiveBackendProfile(profileId)
            postBanner("Active backend updated.")
        }
    }

    fun deleteBackendProfile(profileId: String) {
        viewModelScope.launch {
            repository.deleteBackendProfile(profileId)
            postBanner("Backend profile removed.")
        }
    }

    // Room Host / Join / Switch
    fun hostRoom(roomName: String, backendProfileId: String, onResult: (Result<HostRoomOutcome>) -> Unit) {
        viewModelScope.launch {
            val res = repository.hostRoom(roomName, backendProfileId)
            if (res.isSuccess) {
                _bootRoute.value = BootRoute.HOME
            }
            onResult(res)
        }
    }

    fun joinRoom(
        veilCode: String,
        roomName: String,
        backendProfileId: String,
        hostPublicKey: String = "",
        hostHandle: String = "",
        roomKeyBase64: String = "",
        onResult: (Result<VeilRoomEntity>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.joinRoom(
                veilCodeRaw = veilCode,
                roomNameHint = roomName,
                backendProfileId = backendProfileId,
                hostPublicKey = hostPublicKey,
                hostHandle = hostHandle,
                roomKeyBase64 = roomKeyBase64
            )
            if (res.isSuccess) {
                _bootRoute.value = BootRoute.HOME
            }
            onResult(res)
        }
    }

    fun switchActiveRoom(roomId: String) {
        viewModelScope.launch {
            repository.setActiveRoom(roomId)
            postBanner("Switched active Veil Room.")
        }
    }

    fun deleteRoom(roomId: String) {
        viewModelScope.launch {
            repository.deleteRoom(roomId)
            val remaining = repository.getAllRooms()
            if (remaining.isEmpty()) {
                _bootRoute.value = BootRoute.ROOM_GATEWAY
            }
        }
    }

    /**
     * Exclusive Stealth Room Creator action: removes a profile from the active room.
     */
    fun removeMemberFromRoom(
        roomId: String,
        member: RoomMemberEntity,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.removeMemberFromRoom(roomId, member.publicKey)
            res.onSuccess {
                postBanner("${member.username} was removed from the room.")
            }.onFailure { err ->
                postBanner(err.message ?: "Unable to remove profile from room.")
            }
            onComplete(res)
        }
    }

    // Channel observations
    fun observeMessages(roomId: String, channelType: ChannelType, targetId: String): Flow<List<MessageEntity>> =
        repository.observeMessagesForChannel(roomId, channelType, targetId)

    fun observeMember(roomId: String, publicKey: String): Flow<RoomMemberEntity?> =
        repository.observeRoomMember(roomId, publicKey)

    fun observeCommunity(communityId: String): Flow<CommunityEntity?> =
        repository.observeCommunityById(communityId)

    fun observeCommunityMembers(communityId: String): Flow<List<CommunityMemberEntity>> =
        repository.observeCommunityMembers(communityId)

    // Moderation & Profiles
    fun toggleMuteMember(roomId: String, peerPublicKey: String, muted: Boolean) {
        viewModelScope.launch {
            repository.setMemberMuted(roomId, peerPublicKey, muted)
            postBanner(if (muted) "Profile muted." else "Profile unmuted.")
        }
    }

    fun toggleBlockMember(roomId: String, peerPublicKey: String, blocked: Boolean) {
        viewModelScope.launch {
            repository.setMemberBlocked(roomId, peerPublicKey, blocked)
            postBanner(if (blocked) "Profile blocked." else "Profile unblocked.")
        }
    }

    fun setMemberVerified(roomId: String, peerPublicKey: String, verified: Boolean) {
        viewModelScope.launch {
            repository.setMemberVerified(roomId, peerPublicKey, verified)
            postBanner(if (verified) "Safety number verified." else "Verification cleared.")
        }
    }

    fun reportMember(roomId: String, reportedPublicKey: String, reason: String, notes: String) {
        viewModelScope.launch {
            repository.reportMember(roomId, reportedPublicKey, reason, notes)
                .onSuccess { postBanner("Report submitted for moderation.") }
                .onFailure { postBanner("Unable to submit report: ${it.message}") }
        }
    }

    fun clearDirectUnread(roomId: String, peerPublicKey: String) {
        viewModelScope.launch { repository.clearDirectUnread(roomId, peerPublicKey) }
    }

    fun clearCommunityUnread(communityId: String) {
        viewModelScope.launch { repository.clearCommunityUnread(communityId) }
    }

    // Communities (Strict minimum 3 profiles total)
    fun createPrivateCommunity(
        roomId: String,
        name: String,
        description: String,
        selectedPeerPublicKeys: List<String>,
        onResult: (Result<CommunityEntity>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.createPrivateCommunity(roomId, name, description, selectedPeerPublicKeys)
            res.onSuccess { postBanner("Private community '${it.name}' created.") }
                .onFailure { postBanner(it.message ?: "Unable to create community.") }
            onResult(res)
        }
    }

    // Messaging & Disappearing Views
    fun sendText(roomId: String, channelType: ChannelType, targetId: String, text: String, maxViews: Int) {
        viewModelScope.launch {
            repository.sendTextMessage(roomId, channelType, targetId, text, maxViews)
                .onFailure { postBanner(it.message ?: "Unable to send message.") }
        }
    }

    fun sendMediaUri(
        roomId: String,
        channelType: ChannelType,
        targetId: String,
        uri: Uri,
        preferredType: MessageContentType,
        maxViews: Int
    ) {
        viewModelScope.launch {
            repository.sendMediaFromUri(roomId, channelType, targetId, uri, preferredType, maxViews)
                .onFailure { postBanner(it.message ?: "Unable to encrypt and send file.") }
        }
    }

    fun sendCapturedPhoto(
        roomId: String,
        channelType: ChannelType,
        targetId: String,
        file: File,
        maxViews: Int
    ) {
        viewModelScope.launch {
            repository.sendCapturedCameraPhoto(roomId, channelType, targetId, file, maxViews)
                .onFailure { postBanner(it.message ?: "Unable to send photo.") }
        }
    }

    fun startVoiceRecording() {
        val target = repository.mediaStorage.createVoiceNoteTarget()
        voiceManager.startRecording(target)
            .onFailure { postBanner("Microphone unavailable: ${it.message}") }
    }

    fun stopAndSendVoiceNote(roomId: String, channelType: ChannelType, targetId: String, maxViews: Int) {
        val completed = voiceManager.stopAndFinalizeRecording() ?: return
        viewModelScope.launch {
            repository.sendVoiceNote(
                roomId = roomId,
                channelType = channelType,
                targetId = targetId,
                voiceFile = completed.file,
                durationMs = completed.durationMs,
                peaks = completed.peaks,
                maxViews = maxViews
            ).onFailure { postBanner(it.message ?: "Unable to send voice note.") }
        }
    }

    fun cancelVoiceRecording() {
        voiceManager.cancelRecording()
    }

    fun sendLocation(
        roomId: String,
        channelType: ChannelType,
        targetId: String,
        lat: Double,
        lng: Double,
        label: String,
        maxViews: Int
    ) {
        viewModelScope.launch {
            repository.sendLocation(roomId, channelType, targetId, lat, lng, label, maxViews)
                .onFailure { postBanner(it.message ?: "Unable to share location.") }
        }
    }

    fun consumeEphemeralView(messageId: String) {
        viewModelScope.launch {
            repository.consumeEphemeralView(messageId)
        }
    }

    // Voice-Only Calls
    fun startVoiceCall(member: RoomMemberEntity) {
        val room = activeRoom.value ?: return
        if (member.isBlocked) {
            postBanner("Unblock ${member.username} before placing a voice call.")
            return
        }
        voiceCallController.startOutgoingCall(
            roomId = room.id,
            roomCode = room.veilCode,
            roomName = room.roomName,
            peerPublicKey = member.publicKey,
            peerUsername = member.username,
            peerPfpPath = member.localPfpPath
        )
    }

    fun startVoiceCallByKey(peerPublicKey: String, peerUsername: String, peerPfpPath: String) {
        val room = activeRoom.value ?: return
        voiceCallController.startOutgoingCall(
            roomId = room.id,
            roomCode = room.veilCode,
            roomName = room.roomName,
            peerPublicKey = peerPublicKey,
            peerUsername = peerUsername,
            peerPfpPath = peerPfpPath
        )
    }

    fun markMissedCallsSeen(roomId: String) {
        viewModelScope.launch { repository.markMissedCallsSeen(roomId) }
    }

    // Updates
    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            val res = updateManager.checkForUpdate()
            _isCheckingUpdate.value = false
            res.onSuccess { info ->
                _availableUpdate.value = info
                if (!silent && info == null) {
                    postBanner("Veil is up to date.")
                }
            }.onFailure { err ->
                if (!silent) {
                    postBanner(err.message ?: "Unable to check for updates.")
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _availableUpdate.value = null
    }

    fun startApkUpdateDownload(info: ReleaseUpdateInfo) {
        if (!updateManager.canRequestPackageInstalls()) {
            postBanner("Allow installing updates from Veil in Android settings, then tap Update Now again.")
            updateManager.openInstallUnknownAppsSettings()
            return
        }
        updateManager.downloadAndInstallApk(info) { status ->
            postBanner(status)
        }.onFailure { err ->
            postBanner(err.message ?: "Update download failed.")
        }
    }

    // Sync Loop (Realtime WebSocket + Polling Fallback)
    fun manualSyncActiveRoom() {
        val roomId = activeRoom.value?.id ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            runSyncOnce(roomId)
            _isSyncing.value = false
        }
    }

    private fun startRoomSyncLoop(roomId: String) {
        stopRoomSyncLoop()
        activeSyncJob = viewModelScope.launch {
            while (isActive) {
                runSyncOnce(roomId)
                delay(4_500L)
            }
        }
        realtimeJob = viewModelScope.launch {
            val room = repository.getRoomById(roomId) ?: return@launch
            val config = repository.resolveBackendConfigForRoom(room) ?: return@launch
            runCatching {
                repository.backendClient.observeRealtimePings(config, room.veilCode).collect {
                    runSyncOnce(roomId)
                }
            }
        }
    }

    private suspend fun runSyncOnce(roomId: String) {
        repository.syncRoom(
            roomId = roomId,
            onNewMessageAlert = { alert ->
                notificationHelper.showMessageNotification(
                    notificationId = notifCounter++,
                    title = alert.title,
                    body = alert.body,
                    roomId = alert.roomId,
                    targetId = alert.targetId,
                    isCommunity = alert.isCommunity
                )
            },
            onIncomingCallSignal = { room, signal, peer ->
                voiceCallController.handleIncomingSignal(
                    roomId = room.id,
                    roomCode = room.veilCode,
                    roomName = room.roomName,
                    signal = signal,
                    peerUsername = peer?.username ?: "Veil Peer",
                    peerPfpPath = peer?.localPfpPath.orEmpty(),
                    isPeerMuted = peer?.isMuted ?: false,
                    isPeerBlocked = peer?.isBlocked ?: false
                )
            },
            onRemovedFromRoom = { removedRoomName ->
                postBanner("You were removed from '$removedRoomName'.")
            }
        )
    }

    private fun stopRoomSyncLoop() {
        activeSyncJob?.cancel()
        activeSyncJob = null
        realtimeJob?.cancel()
        realtimeJob = null
    }

    override fun onCleared() {
        stopRoomSyncLoop()
        voiceManager.release()
        voiceCallController.release()
        super.onCleared()
    }
}
