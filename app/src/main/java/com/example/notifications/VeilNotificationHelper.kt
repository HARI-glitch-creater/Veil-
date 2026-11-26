package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.call.IncomingCallActivity

class VeilNotificationHelper(private val context: Context) {

    private val appContext = context.applicationContext

    fun ensureChannels() {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val messageChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Encrypted Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "End-to-end encrypted direct and community messages"
            enableVibration(true)
        }

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ringAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val callChannel = NotificationChannel(
            CHANNEL_CALLS,
            "Encrypted Voice Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming end-to-end encrypted voice call alerts"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600, 400)
            setSound(ringtoneUri, ringAttributes)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        nm.createNotificationChannel(messageChannel)
        nm.createNotificationChannel(callChannel)
    }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun showMessageNotification(
        notificationId: Int,
        title: String,
        body: String,
        roomId: String,
        targetId: String = "",
        isCommunity: Boolean = false
    ) {
        if (!hasNotificationPermission()) return
        ensureChannels()

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROOM_ID, roomId)
            putExtra(EXTRA_TARGET_ID, targetId)
            putExtra(EXTRA_IS_COMMUNITY, isCommunity)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(appContext).notify(notificationId, notification)
        }
    }

    fun showIncomingCallNotification(
        roomId: String,
        roomCode: String,
        callerPublicKey: String,
        callerUsername: String,
        roomName: String,
        offerSdp: String
    ) {
        if (!hasNotificationPermission()) return
        ensureChannels()

        val fullScreenIntent = Intent(appContext, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROOM_ID, roomId)
            putExtra(EXTRA_ROOM_CODE, roomCode)
            putExtra(EXTRA_CALLER_PUBLIC_KEY, callerPublicKey)
            putExtra(EXTRA_CALLER_USERNAME, callerUsername)
            putExtra(EXTRA_ROOM_NAME, roomName)
            putExtra(EXTRA_OFFER_SDP, offerSdp)
        }
        val fullScreenPending = PendingIntent.getActivity(
            appContext,
            REQ_FULL_SCREEN_CALL,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(appContext, MainActivity::class.java).apply {
            action = ACTION_ACCEPT_CALL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROOM_ID, roomId)
            putExtra(EXTRA_ROOM_CODE, roomCode)
            putExtra(EXTRA_CALLER_PUBLIC_KEY, callerPublicKey)
            putExtra(EXTRA_CALLER_USERNAME, callerUsername)
            putExtra(EXTRA_ROOM_NAME, roomName)
            putExtra(EXTRA_OFFER_SDP, offerSdp)
        }
        val acceptPending = PendingIntent.getActivity(
            appContext,
            REQ_ACCEPT_CALL,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(appContext, MainActivity::class.java).apply {
            action = ACTION_DECLINE_CALL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ROOM_ID, roomId)
            putExtra(EXTRA_ROOM_CODE, roomCode)
            putExtra(EXTRA_CALLER_PUBLIC_KEY, callerPublicKey)
            putExtra(EXTRA_CALLER_USERNAME, callerUsername)
            putExtra(EXTRA_ROOM_NAME, roomName)
        }
        val declinePending = PendingIntent.getActivity(
            appContext,
            REQ_DECLINE_CALL,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_CALLS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(callerUsername)
            .setContentText("Encrypted Voice Call · $roomName")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .addAction(0, "Decline", declinePending)
            .addAction(0, "Accept", acceptPending)
            .build()

        runCatching {
            NotificationManagerCompat.from(appContext).notify(INCOMING_CALL_NOTIFICATION_ID, notification)
        }
    }

    fun cancelIncomingCallNotification() {
        runCatching {
            NotificationManagerCompat.from(appContext).cancel(INCOMING_CALL_NOTIFICATION_ID)
        }
    }

    companion object {
        const val CHANNEL_MESSAGES = "veil_messages"
        const val CHANNEL_CALLS = "veil_calls"
        const val INCOMING_CALL_NOTIFICATION_ID = 9001

        const val ACTION_ACCEPT_CALL = "com.veil.app.ACTION_ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "com.veil.app.ACTION_DECLINE_CALL"

        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_ROOM_CODE = "extra_room_code"
        const val EXTRA_TARGET_ID = "extra_target_id"
        const val EXTRA_IS_COMMUNITY = "extra_is_community"
        const val EXTRA_CALLER_PUBLIC_KEY = "extra_caller_public_key"
        const val EXTRA_CALLER_USERNAME = "extra_caller_username"
        const val EXTRA_ROOM_NAME = "extra_room_name"
        const val EXTRA_OFFER_SDP = "extra_offer_sdp"

        private const val REQ_FULL_SCREEN_CALL = 9101
        private const val REQ_ACCEPT_CALL = 9102
        private const val REQ_DECLINE_CALL = 9103
    }
}
