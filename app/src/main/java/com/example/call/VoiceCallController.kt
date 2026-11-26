package com.example.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.crypto.VeilCryptoEngine
import com.example.data.local.CallDirection
import com.example.data.network.RemoteCallSignal
import com.example.notifications.VeilNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
}

/**
 * Strictly Audio-Only WebRTC Voice Call Controller.
 * Zero video tracks, zero video capturers, zero camera APIs.
 * All SDP offers, answers, and ICE candidates are encrypted end-to-end with
 * ECDH + HKDF-SHA256 + AES-256-GCM before transmission over veil_call_signals.
 */
class VoiceCallController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val crypto: VeilCryptoEngine,
    private val notificationHelper: VeilNotificationHelper,
    private val onSendEncryptedSignal: suspend (roomId: String, signal: RemoteCallSignal) -> Unit,
    private val onRecordCallHistory: suspend (
        roomId: String,
        peerPublicKey: String,
        peerUsername: String,
        peerPfpPath: String,
        direction: CallDirection,
        durationSec: Long
    ) -> Unit
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _callState = MutableStateFlow(ActiveCallState())
    val callState: StateFlow<ActiveCallState> = _callState.asStateFlow()

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var timerJob: Job? = null
    private var activeRingtone: Ringtone? = null
    private var connectedAtMs: Long = 0L

    private val processedSignalIds = LinkedHashSet<String>()

    private fun ensureWebRtcFactory() {
        if (factory != null) return
        runCatching {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            factory = PeerConnectionFactory.builder()
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

        _callState.value = ActiveCallState(
            status = VoiceCallStatus.OUTGOING_RINGING,
            roomId = roomId,
            roomCode = roomCode,
            roomName = roomName,
            peerPublicKey = peerPublicKey,
            peerUsername = peerUsername,
            peerPfpPath = peerPfpPath,
            isOutgoing = true,
            statusMessage = "Calling..."
        )

        configureVoiceAudioMode(speakerOn = false)
        setupAudioPeerConnection(roomId, roomCode, peerPublicKey)

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                val payload = JSONObject().put("sdp", desc.description).toString()
                sendSignal(roomId, roomCode, peerPublicKey, SIGNAL_OFFER, payload)
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
        if (signal.id in processedSignalIds) return
        processedSignalIds.add(signal.id)
        if (processedSignalIds.size > 400) {
            processedSignalIds.iterator().let { if (it.hasNext()) { it.next(); it.remove() } }
        }

        if (isPeerBlocked) return

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
                _callState.value = ActiveCallState(
                    status = VoiceCallStatus.INCOMING_RINGING,
                    roomId = roomId,
                    roomCode = roomCode,
                    roomName = roomName,
                    peerPublicKey = signal.callerPublicKey,
                    peerUsername = peerUsername,
                    peerPfpPath = peerPfpPath,
                    isOutgoing = false,
                    pendingOfferSdp = offerSdp,
                    statusMessage = "Incoming encrypted voice call"
                )
                if (!isPeerMuted) {
                    startRingingFeedback()
                    notificationHelper.showIncomingCallNotification(
                        roomId = roomId,
                        roomCode = roomCode,
                        callerPublicKey = signal.callerPublicKey,
                        callerUsername = peerUsername,
                        roomName = roomName,
                        offerSdp = offerSdp
                    )
                }
            }

            SIGNAL_ANSWER -> {
                val current = _callState.value
                if (current.peerPublicKey != signal.callerPublicKey) return
                val answerSdp = json.optString("sdp")
                if (answerSdp.isBlank()) return
                peerConnection?.setRemoteDescription(
                    SimpleSdpObserver(),
                    SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                )
                transitionToConnected()
            }

            SIGNAL_ICE -> {
                val current = _callState.value
                if (current.peerPublicKey != signal.callerPublicKey) return
                val sdpMid = json.optString("sdpMid")
                val mLine = json.optInt("sdpMLineIndex", 0)
                val cand = json.optString("candidate")
                if (cand.isNotBlank()) {
                    peerConnection?.addIceCandidate(IceCandidate(sdpMid, mLine, cand))
                }
            }

            SIGNAL_HANGUP, SIGNAL_DECLINE -> {
                val current = _callState.value
                if (current.peerPublicKey == signal.callerPublicKey && current.status != VoiceCallStatus.IDLE) {
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
        offerSdpOverride: String? = null
    ) {
        stopRingingFeedback()
        notificationHelper.cancelIncomingCallNotification()

        val current = _callState.value
        val roomId = roomIdOverride?.ifBlank { current.roomId } ?: current.roomId
        val roomCode = roomCodeOverride?.ifBlank { current.roomCode } ?: current.roomCode
        val roomName = roomNameOverride?.ifBlank { current.roomName } ?: current.roomName
        val peerKey = callerPublicKeyOverride?.ifBlank { current.peerPublicKey } ?: current.peerPublicKey
        val peerName = callerUsernameOverride?.ifBlank { current.peerUsername } ?: current.peerUsername
        val offerSdp = offerSdpOverride?.ifBlank { current.pendingOfferSdp } ?: current.pendingOfferSdp

        if (roomId.isBlank() || peerKey.isBlank() || offerSdp.isBlank()) return

        _callState.value = current.copy(
            status = VoiceCallStatus.CONNECTING,
            roomId = roomId,
            roomCode = roomCode,
            roomName = roomName,
            peerPublicKey = peerKey,
            peerUsername = peerName,
            isOutgoing = false,
            pendingOfferSdp = offerSdp,
            statusMessage = "Connecting encrypted audio..."
        )

        configureVoiceAudioMode(speakerOn = false)
        setupAudioPeerConnection(roomId, roomCode, peerKey)

        peerConnection?.setRemoteDescription(
            object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    val constraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                    }
                    peerConnection?.createAnswer(object : SimpleSdpObserver() {
                        override fun onCreateSuccess(desc: SessionDescription?) {
                            if (desc == null) return
                            peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                            val payload = JSONObject().put("sdp", desc.description).toString()
                            sendSignal(roomId, roomCode, peerKey, SIGNAL_ANSWER, payload)
                            transitionToConnected()
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
        finishCall(directionOverride = CallDirection.MISSED, sendRemoteHangup = false)
    }

    fun endActiveCall() {
        finishCall(directionOverride = null, sendRemoteHangup = true)
    }

    fun toggleMicMute() {
        val nextMuted = !_callState.value.isMicMuted
        localAudioTrack?.setEnabled(!nextMuted)
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
        }

        peerConnection?.close()
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
                    PeerConnection.IceConnectionState.COMPLETED -> transitionToConnected()
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> {
                        if (_callState.value.status == VoiceCallStatus.CONNECTED) {
                            finishCall(directionOverride = null, sendRemoteHangup = false)
                        }
                    }
                    else -> Unit
                }
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionReceivingChange(p0: Boolean) = Unit
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) = Unit
            override fun onAddStream(p0: MediaStream?) = Unit
            override fun onRemoveStream(p0: MediaStream?) = Unit
            override fun onDataChannel(p0: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) = Unit
        })

        // Audio-Only Track (Strictly NO Video Track)
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        audioSource = pcf.createAudioSource(audioConstraints)
        localAudioTrack = pcf.createAudioTrack("veil_audio_track_${UUID.randomUUID()}", audioSource).apply {
            setEnabled(true)
        }
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("veil_audio_stream"))
        }
    }

    private fun transitionToConnected() {
        stopRingingFeedback()
        notificationHelper.cancelIncomingCallNotification()
        if (_callState.value.status == VoiceCallStatus.CONNECTED) return
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
        notificationHelper.cancelIncomingCallNotification()
        timerJob?.cancel()
        timerJob = null

        val snapshot = _callState.value
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

        if (snapshot.roomId.isNotBlank() && snapshot.peerPublicKey.isNotBlank()) {
            val direction = directionOverride ?: if (snapshot.isOutgoing) {
                CallDirection.OUTGOING
            } else if (snapshot.status == VoiceCallStatus.CONNECTED) {
                CallDirection.INCOMING
            } else {
                CallDirection.MISSED
            }
            scope.launch(Dispatchers.IO) {
                onRecordCallHistory(
                    snapshot.roomId,
                    snapshot.peerPublicKey,
                    snapshot.peerUsername,
                    snapshot.peerPfpPath,
                    direction,
                    snapshot.elapsedSeconds
                )
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
        scope.launch(Dispatchers.IO) {
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
            runCatching { onSendEncryptedSignal(roomId, signal) }
        }
    }

    private fun configureVoiceAudioMode(speakerOn: Boolean) {
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (speakerOn) {
                    val speaker = audioManager.availableCommunicationDevices
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) audioManager.setCommunicationDevice(speaker)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = speakerOn
            }
        }
    }

    private fun restoreNormalAudioMode() {
        runCatching {
            audioManager.isMicrophoneMute = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
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
        runCatching { factory?.dispose() }
        factory = null
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
    }
}
