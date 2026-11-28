package com.example.call

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.MainActivity
import com.example.notifications.VeilNotificationHelper
import com.example.service.CallActionReceiver
import com.example.ui.components.GlassPill
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.VeilObsidianBackdrop
import com.example.ui.theme.CoralError
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary
import com.example.ui.theme.VeilTheme

class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        IncomingCallBridge.onRemoteCancelled = {
            runOnUiThread { finish() }
        }

        val roomId = intent.getStringExtra(VeilNotificationHelper.EXTRA_ROOM_ID).orEmpty()
        val roomCode = intent.getStringExtra(VeilNotificationHelper.EXTRA_ROOM_CODE).orEmpty()
        val callerPub = intent.getStringExtra(VeilNotificationHelper.EXTRA_CALLER_PUBLIC_KEY).orEmpty()
        val callerName = intent.getStringExtra(VeilNotificationHelper.EXTRA_CALLER_USERNAME).orEmpty().ifBlank { "Veil Peer" }
        val callerPfp = intent.getStringExtra(VeilNotificationHelper.EXTRA_CALLER_PFP_PATH).orEmpty()
        val roomName = intent.getStringExtra(VeilNotificationHelper.EXTRA_ROOM_NAME).orEmpty().ifBlank { "Veil Room" }
        val offerSdp = intent.getStringExtra(VeilNotificationHelper.EXTRA_OFFER_SDP).orEmpty()

        setContent {
            VeilTheme {
                IncomingVoiceCallRingScreen(
                    callerUsername = callerName,
                    callerPfpPath = callerPfp,
                    roomName = roomName,
                    onAccept = {
                        VeilNotificationHelper(this).cancelIncomingCallNotification()
                        val acceptIntent = Intent(this, MainActivity::class.java).apply {
                            action = VeilNotificationHelper.ACTION_ACCEPT_CALL
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(VeilNotificationHelper.EXTRA_ROOM_ID, roomId)
                            putExtra(VeilNotificationHelper.EXTRA_ROOM_CODE, roomCode)
                            putExtra(VeilNotificationHelper.EXTRA_CALLER_PUBLIC_KEY, callerPub)
                            putExtra(VeilNotificationHelper.EXTRA_CALLER_USERNAME, callerName)
                            putExtra(VeilNotificationHelper.EXTRA_CALLER_PFP_PATH, callerPfp)
                            putExtra(VeilNotificationHelper.EXTRA_ROOM_NAME, roomName)
                            putExtra(VeilNotificationHelper.EXTRA_OFFER_SDP, offerSdp)
                        }
                        startActivity(acceptIntent)
                        finish()
                    },
                    onDecline = {
                        // Dispatch via CallActionReceiver so Decline works even if MainActivity is dead
                        val declineBroadcast = Intent(this, CallActionReceiver::class.java).apply {
                            action = VeilNotificationHelper.ACTION_DECLINE_CALL
                            putExtra(VeilNotificationHelper.EXTRA_ROOM_ID, roomId)
                            putExtra(VeilNotificationHelper.EXTRA_ROOM_CODE, roomCode)
                            putExtra(VeilNotificationHelper.EXTRA_CALLER_PUBLIC_KEY, callerPub)
                            putExtra(VeilNotificationHelper.EXTRA_CALLER_USERNAME, callerName)
                            putExtra(VeilNotificationHelper.EXTRA_CALLER_PFP_PATH, callerPfp)
                            putExtra(VeilNotificationHelper.EXTRA_ROOM_NAME, roomName)
                        }
                        sendBroadcast(declineBroadcast)
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        IncomingCallBridge.onRemoteCancelled = null
        super.onDestroy()
    }
}

@Composable
private fun IncomingVoiceCallRingScreen(
    callerUsername: String,
    callerPfpPath: String,
    roomName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val accent = LocalVeilAccent.current
    val pulseTransition = rememberInfiniteTransition(label = "incoming_ring_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    VeilObsidianBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlassPill(
                    text = "End-to-End Encrypted Voice",
                    icon = Icons.Outlined.Lock,
                    accentColor = accent.highlight
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = roomName,
                    style = MaterialTheme.typography.bodyMedium.copy(color = SlateSecondary)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(144.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(accent.primary.copy(alpha = 0.14f))
                            .border(1.dp, accent.highlight.copy(alpha = 0.35f), CircleShape)
                    )
                    ProfileAvatar(
                        localPfpPath = callerPfpPath,
                        username = callerUsername,
                        size = 108.dp,
                        accent = accent.highlight
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = callerUsername,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = SnowPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Incoming Voice Call",
                    style = MaterialTheme.typography.bodyLarge.copy(color = SlateSecondary)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(CoralError)
                            .testTag("incoming_call_decline")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = "Decline voice call",
                            tint = ObsidianDeep,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Decline", style = MaterialTheme.typography.labelMedium.copy(color = SlateSecondary))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(accent.primary)
                            .testTag("incoming_call_accept")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = "Accept voice call",
                            tint = ObsidianDeep,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Accept", style = MaterialTheme.typography.labelMedium.copy(color = accent.highlight))
                }
            }
        }
    }
}
