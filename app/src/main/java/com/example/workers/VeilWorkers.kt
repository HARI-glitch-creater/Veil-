package com.example.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.VeilRepository
import com.example.notifications.VeilNotificationHelper
import java.util.concurrent.TimeUnit

class CallHistoryRetentionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val repository = VeilRepository(applicationContext)
            repository.pruneExpiredCallHistory()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

class VeilBackgroundSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val repository = VeilRepository(applicationContext)
            val notificationHelper = VeilNotificationHelper(applicationContext)
            val rooms = repository.getAllRooms()
            var notifIndex = 2000
            for (room in rooms) {
                repository.syncRoom(
                    roomId = room.id,
                    onNewMessageAlert = { alert ->
                        notificationHelper.showMessageNotification(
                            notificationId = notifIndex++,
                            title = alert.title,
                            body = alert.body,
                            roomId = alert.roomId,
                            targetId = alert.targetId,
                            isCommunity = alert.isCommunity
                        )
                    },
                    onIncomingCallSignal = { callRoom, signal, peer ->
                        if (signal.signalType == "OFFER" && peer?.isBlocked != true && peer?.isMuted != true) {
                            val decrypted = repository.crypto.decryptSignalFromPeer(
                                signal.callerPublicKey,
                                callRoom.veilCode,
                                signal.encryptedPayload
                            )
                            val sdp = decrypted?.let {
                                runCatching { org.json.JSONObject(it).optString("sdp") }.getOrNull()
                            }.orEmpty()
                            if (sdp.isNotBlank()) {
                                notificationHelper.showIncomingCallNotification(
                                    roomId = callRoom.id,
                                    roomCode = callRoom.veilCode,
                                    callerPublicKey = signal.callerPublicKey,
                                    callerUsername = peer?.username ?: "Veil Peer",
                                    roomName = callRoom.roomName,
                                    offerSdp = sdp
                                )
                            }
                        }
                    }
                )
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

object VeilWorkScheduler {
    private const val WORK_CALL_RETENTION = "veil_call_history_90d_retention"
    private const val WORK_BACKGROUND_SYNC = "veil_background_sync_v1"

    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)

        val retentionRequest = PeriodicWorkRequestBuilder<CallHistoryRetentionWorker>(
            24,
            TimeUnit.HOURS
        ).build()
        wm.enqueueUniquePeriodicWork(
            WORK_CALL_RETENTION,
            ExistingPeriodicWorkPolicy.KEEP,
            retentionRequest
        )

        val syncConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val syncRequest = PeriodicWorkRequestBuilder<VeilBackgroundSyncWorker>(
            15,
            TimeUnit.MINUTES
        )
            .setConstraints(syncConstraints)
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK_BACKGROUND_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
