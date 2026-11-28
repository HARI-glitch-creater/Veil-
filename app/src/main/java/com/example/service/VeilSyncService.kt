package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.call.IncomingCallBridge
import com.example.call.VoiceCallController
import com.example.data.VeilRepository
import com.example.data.local.CallDirection
import com.example.data.network.RemoteCallSignal
import com.example.notifications.VeilNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Shared process-level state linking MainActivity, VoiceCallController,
 * IncomingCallActivity, and VeilSyncService so foreground and background
 * polling coordinate cleanly without duplicating alerts.
 */
object VeilAppState {
    @Volatile
    var isForeground: Boolean = false

    @Volatile
    var isCallActiveOrRinging: Boolean = false

    private val seenBackgroundSignalIds = LinkedHashSet<String>()

    @Synchronized
    fun markSignalSeen(signalId: String): Boolean {
        if (signalId in seenBackgroundSignalIds) return false
        seenBackgroundSignalIds.add(signalId)
        if (seenBackgroundSignalIds.size > 500) {
            val it = seenBackgroundSignalIds.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }
        return true
    }
}

private data class PendingBackgroundOffer(
    val roomId: String,
    val roomCode: String,
    val callerPublicKey: String,
    val callerUsername: String,
    val callerPfpPath: String,
    val receivedAtMs: Long
)

/**
 * Lightweight persistent foreground service that keeps polling `veil_call_signals`
 * and `veil_messages` even when Veil is minimized or swiped out of recents.
 * Fires a high-priority CallStyle + fullScreenIntent notification on channel
 * `veil_incoming_calls` whenever an encrypted OFFER arrives while the app is in
 * the background or the screen is off.
 */
class VeilSyncService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private lateinit var repository: VeilRepository
    private lateinit var notificationHelper: VeilNotificationHelper

    private var notifCounter = 4000
    private var pendingBgOffer: PendingBackgroundOffer? = null

    override fun onCreate() {
        super.onCreate()
        repository = VeilRepository(applicationContext)
        notificationHelper = VeilNotificationHelper(applicationContext)
        notificationHelper.ensureChannels()
        startForegroundSafely()
        startSyncLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()
        if (pollJob == null || pollJob?.isActive != true) {
            startSyncLoop()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Schedule a quick restart so swiping Veil from recents preserves background call alerts
        runCatching {
            val restartIntent = Intent(applicationContext, VeilSyncService::class.java)
            val pending = PendingIntent.getService(
                applicationContext,
                REQ_SERVICE_RESTART,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1_500L,
                pending
            )
        }
    }

    override fun onDestroy() {
        pollJob?.cancel()
        pollJob = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely() {
        val notification = notificationHelper.buildSyncServiceNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Try combined dataSync|phoneCall first; fall back cleanly to dataSync if OEM restricts idle phoneCall
            val startedCombined = runCatching {
                startForeground(
                    VeilNotificationHelper.SYNC_SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            }.isSuccess
            if (!startedCombined) {
                runCatching {
                    startForeground(
                        VeilNotificationHelper.SYNC_SERVICE_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                startForeground(
                    VeilNotificationHelper.SYNC_SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            }
        } else {
            runCatching {
                startForeground(VeilNotificationHelper.SYNC_SERVICE_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun startSyncLoop() {
        pollJob?.cancel()
        pollJob = serviceScope.launch {
            var tick = 0
            while (isActive) {
                runCatching {
                    val hasBoundProfile = repository.getUserProfile() != null
                    if (hasBoundProfile) {
                        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                        val screenInteractive = pm?.isInteractive ?: true
                        val needsBackgroundHandling = !VeilAppState.isForeground || !screenInteractive

                        // Expire unanswered background offer after 45s and record MISSED call
                        val currentOffer = pendingBgOffer
                        if (currentOffer != null && System.currentTimeMillis() - currentOffer.receivedAtMs > OFFER_TIMEOUT_MS) {
                            pendingBgOffer = null
                            notificationHelper.cancelIncomingCallNotification()
                            IncomingCallBridge.onRemoteCancelled?.invoke()
                            if (!VeilAppState.isCallActiveOrRinging) {
                                repository.recordCallHistory(
                                    roomId = currentOffer.roomId,
                                    roomCode = currentOffer.roomCode,
                                    peerPublicKey = currentOffer.callerPublicKey,
                                    peerUsername = currentOffer.callerUsername,
                                    peerPfpPath = currentOffer.callerPfpPath,
                                    direction = CallDirection.MISSED,
                                    durationSec = 0L
                                )
                            }
                        }

                        if (needsBackgroundHandling) {
                            val rooms = repository.getAllRooms()
                            val doFullMessageSync = (tick % 4 == 0)
                            for (room in rooms) {
                                if (doFullMessageSync) {
                                    repository.syncRoom(
                                        roomId = room.id,
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
                                        onIncomingCallSignal = { callRoom, sig, peer ->
                                            handleBackgroundCallSignal(
                                                roomId = callRoom.id,
                                                roomCode = callRoom.veilCode,
                                                roomName = callRoom.roomName,
                                                signal = sig,
                                                peerUsername = peer?.username ?: "Veil Peer",
                                                peerPfpPath = peer?.localPfpPath.orEmpty(),
                                                isMuted = peer?.isMuted == true,
                                                isBlocked = peer?.isBlocked == true
                                            )
                                        }
                                    )
                                } else {
                                    repository.pollCallSignals(room.id) { callRoom, sig, peer ->
                                        handleBackgroundCallSignal(
                                            roomId = callRoom.id,
                                            roomCode = callRoom.veilCode,
                                            roomName = callRoom.roomName,
                                            signal = sig,
                                            peerUsername = peer?.username ?: "Veil Peer",
                                            peerPfpPath = peer?.localPfpPath.orEmpty(),
                                            isMuted = peer?.isMuted == true,
                                            isBlocked = peer?.isBlocked == true
                                        )
                                    }
                                }
                            }
                        } else {
                            // MainActivity is in foreground; clear stale background offer tracking
                            pendingBgOffer = null
                        }
                    }
                }
                tick++
                delay(1_500L)
            }
        }
    }

    private fun handleBackgroundCallSignal(
        roomId: String,
        roomCode: String,
        roomName: String,
        signal: RemoteCallSignal,
        peerUsername: String,
        peerPfpPath: String,
        isMuted: Boolean,
        isBlocked: Boolean
    ) {
        if (isBlocked) return
        if (!VeilAppState.markSignalSeen(signal.id)) return

        // Ignore signals older than 55 seconds so stale server rows never re-ring
        val ageMs = System.currentTimeMillis() - signal.createdAt
        if (ageMs > 55_000L) return

        when (signal.signalType) {
            VoiceCallController.SIGNAL_OFFER -> {
                if (VeilAppState.isCallActiveOrRinging) return
                val decrypted = repository.crypto.decryptSignalFromPeer(
                    peerPublicKeyB64 = signal.callerPublicKey,
                    roomCode = roomCode,
                    packedPayload = signal.encryptedPayload
                ) ?: return
                val offerSdp = runCatching { JSONObject(decrypted).optString("sdp") }.getOrNull().orEmpty()
                if (offerSdp.isBlank()) return

                pendingBgOffer = PendingBackgroundOffer(
                    roomId = roomId,
                    roomCode = roomCode,
                    callerPublicKey = signal.callerPublicKey,
                    callerUsername = peerUsername,
                    callerPfpPath = peerPfpPath,
                    receivedAtMs = System.currentTimeMillis()
                )

                if (!isMuted) {
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

            VoiceCallController.SIGNAL_HANGUP, VoiceCallController.SIGNAL_DECLINE -> {
                val activeOffer = pendingBgOffer
                if (activeOffer != null && activeOffer.callerPublicKey == signal.callerPublicKey) {
                    pendingBgOffer = null
                    notificationHelper.cancelIncomingCallNotification()
                    IncomingCallBridge.onRemoteCancelled?.invoke()
                    if (!VeilAppState.isCallActiveOrRinging) {
                        serviceScope.launch {
                            repository.recordCallHistory(
                                roomId = activeOffer.roomId,
                                roomCode = activeOffer.roomCode,
                                peerPublicKey = activeOffer.callerPublicKey,
                                peerUsername = activeOffer.callerUsername,
                                peerPfpPath = activeOffer.callerPfpPath,
                                direction = CallDirection.MISSED,
                                durationSec = 0L
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val REQ_SERVICE_RESTART = 9201
        private const val OFFER_TIMEOUT_MS = 45_000L

        fun start(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, VeilSyncService::class.java)
            runCatching {
                ContextCompat.startForegroundService(appCtx, intent)
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            runCatching {
                appCtx.stopService(Intent(appCtx, VeilSyncService::class.java))
            }
        }
    }
}
