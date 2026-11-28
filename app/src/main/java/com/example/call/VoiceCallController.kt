package com.example.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.crypto.VeilCryptoEngine
import com.example.data.local.CallDirection
import com.example.data.network.RemoteCallSignal
import com.example.notifications.VeilNotificationHelper
import com.example.service.VeilAppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.UUID

enum class VoiceCallStatus {
    IDLE,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    CONNECTING,
    CONNECTED,
    ENDED
}

data class ActiveCallState(
    val status: VoiceCallStatus = VoiceCallStatus.IDLE,
    val roomId: String = "",
    val roomCode: String = "",
    val roomName: String = "",
    val peerPublicKey: String = "",
    val peerUsername: String = "",
    val peerPfpPath: String = "",
    val isOutgoing: Boolean = true,
    val isMicMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val pendingOfferSdp: String = "",
    val statusMessage: String = ""
)

object IncomingCallBridge {
    @Volatile
    var onAcceptFromLockScreen: ((String, String, String, String, String, String) -> Unit)? = null

    @Volatile
    var onDeclineFromLockScreen: ((String, String, String, String) -> Unit)? = null

    @Volatile
    var onRemoteCancelled: (() -> Unit)? = null
}

private data class QueuedSignal(
    val roomId: String,
    val signal: RemoteCallSignal
)

/**
 * Strictly Audio-Only WebRTC Voice Call Controller.
 * Zero video tracks, zero video capturers, zero camera APIs.
 *
 * Features:
 * - JavaAudioDeviceModule with hardware AEC + Noise Suppression + AudioFocus
 * - Explicit AudioManager.MODE_IN_COMMUNICATION, unmuted mic, reliable Speaker toggle
 * - Pending remote ICE candidate buffering until setRemoteDescription completes
 * - Serialized encrypted signaling queue so OFFER/ANSWER arrive before trickled ICE
 * - True transition to CONNECTED + live 00:00 timer upon IceConnectionState.CONNECTED
 * - Immediate Room call-log persistence (OUTGOING / INCOMING / MISSED) + duration updates
 */
class VoiceCallController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val crypto: VeilCryptoEngine,
    private val notificationHelper: VeilNotificationHelper,
    private val onSendEncryptedSignal: suspend (roomId: String, signal: RemoteCallSignal) -> Result<Unit>,
    private val onRecordCallHistory: suspend (
        roomId: String,
        roomCode: String,
        peerPublicKey: String,
        peerUsername: String,
        peerPfpPath: String,
        direction: CallDirection,
        durationSec: Long
    ) -> String,
    private val onUpdateCallHistory: suspend (
        id: String,
        direction: CallDirection,
        durationSec: Long
    ) -> Unit
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _callState = MutableStateFlow(ActiveCallState())
    val callState: StateFlow<ActiveCallState> = _callState.asStateFlow()

    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var timerJob: Job? = null
    private var ringTimeoutJob: Job? = null
    private var activeRingtone: Ringtone? = null
    private var connectedAtMs: Long = 0L
    @Volatile
    private var activeCallLogId: String? = null

    // Buffer remote ICE candidates that arrive before setRemoteDescription succeeds
    private val pendingRemoteIceCandidates = mutableListOf<IceCandidate>()
    @Volatile
    private var isRemoteDescriptionSet: Boolean = false

    private val processedSignalIds = LinkedHashSet<String>()
    private val outboundSignalChannel = Channel<QueuedSignal>(Channel.UNLIMITED)

    init {
        // Drain outbound signals strictly in order with 1 automatic retry
        scope.launch(Dispatchers.IO) {
            for (item in outboundSignalChannel) {
                val first = runCatching { onSendEncryptedSignal(item.roomId, item.signal) }.getOrNull()
                if (first == null || first.isFailure) {
                    delay(450L)
                    runCatching { onSendEncryptedSignal(item.roomId, item.signal) }
                }
            }
        }
    }

    private fun ensureWebRtcFactory() {
        if (factory != null) return
        runCatching {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            val commAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val adm = JavaAudioDeviceModule.builder(appContext)
                .setAudioAttributes(commAttrs)
                .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
                .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
                .createAudioDeviceModule()
            audioDeviceModule = adm

            factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setOptions(PeerConnectionFactory.Options())
                .createPeerConnectionFactory()
        }
    }

    fun startOutgoingCall(
        roomId: String,
        roomCode: String,
        roomName: String,
        peerPublicKey: String,
        peerUsername: String,
        peerPfpPath: String
    ) {
        if (_callState.value.status != VoiceCallStatus.IDLE) return

        resetIceBuffer()
        activeCallLogId = null
        VeilAppState.isCallActiveOrRinging = true

        _callState.value = ActiveCallState(
            status = VoiceCallStatus.OUTGOING_RINGING,
            roomId = roomId,
            roomCode = roomCode,
            roomName = roomName,
            peerPublicKey = peerPublicKey,
            peerUsername = peerUsername,
            peerPfpPath = peerPfpPath,
            isOutgoing = true,
            isMicMuted = false,
            isSpeakerOn = false,
            statusMessage = "Calling..."
        )

        // Persist outgoing call immediately so it always appears in the Calls tab
        scope.launch(Dispatchers.IO) {
            activeCallLogId = runCatching {
                onRecordCallHistory(
                    roomId,
                    roomCode,
                    peerPublicKey,
                    peerUsername,
                    peerPfpPath,
                    CallDirection.OUTGOING,
                    0L
                )
            }.getOrNull()
        }

        requestVoiceAudioFocus()
        configureVoiceAudioMode(speakerOn = false)
        setupAudioPeerConnection(roomId, roomCode, peerPublicKey)
        startRingingTimeout(isOutgoing = true)

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        val payload = JSONObject().put("sdp", desc.description).toString()
                        sendSignal(roomId, roomCode, peerPublicKey, SIGNAL_OFFER, payload)
                    }
                }, desc)
            }
        }, constraints)
    }

    fun handleIncomingSignal(
        roomId: String,
        roomCode: String,
        roomName: String,
        signal: RemoteCallSignal,
        peerUsername: String,
        peerPfpPath: String,
        isPeerMuted: Boolean,
        isPeerBlocked: Boolean
    ) {
        synchronized(processedSignalIds) {
            if (signal.id in processedSignalIds) return
            processedSignalIds.add(signal.id)
            if (processedSignalIds.size > 500) {
                val it = processedSignalIds.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
        }
        VeilAppState.markSignalSeen(signal.id)

        if (isPeerBlocked) return

        // Ignore very old OFFER rows that predate this session
        val ageMs = System.currentTimeMillis() - signal.createdAt
        if (signal.signalType == SIGNAL_OFFER && ageMs > 55_000L) return

        val decrypted = crypto.decryptSignalFromPeer(
            peerPublicKeyB64 = signal.callerPublicKey,
            roomCode = roomCode,
            packedPayload = signal.encryptedPayload
        ) ?: return

        val json = runCatching { JSONObject(decrypted) }.getOrNull() ?: return

        when (signal.signalType) {
            SIGNAL_OFFER -> {
                if (_callState.value.status != VoiceCallStatus.IDLE) return
                val offerSdp = json.optString("sdp")
                if (offerSdp.isBlank()) return

                resetIceBuffer()
                activeCallLogId = null
                VeilAppState.isCallActiveOrRinging = true

                _callState.value = ActiveCallState(
                    status = VoiceCallStatus.INCOMING_RINGING,
                    roomId = roomId,
                    roomCode = roomCode,
                    roomName = roomName,
                    peerPublicKey = signal.callerPublicKey,
                    peerUsername = peerUsername,
                    peerPfpPath = peerPfpPath,
                    isOutgoing = false,
                    isMicMuted = false,
                    isSpeakerOn = false,
                    pendingOfferSdp = offerSdp,
                    statusMessage = "Incoming encrypted voice call"
                )
                startRingingTimeout(isOutgoing = false)

                if (!isPeerMuted) {
                    val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val screenInteractive = pm?.isInteractive ?: true
                    startRingingFeedback()
                    if (!VeilAppState.isForeground || !screenInteractive) {
                        notificationHelper.showIncomingCallNotification(
                            roomId = roomId,
                            roomCode = roomCode,
                            callerPublicKey = signal.callerPublicKey,
                            callerUsername = peerUsername,
                            callerPfpPath = peerPfpPath,
                            roomName = roomName,
                            offerSdp = offerSdp
                        )
                    }
                }
            }

            SIGNAL_ANSWER -> {
                val current = _callState.value
                if (current.peerPublicKey != signal.callerPublicKey) return
                if (current.status != VoiceCallStatus.OUTGOING_RINGING && current.status != VoiceCallStatus.CONNECTING) return
                val answerSdp = json.optString("sdp")
                if (answerSdp.isBlank()) return

                ringTimeoutJob?.cancel()
                ringTimeoutJob = null
                _callState.value = current.copy(
                    status = VoiceCallStatus.CONNECTING,
                    statusMessage = "Establishing Encrypted Audio..."
                )

                peerConnection?.setRemoteDescription(
                    object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            isRemoteDescriptionSet = true
                            drainPendingRemoteIceCandidates()
                        }
                    },
                    SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                )
            }

            SIGNAL_ICE -> {
                val current = _callState.value
                if (current.status == VoiceCallStatus.IDLE) return
                if (current.peerPublicKey != signal.callerPublicKey) return
                val sdpMid = json.optString("sdpMid")
                val mLine = json.optInt("sdpMLineIndex", 0)
                val cand = json.optString("candidate")
                if (cand.isBlank()) return

                val ice = IceCandidate(sdpMid, mLine, cand)
                synchronized(pendingRemoteIceCandidates) {
                    val pc = peerConnection
                    if (pc != null && isRemoteDescriptionSet) {
                        pc.addIceCandidate(ice)
                    } else {
                        pendingRemoteIceCandidates.add(ice)
                    }
                }
            }

            SIGNAL_HANGUP, SIGNAL_DECLINE -> {
                val current = _callState.value
                if (current.peerPublicKey == signal.callerPublicKey && current.status != VoiceCallStatus.IDLE) {
                    IncomingCallBridge.onRemoteCancelled?.invoke()
                    finishCall(
                        directionOverride = if (!current.isOutgoing && current.status == VoiceCallStatus.INCOMING_RINGING) {
                            CallDirection.MISSED
                        } else null,
                        sendRemoteHangup = false
                    )
                }
            }
        }
    }

    fun acceptIncomingCall(
        roomIdOverride: String? = null,
        roomCodeOverride: String? = null,
        roomNameOverride: String? = null,
        callerPublicKeyOverride: String? = null,
        callerUsernameOverride: String? = null,
        callerPfpPathOverride: String? = null,
        offerSdpOverride: String? = null
    ) {
        stopRingingFeedback()
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
        notificationHelper.cancelIncomingCallNotification()

        val current = _callState.value
        val roomId = roomIdOverride?.ifBlank { current.roomId } ?: current.roomId
        val roomCode = roomCodeOverride?.ifBlank { current.roomCode } ?: current.roomCode
        val roomName = roomNameOverride?.ifBlank { current.roomName } ?: current.roomName
        val peerKey = callerPublicKeyOverride?.ifBlank { current.peerPublicKey } ?: current.peerPublicKey
        val peerName = callerUsernameOverride?.ifBlank { current.peerUsername } ?: current.peerUsername
        val peerPfp = callerPfpPathOverride?.ifBlank { current.peerPfpPath } ?: current.peerPfpPath
        val offerSdp = offerSdpOverride?.ifBlank { current.pendingOfferSdp } ?: current.pendingOfferSdp

        if (roomId.isBlank() || peerKey.isBlank() || offerSdp.isBlank()) return

        VeilAppState.isCallActiveOrRinging = true
        isRemoteDescriptionSet = false

        _callState.value = current.copy(
            status = VoiceCallStatus.CONNECTING,
            roomId = roomId,
            roomCode = roomCode,
            roomName = roomName,
            peerPublicKey = peerKey,
            peerUsername = peerName,
            peerPfpPath = peerPfp,
            isOutgoing = false,
            isMicMuted = false,
            isSpeakerOn = false,
            pendingOfferSdp = offerSdp,
            statusMessage = "Establishing Encrypted Audio..."
        )

        // Immediately record answered INCOMING call in Room
        scope.launch(Dispatchers.IO) {
            activeCallLogId = runCatching {
                onRecordCallHistory(
                    roomId,
                    roomCode,
                    peerKey,
                    peerName,
                    peerPfp,
                    CallDirection.INCOMING,
                    0L
                )
            }.getOrNull()
        }

        requestVoiceAudioFocus()
        configureVoiceAudioMode(speakerOn = false)
        setupAudioPeerConnection(roomId, roomCode, peerKey)

        peerConnection?.setRemoteDescription(
            object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    isRemoteDescriptionSet = true
                    drainPendingRemoteIceCandidates()

                    val constraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                    }
                    peerConnection?.createAnswer(object : SimpleSdpObserver() {
                        override fun onCreateSuccess(desc: SessionDescription?) {
                            if (desc == null) return
                            peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                                override fun onSetSuccess() {
                                    val payload = JSONObject().put("sdp", desc.description).toString()
                                    sendSignal(roomId, roomCode, peerKey, SIGNAL_ANSWER, payload)
                                }
                            }, desc)
                        }
                    }, constraints)
                }
            },
            SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        )
    }

    fun declineIncomingCall() {
        val current = _callState.value
        if (current.roomId.isNotBlank() && current.peerPublicKey.isNotBlank()) {
            sendSignal(current.roomId, current.roomCode, current.peerPublicKey, SIGNAL_DECLINE, "{}")
        }
        IncomingCallBridge.onRemoteCancelled?.invoke()
        finishCall(directionOverride = CallDirection.MISSED, sendRemoteHangup = false)
    }

    fun endActiveCall() {
        finishCall(directionOverride = null, sendRemoteHangup = true)
    }

    fun toggleMicMute() {
        val nextMuted = !_callState.value.isMicMuted
        localAudioTrack?.setEnabled(!nextMuted)
        audioDeviceModule?.setMicrophoneMute(nextMuted)
        runCatching { audioManager.isMicrophoneMute = nextMuted }
        _callState.value = _callState.value.copy(isMicMuted = nextMuted)
    }

    fun toggleSpeaker() {
        val nextSpeaker = !_callState.value.isSpeakerOn
        configureVoiceAudioMode(speakerOn = nextSpeaker)
        _callState.value = _callState.value.copy(isSpeakerOn = nextSpeaker)
    }

    private fun setupAudioPeerConnection(roomId: String, roomCode: String, peerPublicKey: String) {
        ensureWebRtcFactory()
        val pcf = factory ?: return

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }

        runCatching { localAudioTrack?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        localAudioTrack = null
        audioSource = null

        peerConnection = pcf.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                val payload = JSONObject().apply {
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("candidate", candidate.sdp)
                }.toString()
                sendSignal(roomId, roomCode, peerPublicKey, SIGNAL_ICE, payload)
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        scope.launch(Dispatchers.Main) {
                            transitionToConnected()
                        }
                    }
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> {
                        scope.launch(Dispatchers.Main) {
                            if (_callState.value.status == VoiceCallStatus.CONNECTED ||
                                _callState.value.status == VoiceCallStatus.CONNECTING
                            ) {
                                finishCall(directionOverride = null, sendRemoteHangup = false)
                            }
                        }
                    }
                    else -> Unit
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        scope.launch(Dispatchers.Main) {
                            transitionToConnected()
                        }
                    }
                    PeerConnection.PeerConnectionState.FAILED,
                    PeerConnection.PeerConnectionState.CLOSED -> {
                        scope.launch(Dispatchers.Main) {
                            if (_callState.value.status == VoiceCallStatus.CONNECTED) {
                                finishCall(directionOverride = null, sendRemoteHangup = false)
                            }
                        }
                    }
                    else -> Unit
                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is AudioTrack) {
                    track.setEnabled(true)
                    track.setVolume(1.0)
                }
                streams?.forEach { stream ->
                    stream.audioTracks?.forEach { audioTrack ->
                        audioTrack.setEnabled(true)
                        audioTrack.setVolume(1.0)
                    }
                }
            }

            override fun onAddStream(stream: MediaStream?) {
                stream?.audioTracks?.forEach { audioTrack ->
                    audioTrack.setEnabled(true)
                    audioTrack.setVolume(1.0)
                }
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionReceivingChange(p0: Boolean) = Unit
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) = Unit
            override fun onRemoveStream(p0: MediaStream?) = Unit
            override fun onDataChannel(p0: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
        })

        // Audio-Only Track (Strictly NO Video Track)
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        audioSource = pcf.createAudioSource(audioConstraints)
        localAudioTrack = pcf.createAudioTrack("veil_audio_track_${UUID.randomUUID()}", audioSource).apply {
            setEnabled(true)
            setVolume(1.0)
        }
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("veil_audio_stream"))
        }
    }

    private fun drainPendingRemoteIceCandidates() {
        val queued: List<IceCandidate>
        synchronized(pendingRemoteIceCandidates) {
            if (pendingRemoteIceCandidates.isEmpty()) return
            queued = pendingRemoteIceCandidates.toList()
            pendingRemoteIceCandidates.clear()
        }
        val pc = peerConnection ?: return
        queued.forEach { candidate ->
            runCatching { pc.addIceCandidate(candidate) }
        }
    }

    private fun resetIceBuffer() {
        isRemoteDescriptionSet = false
        synchronized(pendingRemoteIceCandidates) {
            pendingRemoteIceCandidates.clear()
        }
    }

    private fun startRingingTimeout(isOutgoing: Boolean) {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = scope.launch(Dispatchers.Main) {
            delay(RING_TIMEOUT_MS)
            val status = _callState.value.status
            if (status == VoiceCallStatus.OUTGOING_RINGING || status == VoiceCallStatus.INCOMING_RINGING) {
                IncomingCallBridge.onRemoteCancelled?.invoke()
                finishCall(
                    directionOverride = if (isOutgoing) CallDirection.OUTGOING else CallDirection.MISSED,
                    sendRemoteHangup = isOutgoing
                )
            }
        }
    }

    private fun transitionToConnected() {
        stopRingingFeedback()
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
        notificationHelper.cancelIncomingCallNotification()

        if (_callState.value.status == VoiceCallStatus.CONNECTED) return
        if (_callState.value.status == VoiceCallStatus.IDLE) return

        // Re-assert voice audio routing once ICE connects
        configureVoiceAudioMode(speakerOn = _callState.value.isSpeakerOn)
        localAudioTrack?.setEnabled(!_callState.value.isMicMuted)

        connectedAtMs = System.currentTimeMillis()
        _callState.value = _callState.value.copy(
            status = VoiceCallStatus.CONNECTED,
            elapsedSeconds = 0L,
            statusMessage = "End-to-End Encrypted Voice"
        )
        timerJob?.cancel()
        timerJob = scope.launch(Dispatchers.Main) {
            while (isActive && _callState.value.status == VoiceCallStatus.CONNECTED) {
                val secs = ((System.currentTimeMillis() - connectedAtMs) / 1000L).coerceAtLeast(0L)
                _callState.value = _callState.value.copy(elapsedSeconds = secs)
                delay(1000L)
            }
        }
    }

    private fun finishCall(directionOverride: CallDirection?, sendRemoteHangup: Boolean) {
        stopRingingFeedback()
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
        notificationHelper.cancelIncomingCallNotification()
        timerJob?.cancel()
        timerJob = null

        val snapshot = _callState.value
        val finalDurationSec = if (snapshot.status == VoiceCallStatus.CONNECTED && connectedAtMs > 0L) {
            ((System.currentTimeMillis() - connectedAtMs) / 1000L).coerceAtLeast(snapshot.elapsedSeconds)
        } else {
            snapshot.elapsedSeconds
        }
        connectedAtMs = 0L
        VeilAppState.isCallActiveOrRinging = false
        resetIceBuffer()

        if (sendRemoteHangup && snapshot.roomId.isNotBlank() && snapshot.peerPublicKey.isNotBlank()) {
            sendSignal(snapshot.roomId, snapshot.roomCode, snapshot.peerPublicKey, SIGNAL_HANGUP, "{}")
        }

        runCatching { localAudioTrack?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        localAudioTrack = null
        audioSource = null
        peerConnection = null
        restoreNormalAudioMode()
        abandonVoiceAudioFocus()

        val logId = activeCallLogId
        activeCallLogId = null

        if (snapshot.roomId.isNotBlank() && snapshot.peerPublicKey.isNotBlank()) {
            val direction = directionOverride ?: if (snapshot.isOutgoing) {
                CallDirection.OUTGOING
            } else if (snapshot.status == VoiceCallStatus.CONNECTED) {
                CallDirection.INCOMING
            } else {
                CallDirection.MISSED
            }
            scope.launch(Dispatchers.IO) {
                if (!logId.isNullOrBlank()) {
                    runCatching { onUpdateCallHistory(logId, direction, finalDurationSec) }
                } else {
                    runCatching {
                        onRecordCallHistory(
                            snapshot.roomId,
                            snapshot.roomCode,
                            snapshot.peerPublicKey,
                            snapshot.peerUsername,
                            snapshot.peerPfpPath,
                            direction,
                            finalDurationSec
                        )
                    }
                }
            }
        }

        _callState.value = ActiveCallState(status = VoiceCallStatus.IDLE)
    }

    private fun sendSignal(
        roomId: String,
        roomCode: String,
        peerPublicKey: String,
        signalType: String,
        plaintextJson: String
    ) {
        val encrypted = crypto.encryptSignalForPeer(peerPublicKey, roomCode, plaintextJson)
        val signal = RemoteCallSignal(
            id = UUID.randomUUID().toString(),
            roomCode = roomCode,
            callerPublicKey = crypto.getOrCreateIdentity().publicKeyBase64,
            calleePublicKey = peerPublicKey,
            signalType = signalType,
            encryptedPayload = encrypted,
            createdAt = System.currentTimeMillis()
        )
        outboundSignalChannel.trySend(QueuedSignal(roomId, signal))
    }

    private fun requestVoiceAudioFocus() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        }
    }

    private fun abandonVoiceAudioFocus() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        }
    }

    private fun configureVoiceAudioMode(speakerOn: Boolean) {
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = _callState.value.isMicMuted
            audioDeviceModule?.setMicrophoneMute(_callState.value.isMicMuted)
            audioDeviceModule?.setSpeakerMute(false)

            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = speakerOn

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (speakerOn) {
                    val speaker = audioManager.availableCommunicationDevices
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) {
                        audioManager.setCommunicationDevice(speaker)
                    }
                } else {
                    val earpiece = audioManager.availableCommunicationDevices
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    if (earpiece != null) {
                        audioManager.setCommunicationDevice(earpiece)
                    } else {
                        audioManager.clearCommunicationDevice()
                    }
                }
            }
        }
    }

    private fun restoreNormalAudioMode() {
        runCatching {
            audioManager.isMicrophoneMute = false
            audioDeviceModule?.setMicrophoneMute(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    private fun startRingingFeedback() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            activeRingtone = RingtoneManager.getRingtone(appContext, uri)?.also { it.play() }
        }
        runCatching {
            val pattern = longArrayOf(0, 500, 500, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                val v = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            }
        }
    }

    private fun stopRingingFeedback() {
        runCatching { activeRingtone?.stop() }
        activeRingtone = null
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.cancel()
            } else {
                @Suppress("DEPRECATION")
                val v = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.cancel()
            }
        }
    }

    fun release() {
        finishCall(directionOverride = null, sendRemoteHangup = false)
        outboundSignalChannel.close()
        runCatching { factory?.dispose() }
        runCatching { audioDeviceModule?.release() }
        factory = null
        audioDeviceModule = null
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }

    companion object {
        const val SIGNAL_OFFER = "OFFER"
        const val SIGNAL_ANSWER = "ANSWER"
        const val SIGNAL_ICE = "ICE"
        const val SIGNAL_HANGUP = "HANGUP"
        const val SIGNAL_DECLINE = "DECLINE"
        private const val RING_TIMEOUT_MS = 45_000L
    }
}
