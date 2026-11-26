package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.crypto.VeilCryptoEngine
import com.example.data.local.RoomMemberEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.ProfileAvatar
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.GlassDivider
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val REPORT_REASONS = listOf("Spam", "Harassment", "Impersonation", "Malicious Files", "Other")

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfilesTabScreen(
    roomName: String,
    members: List<RoomMemberEntity>,
    searchQuery: String,
    isRoomCreator: Boolean,
    onOpenDirectChat: (RoomMemberEntity) -> Unit,
    onInspectProfile: (RoomMemberEntity) -> Unit,
    onCreateCommunityWithPeer: (RoomMemberEntity?) -> Unit,
    onToggleMute: (RoomMemberEntity) -> Unit,
    onToggleBlock: (RoomMemberEntity) -> Unit,
    onReportMember: (RoomMemberEntity, String, String) -> Unit,
    onRemoveMemberFromRoom: (RoomMemberEntity) -> Unit,
    onShareRoomInvite: () -> Unit
) {
    val accent = LocalVeilAccent.current
    val haptic = LocalHapticFeedback.current
    var longPressedMember by remember { mutableStateOf<RoomMemberEntity?>(null) }
    var reportingMember by remember { mutableStateOf<RoomMemberEntity?>(null) }
    var removingMember by remember { mutableStateOf<RoomMemberEntity?>(null) }

    val filteredMembers = remember(members, searchQuery) {
        if (searchQuery.isBlank()) members
        else {
            val q = searchQuery.trim().lowercase()
            members.filter {
                it.username.lowercase().contains(q) ||
                    it.motto.lowercase().contains(q) ||
                    it.lastMessagePreview.lowercase().contains(q)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (filteredMembers.isEmpty()) {
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = accent.highlight,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "Waiting for Profiles in $roomName" else "No Matching Profiles",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SnowPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = if (searchQuery.isBlank()) {
                                "Share this room's 16-character Veil Code so peers can join and appear in this directory."
                            } else {
                                "Try a different username or motto filter."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                        )
                        if (searchQuery.isBlank()) {
                            GlassPrimaryButton(
                                text = "View 16-Char Veil Code",
                                icon = Icons.Rounded.Key,
                                onClick = onShareRoomInvite,
                                tag = "profiles_empty_share_invite"
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
            ) {
                items(filteredMembers, key = { it.id }) { member ->
                    ProfileDirectoryRow(
                        member = member,
                        onTapRow = { onOpenDirectChat(member) },
                        onTapAvatar = { onInspectProfile(member) },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            longPressedMember = member
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
            }
        }

        // Bottom-right theme-colored rounded-square FAB for creating a Private Community
        FloatingActionButton(
            onClick = { onCreateCommunityWithPeer(null) },
            shape = RoundedCornerShape(16.dp),
            containerColor = accent.primary,
            contentColor = ObsidianDeep,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("profiles_create_community_fab")
        ) {
            Icon(
                imageVector = Icons.Rounded.GroupAdd,
                contentDescription = "Create Private Community"
            )
        }
    }

    // Long-Press Profile Context Menu (5 exact vector options)
    val targetMember = longPressedMember
    if (targetMember != null) {
        ModalBottomSheet(
            onDismissRequest = { longPressedMember = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = GlassSurfaceElevated,
            contentColor = SnowPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    ProfileAvatar(
                        localPfpPath = targetMember.localPfpPath,
                        username = targetMember.username,
                        size = 44.dp
                    )
                    Column {
                        Text(
                            text = targetMember.username,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SnowPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        if (targetMember.motto.isNotBlank()) {
                            Text(
                                text = targetMember.motto,
                                style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                ContextActionRow(
                    icon = Icons.Outlined.PersonSearch,
                    label = "Inspect Profile",
                    onClick = {
                        longPressedMember = null
                        onInspectProfile(targetMember)
                    },
                    tag = "profile_menu_inspect"
                )
                ContextActionRow(
                    icon = Icons.Outlined.GroupAdd,
                    label = "Create Community",
                    onClick = {
                        longPressedMember = null
                        onCreateCommunityWithPeer(targetMember)
                    },
                    tag = "profile_menu_create_community"
                )
                ContextActionRow(
                    icon = Icons.Outlined.VolumeOff,
                    label = if (targetMember.isMuted) "Unmute Profile" else "Mute Profile",
                    onClick = {
                        longPressedMember = null
                        onToggleMute(targetMember)
                    },
                    tag = "profile_menu_mute"
                )
                ContextActionRow(
                    icon = Icons.Outlined.Block,
                    label = if (targetMember.isBlocked) "Unblock Profile" else "Block Profile",
                    tint = CoralError,
                    onClick = {
                        longPressedMember = null
                        onToggleBlock(targetMember)
                    },
                    tag = "profile_menu_block"
                )
                ContextActionRow(
                    icon = Icons.Outlined.Flag,
                    label = "Report Profile",
                    tint = CoralError,
                    onClick = {
                        longPressedMember = null
                        reportingMember = targetMember
                    },
                    tag = "profile_menu_report"
                )
                if (isRoomCreator) {
                    ContextActionRow(
                        icon = Icons.Outlined.PersonRemove,
                        label = "Remove from Room",
                        tint = CoralError,
                        onClick = {
                            longPressedMember = null
                            removingMember = targetMember
                        },
                        tag = "profile_menu_remove_from_room"
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Structured Report Dialog
    val reportTarget = reportingMember
    if (reportTarget != null) {
        ReportProfileDialog(
            username = reportTarget.username,
            onDismiss = { reportingMember = null },
            onSubmit = { reason, notes ->
                onReportMember(reportTarget, reason, notes)
                reportingMember = null
            }
        )
    }

    // Stealth Creator-Only Member Removal Confirmation
    val removeTarget = removingMember
    if (removeTarget != null && isRoomCreator) {
        AlertDialog(
            onDismissRequest = { removingMember = null },
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
                    text = "Remove ${removeTarget.username}?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "This removes ${removeTarget.username} from $roomName and revokes their room membership across the directory and private communities.",
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                )
            },
            confirmButton = {
                GlassSecondaryButton(
                    text = "Remove",
                    icon = Icons.Outlined.PersonRemove,
                    accentColor = CoralError,
                    onClick = {
                        onRemoveMemberFromRoom(removeTarget)
                        removingMember = null
                    },
                    tag = "confirm_remove_member_button"
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { removingMember = null },
                    modifier = Modifier.testTag("cancel_remove_member_button")
                ) {
                    Text("Cancel", color = SlateSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileDirectoryRow(
    member: RoomMemberEntity,
    onTapRow: () -> Unit,
    onTapAvatar: () -> Unit,
    onLongPress: () -> Unit
) {
    val accent = LocalVeilAccent.current
    val hasChat = member.lastMessagePreview.isNotBlank() && member.lastMessageTimestamp > 0L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTapRow,
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("profile_row_${member.username.lowercase().replace(" ", "_")}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProfileAvatar(
            localPfpPath = member.localPfpPath,
            username = member.username,
            size = 48.dp,
            showOnlineDot = member.isOnline && !member.isBlocked,
            onClick = onTapAvatar
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = member.username,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = SnowPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (member.isMuted) {
                    Icon(
                        imageVector = Icons.Outlined.VolumeOff,
                        contentDescription = "Muted",
                        tint = SlateMuted,
                        modifier = Modifier.size(15.dp)
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (hasChat) {
                    Icon(
                        imageVector = Icons.Rounded.DoneAll,
                        contentDescription = null,
                        tint = accent.highlight,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = when {
                        hasChat -> member.lastMessagePreview
                        member.motto.isNotBlank() -> member.motto
                        else -> "Tap to start encrypted chat"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (member.lastMessageTimestamp > 0L) {
                Text(
                    text = formatCompactTime(member.lastMessageTimestamp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        color = if (member.unreadCount > 0) accent.highlight else SlateMuted
                    )
                )
            }
            if (member.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accent.primary)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (member.unreadCount > 99) "99+" else member.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ObsidianDeep,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = SnowPrimary,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(color = tint)
        )
    }
}

@Composable
fun ReportProfileDialog(
    username: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var selectedReason by remember { mutableStateOf(REPORT_REASONS.first()) }
    var notes by remember { mutableStateOf("") }
    val accent = LocalVeilAccent.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassSurfaceElevated,
        titleContentColor = SnowPrimary,
        textContentColor = SlateSecondary,
        title = {
            Text(
                text = "Report $username",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Select a reason for this moderation report:",
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                )
                REPORT_REASONS.forEach { reason ->
                    val selected = selectedReason == reason
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedReason = reason }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (selected) accent.highlight else SlateMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodyMedium.copy(color = SnowPrimary)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                GlassTextField(
                    value = notes,
                    onValueChange = { notes = VeilCryptoEngine.stripEmojis(it).take(300) },
                    label = "Optional Notes",
                    placeholder = "Add helpful context",
                    singleLine = false,
                    minLines = 2,
                    tag = "report_notes_input"
                )
            }
        },
        confirmButton = {
            GlassPrimaryButton(
                text = "Submit Report",
                onClick = { onSubmit(selectedReason, notes) },
                tag = "report_submit_button"
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SlateSecondary)
            }
        }
    )
}

fun formatCompactTime(timestampMs: Long): String {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return fmt.format(Date(timestampMs))
}
