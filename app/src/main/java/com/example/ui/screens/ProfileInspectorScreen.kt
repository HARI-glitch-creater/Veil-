package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.crypto.VeilCryptoEngine
import com.example.data.local.RoomMemberEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.QrCodeView
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileInspectorScreen(
    roomName: String,
    member: RoomMemberEntity,
    safetyNumberGroups: List<String>,
    isRoomCreator: Boolean,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onVoiceCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleBlock: () -> Unit,
    onToggleVerified: (Boolean) -> Unit,
    onReport: (String, String) -> Unit,
    onRemoveFromRoom: () -> Unit
) {
    BackHandler(onBack = onBack)
    val accent = LocalVeilAccent.current
    var showReportDialog by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("profile_inspector_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = SnowPrimary
                    )
                }
                Text(
                    text = "Inspect Profile",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = SnowPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Full Profile Header Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileAvatar(
                        localPfpPath = member.localPfpPath,
                        username = member.username,
                        size = 104.dp,
                        showOnlineDot = member.isOnline && !member.isBlocked
                    )
                    Text(
                        text = member.username,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = SnowPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (member.motto.isNotBlank()) {
                        Text(
                            text = member.motto,
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateSecondary),
                            textAlign = TextAlign.Center
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassPill(text = member.gender, accentColor = SlateSecondary)
                        if (member.isVerified) {
                            GlassPill(
                                text = "Verified",
                                icon = Icons.Rounded.VerifiedUser,
                                accentColor = accent.highlight
                            )
                        }
                        if (member.isBlocked) {
                            GlassPill(
                                text = "Blocked",
                                accentColor = CoralError,
                                containerColor = CoralContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Primary Quick Actions: Message & Voice Call (NO video)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassPrimaryButton(
                            text = "Message",
                            icon = Icons.Rounded.ChatBubble,
                            onClick = onMessage,
                            modifier = Modifier.weight(1f),
                            tag = "inspector_message_button"
                        )
                        GlassSecondaryButton(
                            text = "Voice Call",
                            icon = Icons.Rounded.Call,
                            accentColor = accent.highlight,
                            enabled = !member.isBlocked,
                            onClick = onVoiceCall,
                            modifier = Modifier.weight(1f),
                            tag = "inspector_voice_call_button"
                        )
                    }
                }
            }

            // Device-Bound 60-Digit Safety Number & QR
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Device-Bound Safety Number",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SnowPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = accent.highlight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Compare this 60-digit cryptographic safety number or QR in person to verify zero-knowledge E2EE keys.",
                        style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                    )

                    if (member.safetyNumber.isNotBlank()) {
                        QrCodeView(
                            payload = "veil://safety?num=${member.safetyNumber}&pub=${member.publicKey}",
                            sizeDp = 172.dp
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            safetyNumberGroups.forEach { block ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(GlassSurfaceSubtle)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = block,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            color = accent.highlight
                                        )
                                    )
                                }
                            }
                        }
                        GlassSecondaryButton(
                            text = if (member.isVerified) "Clear Verified Mark" else "Mark Safety Number Verified",
                            icon = Icons.Rounded.VerifiedUser,
                            accentColor = if (member.isVerified) SlateSecondary else accent.highlight,
                            onClick = { onToggleVerified(!member.isVerified) },
                            modifier = Modifier.fillMaxWidth(),
                            tag = "inspector_verify_button"
                        )
                    } else {
                        Text(
                            text = "Safety number will appear once key exchange completes.",
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                        )
                    }
                }
            }

            // Moderation Controls: Mute, Block, Report
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Privacy & Moderation",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = SnowPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    GlassSecondaryButton(
                        text = if (member.isMuted) "Unmute Profile" else "Mute Profile",
                        icon = Icons.Outlined.VolumeOff,
                        onClick = onToggleMute,
                        modifier = Modifier.fillMaxWidth(),
                        tag = "inspector_mute_button"
                    )
                    GlassSecondaryButton(
                        text = if (member.isBlocked) "Unblock Profile" else "Block Profile",
                        icon = Icons.Outlined.Block,
                        accentColor = CoralError,
                        onClick = onToggleBlock,
                        modifier = Modifier.fillMaxWidth(),
                        tag = "inspector_block_button"
                    )
                    GlassSecondaryButton(
                        text = "Report Profile",
                        icon = Icons.Outlined.Flag,
                        accentColor = CoralError,
                        onClick = { showReportDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        tag = "inspector_report_button"
                    )
                    if (isRoomCreator) {
                        GlassSecondaryButton(
                            text = "Remove from Room",
                            icon = Icons.Outlined.PersonRemove,
                            accentColor = CoralError,
                            onClick = { showRemoveConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            tag = "inspector_remove_from_room_button"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showReportDialog) {
        ReportProfileDialog(
            username = member.username,
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, notes ->
                onReport(reason, notes)
                showReportDialog = false
            }
        )
    }

    if (showRemoveConfirm && isRoomCreator) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            containerColor = GlassSurfaceElevated,
            titleContentColor = SnowPrimary,
            textContentColor = SlateSecondary,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.PersonRemove,
                    contentDescription = null,
                    tint = CoralError,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Remove ${member.username}?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "This removes ${member.username} from $roomName and revokes their room membership across the directory and private communities.",
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                )
            },
            confirmButton = {
                GlassSecondaryButton(
                    text = "Remove",
                    icon = Icons.Outlined.PersonRemove,
                    accentColor = CoralError,
                    onClick = {
                        showRemoveConfirm = false
                        onRemoveFromRoom()
                    },
                    tag = "inspector_confirm_remove_button"
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { showRemoveConfirm = false },
                    modifier = Modifier.testTag("inspector_cancel_remove_button")
                ) {
                    Text("Cancel", color = SlateSecondary)
                }
            }
        )
    }
}
