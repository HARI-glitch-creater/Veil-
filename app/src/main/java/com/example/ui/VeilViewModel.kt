package com.example.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.LocalIdentity
import com.example.crypto.ParsedRoomInvite
import com.example.data.BackendProfileDraft
import com.example.data.HostRoomOutcome
import com.example.data.VeilRepository
import com.example.data.local.BackendProfileEntity
import com.example.data.local.MessageContentType
import com.example.data.local.MessageEntity
import com.example.data.local.VeilRoomEntity
import com.example.data.network.ConnectionTestResult
import com.example.media.VoiceNoteManager
import com.example.media.VoicePlaybackState
import com.example.media.VoiceRecordingState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VeilViewModel(application: Application) : AndroidViewModel(application) {

    val repository = VeilRepository(application)
    private val voiceManager = VoiceNoteManager(application, viewModelScope)

    val rooms: StateFlow<List<VeilRoomEntity>> = repository.roomsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val backendProfiles: StateFlow<List<BackendProfileEntity>> = repository.backendProfilesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _identity = MutableStateFlow(repository.getIdentity())
    val identity: StateFlow<LocalIdentity> = _identity.asStateFlow()

    val recordingState: StateFlow<VoiceRecordingState> = voiceManager.recordingState
    val playbackState: StateFlow<VoicePlaybackState> = voiceManager.playbackState

    private val _connectionTest = MutableStateFlow<ConnectionTestResult?>(null)
    val connectionTest: StateFlow<ConnectionTestResult?> = _connectionTest.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _hostOutcome = MutableStateFlow<HostRoomOutcome?>(null)
    val hostOutcome: StateFlow<HostRoomOutcome?> = _hostOutcome.asStateFlow()

    private var activeSyncJob: Job? = null
    private var activeWsJob: Job? = null

    fun consumeToast() {
        _toastMessage.value = null
    }

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun clearConnectionTest() {
        _connectionTest.value = null
    }

    fun clearHostOutcome() {
        _hostOutcome.value = null
    }

    fun updateHandle(newHandle: String) {
        _identity.value = repository.updateHandle(newHandle)
        showToast("Handle updated")
    }

    fun observeRoom(roomId: String) = repository.observeRoom(roomId)

    fun observeMessages(roomId: String) = repository.observeMessages(roomId)

    fun startRoomSync(roomId: String) {
        activeSyncJob?.cancel()
        activeWsJob?.cancel()
        viewModelScope.launch { repository.clearUnread(roomId) }

        activeSyncJob = viewModelScope.launch {
            while (isActive) {
                repository.syncRoom(roomId)
                val room = rooms.value.firstOrNull { it.id == roomId }
                val intervalSec = backendProfiles.value
                    .firstOrNull { it.id == room?.backendProfileId }
                    ?.pollIntervalSec ?: 5
                delay(intervalSec.coerceIn(2, 60) * 1000L)
            }
        }

        activeWsJob = viewModelScope.launch {
            val room = rooms.value.firstOrNull { it.id == roomId } ?: return@launch
            val config = repository.resolveBackendConfig(room.backendProfileId) ?: return@launch
            repository.backendClient.observeWebSocketMessages(config, room.veilCode)
                .collectLatest { incoming ->
                    val latestRoom = rooms.value.firstOrNull { it.id == roomId } ?: room
                    repository.ingestRemoteEncryptedMessage(latestRoom, config, incoming)
                }
        }
    }

    fun stopRoomSync() {
        activeSyncJob?.cancel()
        activeSyncJob = null
        activeWsJob?.cancel()
        activeWsJob = null
        voiceManager.stopPlayback()
        voiceManager.cancelRecording()
    }

    fun syncRoomNow(roomId: String) {
        viewModelScope.launch {
            repository.syncRoom(roomId)
            showToast("Encrypted sync complete")
        }
    }

    fun testConnectionDraft(draft: BackendProfileDraft) {
        viewModelScope.launch {
            _isBusy.value = true
            _connectionTest.value = repository.testDraftConnection(draft)
            _isBusy.value = false
        }
    }

    fun testSavedProfile(profile: BackendProfileEntity) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.testSavedProfile(profile)
            _connectionTest.value = res
            _isBusy.value = false
            showToast(res.message)
        }
    }

    fun saveBackendProfile(draft: BackendProfileDraft, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveBackendProfile(draft, makeActive = true)
            _isBusy.value = false
            result.onSuccess {
                showToast("Saved ${it.name}")
                onSaved()
            }.onFailure {
                showToast(it.message ?: "Invalid configuration")
            }
        }
    }

    fun setActiveBackend(profileId: String) {
        viewModelScope.launch {
            repository.setActiveBackendProfile(profileId)
            showToast("Active backend switched")
        }
    }

    fun deleteBackend(profileId: String) {
        viewModelScope.launch {
            repository.deleteBackendProfile(profileId)
            showToast("Backend profile removed")
        }
    }

    fun hostVeilRoom(draft: BackendProfileDraft, roomName: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.hostVeilRoom(draft, roomName)
            _isBusy.value = false
            result.onSuccess { outcome ->
                _hostOutcome.value = outcome
                showToast(outcome.note)
            }.onFailure { err ->
                showToast(err.message ?: "Failed to host Veil Room")
            }
        }
    }

    fun joinVeilRoom(
        rawCode: String,
        existingProfileId: String?,
        inlineDraft: BackendProfileDraft?,
        parsedInvite: ParsedRoomInvite?,
        onJoined: (VeilRoomEntity) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.joinVeilRoom(
                rawVeilCode = rawCode,
                existingProfileId = existingProfileId,
                inlineDraft = inlineDraft,
                parsedInvite = parsedInvite
            )
            _isBusy.value = false
            result.onSuccess { room ->
                showToast("Joined ${room.roomName} with E2EE")
                onJoined(room)
            }.onFailure { err ->
                showToast(err.message ?: "Could not join Veil Room")
            }
        }
    }

    fun sendText(roomId: String, text: String) {
        viewModelScope.launch {
            repository.sendTextMessage(roomId, text).onFailure {
                showToast(it.message ?: "Failed to encrypt message")
            }
        }
    }

    fun sendPickedMedia(roomId: String, uri: Uri, isVideo: Boolean) {
        viewModelScope.launch {
            val type = if (isVideo) MessageContentType.VIDEO else MessageContentType.IMAGE
            repository.sendMediaFromUri(roomId, uri, type).onFailure {
                showToast(it.message ?: "Failed to attach media")
            }
        }
    }

    fun sendPickedDocument(roomId: String, uri: Uri) {
        viewModelScope.launch {
            repository.sendMediaFromUri(roomId, uri, MessageContentType.DOCUMENT).onFailure {
                showToast(it.message ?: "Failed to attach document")
            }
        }
    }

    fun sendManualLocation(roomId: String, lat: Double, lon: Double, label: String) {
        viewModelScope.launch {
            repository.sendLocation(roomId, lat, lon, label).onFailure {
                showToast(it.message ?: "Failed to send location")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun captureAndSendCurrentLocation(roomId: String, onFallbackNeeded: () -> Unit) {
        val app = getApplication<Application>()
        val fused = LocationServices.getFusedLocationProviderClient(app)
        fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    sendManualLocation(roomId, loc.latitude, loc.longitude, "Current Location")
                } else {
                    val fallback = queryLastKnownSystemLocation(app)
                    if (fallback != null) {
                        sendManualLocation(roomId, fallback.latitude, fallback.longitude, "Last Known Location")
                    } else {
                        showToast("GPS signal unavailable — enter coordinates manually")
                        onFallbackNeeded()
                    }
                }
            }
            .addOnFailureListener {
                val fallback = queryLastKnownSystemLocation(app)
                if (fallback != null) {
                    sendManualLocation(roomId, fallback.latitude, fallback.longitude, "Last Known Location")
                } else {
                    showToast("Location unavailable — enter coordinates manually")
                    onFallbackNeeded()
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun queryLastKnownSystemLocation(app: Application): Location? {
        return runCatching {
            val lm = app.getSystemService(LocationManager::class.java) ?: return null
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()
    }

    fun startVoiceRecording() {
        val target = repository.mediaStorage.createVoiceNoteTarget()
        voiceManager.startRecording(target).onFailure {
            showToast("Could not start microphone recording")
        }
    }

    fun cancelVoiceRecording() {
        voiceManager.cancelRecording()
    }

    fun finishAndSendVoiceRecording(roomId: String) {
        val completed = voiceManager.stopAndFinalizeRecording() ?: return
        viewModelScope.launch {
            repository.sendVoiceNote(
                roomId = roomId,
                audioFile = completed.file,
                durationMs = completed.durationMs,
                waveformPeaks = completed.peaks
            ).onFailure {
                showToast(it.message ?: "Failed to send voice note")
            }
        }
    }

    fun toggleVoicePlayback(message: MessageEntity) {
        if (message.localMediaPath.isBlank()) {
            showToast("Audio file not available locally")
            return
        }
        voiceManager.togglePlayback(message.id, message.localMediaPath)
    }

    fun seekVoicePlayback(messageId: String, fraction: Float) {
        voiceManager.seekPlayback(messageId, fraction)
    }

    fun setRoomVerified(roomId: String, verified: Boolean) {
        viewModelScope.launch {
            repository.setRoomVerified(roomId, verified)
            showToast(if (verified) "Safety number verified" else "Verification cleared")
        }
    }

    fun deleteRoom(roomId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteRoom(roomId)
            showToast("Veil Room removed")
            onDeleted()
        }
    }

    fun buildRoomInviteUri(room: VeilRoomEntity, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val uri = repository.buildInviteUriForRoom(room)
            if (uri != null) {
                onReady(uri)
            } else {
                showToast("Backend configuration missing for this room")
            }
        }
    }

    fun parseRoomInvite(raw: String): ParsedRoomInvite? = repository.crypto.parseRoomInviteUri(raw)

    fun formatVeilCode(raw: String): String = repository.crypto.formatVeilCode(raw)

    fun formatSafetyNumberGroups(safety60: String): List<String> =
        repository.crypto.formatSafetyNumberGroups(safety60)

    override fun onCleared() {
        super.onCleared()
        stopRoomSync()
        voiceManager.release()
    }
}
