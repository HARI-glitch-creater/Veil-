package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.call.IncomingCallBridge
import com.example.call.VoiceCallController
import com.example.data.VeilRepository
import com.example.data.local.CallDirection
import com.example.data.network.RemoteCallSignal
import com.example.notifications.VeilNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Handles Decline actions directly from the incoming call notification or lock screen
 * even when MainActivity is not running — cancels notification, transmits encrypted
 * DECLINE signal, and persists a MISSED call entry in local Room storage.
 */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != VeilNotificationHelper.ACTION_DECLINE_CALL) return

        val appCtx = context.applicationContext
        VeilNotificationHelper(appCtx).cancelIncomingCallNotification()
        IncomingCallBridge.onRemoteCancelled?.invoke()

        val roomId = intent.getStringExtra(VeilNotificationHelper.EXTRA_ROOM_ID).orEmpty()
        val roomCode = intent.getStringExtra(VeilNotificationHelper.EXTRA_ROOM_CODE).orEmpty()
        val callerPub = intent.getStringExtra(VeilNotificationHelper.EXTRA_CALLER_PUBLIC_KEY).orEmpty()
        val callerName = intent.getStringExtra(VeilNotificationHelper.EXTRA_CALLER_USERNAME).orEmpty()
            .ifBlank { "Veil Peer" }
        val callerPfp = intent.getStringExtra(VeilNotificationHelper.EXTRA_CALLER_PFP_PATH).orEmpty()

        val activeBridge = IncomingCallBridge.onDeclineFromLockScreen
        if (activeBridge != null && VeilAppState.isCallActiveOrRinging) {
            activeBridge.invoke(roomId, roomCode, callerPub, callerName)
            return
        }

        if (roomId.isBlank() || callerPub.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = VeilRepository(appCtx)
                val encrypted = repo.crypto.encryptSignalForPeer(callerPub, roomCode, "{}")
                val signal = RemoteCallSignal(
                    id = UUID.randomUUID().toString(),
                    roomCode = roomCode,
                    callerPublicKey = repo.crypto.getOrCreateIdentity().publicKeyBase64,
                    calleePublicKey = callerPub,
                    signalType = VoiceCallController.SIGNAL_DECLINE,
                    encryptedPayload = encrypted,
                    createdAt = System.currentTimeMillis()
                )
                runCatching { repo.sendEncryptedCallSignal(roomId, signal) }
                runCatching {
                    repo.recordCallHistory(
                        roomId = roomId,
                        roomCode = roomCode,
                        peerPublicKey = callerPub,
                        peerUsername = callerName,
                        peerPfpPath = callerPfp,
                        direction = CallDirection.MISSED,
                        durationSec = 0L
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * Restarts VeilSyncService after device reboot or in-place app update if the user
 * has already bound a Veil profile on this phone.
 */
class VeilBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val appCtx = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = VeilRepository(appCtx)
                if (repo.getUserProfile() != null) {
                    VeilSyncService.start(appCtx)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
