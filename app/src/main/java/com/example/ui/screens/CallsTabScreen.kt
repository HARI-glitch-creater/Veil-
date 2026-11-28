package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.call.ActiveCallState
import com.example.call.VoiceCallStatus
import com.example.data.local.CallDirection
import com.example.data.local.CallHistoryEntity
import com.example.data.local.RoomMemberEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.VeilObsidianBackdrop
import com.example.ui.theme.CoralError
import com.example.ui.theme.GlassDivider
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Strictly Voice-Only Calls Tab & 3-Month (90-Day) Retained Call Log.
 * Zero video call icons, camera switches, or video UI anywhere.
 */
@Composable
fun CallsTabScreen(
    callHistory: List<CallHistoryEntity>,
    members: List<RoomMemberEntity> = emptyList(),
    searchQuery: String,
    onPlaceVoiceCall: (peerPublicKey: String, peerUsername: String, peerPfpPath: String) -> Unit
) {
    val accent = LocalVeilAccent.current
    val memberByKey = remember(members) { members.associateBy { it.publicKey } }
    val filtered = remember(callHistory, searchQuery) {
        if (searchQuery.isBlank()) callHistory
        else {
            val q = searchQuery.trim().lowercase()
            callHistory.filter { it.peerUsername.lowercase().contains(q) }
        }
    }

    if (filtered.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = null,
                        tint = accent.highlight,
                        modifier = Modifier.size(38.dp)
                    )
                    Text(
                        text = if (searchQuery.isBlank()) "No Voice Calls Yet" else "No Matching Calls",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = SnowPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "Start an end-to-end encrypted voice call from any profile or chat header. Call history is automatically pruned after 90 days.",
                        style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            items(filtered, key = { it.id }) { entry ->
                val livePeer = memberByKey[entry.peerPublicKey]
                val resolvedPfp = livePeer?.localPfpPath?.ifBlank { entry.peerPfpPath } ?: entry.peerPfpPath
                val resolvedName = livePeer?.username?.ifBlank { entry.peerUsername } ?: entry.peerUsername
                CallHistoryRow(
                    entry = entry.copy(peerUsername = resolvedName, peerPfpPath = resolvedPfp),
                    onVoiceCallback = {
                        onPlaceVoiceCall(entry.peerPublicKey, resolvedName, resolvedPfp)
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 76.dp, end = 16.dp)
                        .height(1.dp)
                        .background(GlassDivider)
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassPill(
                        text = "Encrypted Voice Logs · Auto-Deleted After 90 Days",
                        icon = Icons.Outlined.Lock,
                        accentColor = SlateMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun CallHistoryRow(
    entry: CallHistoryEntity,
    onVoiceCallback: () -> Unit
) {
    val accent = LocalVeilAccent.current
    val (dirIcon, dirTint, dirLabel) = when (entry.direction) {
        CallDirection.OUTGOING -> Triple(Icons.AutoMirrored.Rounded.CallMade, accent.highlight, "Outgoing")
        CallDirection.INCOMING -> Triple(Icons.AutoMirrored.Rounded.CallReceived, accent.highlight, "Incoming")
        CallDirection.MISSED -> Triple(Icons.AutoMirrored.Rounded.CallMissed, CoralError, "Missed")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onVoiceCallback)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("call_history_row_${entry.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProfileAvatar(
            localPfpPath = entry.peerPfpPath,
            username = entry.peerUsername,
            size = 46.dp
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = entry.peerUsername,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = if (entry.direction == CallDirection.MISSED) CoralError else SnowPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = dirIcon,
                    contentDescription = dirLabel,
                    tint = dirTint,
                    modifier = Modifier.size(15.dp)
                )
                val durationPart = if (entry.durationSec > 0L) " · ${formatDurationMmSs(entry.durationSec)}" else ""
                Text(
                    text = "$dirLabel · ${formatCallDate(entry.timestamp)}$durationPart",
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                )
            }
        }

        IconButton(
            onClick = onVoiceCallback,
            modifier = Modifier.testTag("call_history_callback_${entry.id}")
        ) {
            Icon(
                imageVector = Icons.Rounded.Call,
                contentDescription = "Place encrypted voice call to ${entry.peerUsername}",
                tint = accent.highlight
            )
        }
    }
}

/**
 * Full-screen dark glass active voice call UI.
 * Strictly 3 controls during active call (Mute Mic, Speaker, End Call) — NO video controls.
 */
@Composable
fun ActiveVoiceCallOverlay(
    callState: ActiveCallState,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    onAcceptIncoming: () -> Unit,
    onDeclineIncoming: () -> Unit
) {
    val accent = LocalVeilAccent.current
    val pulseTransition = rememberInfiniteTransition(label = "active_voice_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.26f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice_pulse_scale"
    )

    VeilObsidianBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassPill(
                    text = "End-to-End Encrypted Voice",
                    icon = Icons.Outlined.Lock,
                    accentColor = accent.highlight
                )
                Text(
                    text = callState.roomName,
                    style = MaterialTheme.typography.bodyMedium.copy(color = SlateSecondary)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(152.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(accent.primary.copy(alpha = 0.14f))
                            .border(1.dp, accent.highlight.copy(alpha = 0.36f), CircleShape)
                    )
                    ProfileAvatar(
                        localPfpPath = callState.peerPfpPath,
                        username = callState.peerUsername.ifBlank { "Veil Peer" },
                        size = 112.dp
                    )
                }

                Text(
                    text = callState.peerUsername.ifBlank { "Veil Peer" },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = SnowPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )

                val subtitle = when (callState.status) {
                    VoiceCallStatus.CONNECTED -> formatDurationMmSs(callState.elapsedSeconds)
                    VoiceCallStatus.OUTGOING_RINGING -> "Calling..."
                    VoiceCallStatus.INCOMING_RINGING -> "Incoming Voice Call"
                    VoiceCallStatus.CONNECTING -> "Establishing Encrypted Audio..."
                    else -> callState.statusMessage
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = if (callState.status == VoiceCallStatus.CONNECTED) {
                            JetBrainsMonoFamily
                        } else {
                            MaterialTheme.typography.titleMedium.fontFamily
                        },
                        color = accent.highlight
                    ),
                    modifier = Modifier.testTag("active_call_status_timer")
                )
            }

            if (callState.status == VoiceCallStatus.INCOMING_RINGING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    VoiceControlButton(
                        icon = Icons.Rounded.CallEnd,
                        label = "Decline",
                        containerColor = CoralError,
                        iconTint = ObsidianDeep,
                        onClick = onDeclineIncoming,
                        tag = "active_call_decline"
                    )
                    VoiceControlButton(
                        icon = Icons.Rounded.Call,
                        label = "Accept",
                        containerColor = accent.primary,
                        iconTint = ObsidianDeep,
                        onClick = onAcceptIncoming,
                        tag = "active_call_accept"
                    )
                }
            } else {
                // Strictly 3 Voice Controls: Mute Mic, Speaker, End Call
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    VoiceControlButton(
                        icon = if (callState.isMicMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        label = if (callState.isMicMuted) "Unmute" else "Mute Mic",
                        containerColor = if (callState.isMicMuted) accent.container else GlassSurfaceElevated,
                        iconTint = if (callState.isMicMuted) accent.highlight else SnowPrimary,
                        onClick = onToggleMute,
                        tag = "active_call_mute_mic"
                    )
                    VoiceControlButton(
                        icon = Icons.Rounded.VolumeUp,
                        label = "Speaker",
                        containerColor = if (callState.isSpeakerOn) accent.container else GlassSurfaceElevated,
                        iconTint = if (callState.isSpeakerOn) accent.highlight else SnowPrimary,
                        onClick = onToggleSpeaker,
                        tag = "active_call_speaker"
                    )
                    VoiceControlButton(
                        icon = Icons.Rounded.CallEnd,
                        label = "End Call",
                        containerColor = CoralError,
                        iconTint = ObsidianDeep,
                        onClick = onEndCall,
                        tag = "active_call_end"
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    tag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(containerColor)
                .testTag(tag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(color = SlateSecondary)
        )
    }
}

fun formatDurationMmSs(totalSeconds: Long): String {
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    return "%02d:%02d".format(mins, secs)
}

private fun formatCallDate(timestampMs: Long): String {
    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return fmt.format(Date(timestampMs))
}
