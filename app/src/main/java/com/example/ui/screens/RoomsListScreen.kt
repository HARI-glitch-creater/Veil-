package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.BackendProfileEntity
import com.example.data.local.BackendType
import com.example.data.local.RoomRole
import com.example.data.local.VeilRoomEntity
import com.example.ui.VeilViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.MonogramAvatar
import com.example.ui.theme.GlacierMint
import com.example.ui.theme.GlacierMintContainer
import com.example.ui.theme.GlassBorderBottom
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.IceCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RoomsListScreen(
    viewModel: VeilViewModel,
    selectedRoomId: String?,
    onOpenRoom: (String) -> Unit,
    onHostNewRoom: () -> Unit,
    onJoinNewRoom: () -> Unit,
    onOpenRoleChooser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val profiles by viewModel.backendProfiles.collectAsStateWithLifecycle()
    val activeProfile = profiles.firstOrNull { it.isActive } ?: profiles.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Minimalist Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Veil",
                    style = MaterialTheme.typography.headlineMedium.copy(color = SnowPrimary)
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (activeProfile != null) {
                    GlassPill(
                        text = activeProfile.name,
                        icon = if (activeProfile.backendType == BackendType.SUPABASE) {
                            Icons.Rounded.Storage
                        } else {
                            Icons.Rounded.Dns
                        },
                        accentColor = GlacierMint
                    )
                } else {
                    Text(
                        text = "Zero-knowledge encrypted rooms",
                        style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                    )
                }
            }

            IconButton(
                onClick = onOpenRoleChooser,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(GlacierMintContainer)
                    .border(1.dp, GlacierMint.copy(alpha = 0.42f), CircleShape)
                    .testTag("add_room_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Host or join a Veil Room",
                    tint = GlacierMint
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (rooms.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = GlacierMint,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = stringResource(R.string.empty_rooms_title),
                            style = MaterialTheme.typography.titleLarge.copy(color = SnowPrimary)
                        )
                        Text(
                            text = stringResource(R.string.empty_rooms_desc),
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassPrimaryButton(
                                text = "Host Room",
                                icon = Icons.Rounded.Dns,
                                onClick = onHostNewRoom,
                                modifier = Modifier.weight(1f),
                                tag = "empty_host_button"
                            )
                            GlassSecondaryButton(
                                text = "Join Code",
                                icon = Icons.Rounded.Key,
                                onClick = onJoinNewRoom,
                                modifier = Modifier.weight(1f),
                                accentColor = IceCyan,
                                tag = "empty_join_button"
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 104.dp)
            ) {
                items(rooms, key = { it.id }) { room ->
                    val linkedBackend = profiles.firstOrNull { it.id == room.backendProfileId }
                    RoomGlassRow(
                        room = room,
                        backend = linkedBackend,
                        formattedCode = viewModel.formatVeilCode(room.veilCode),
                        selected = room.id == selectedRoomId,
                        onClick = { onOpenRoom(room.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomGlassRow(
    room: VeilRoomEntity,
    backend: BackendProfileEntity?,
    formattedCode: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("room_card_${room.veilCode}"),
        cornerRadius = 22.dp,
        containerColor = if (selected) GlassSurfaceElevated else com.example.ui.theme.GlassSurface,
        borderTopColor = if (selected) GlacierMint.copy(alpha = 0.45f) else GlassBorderTop,
        borderBottomColor = GlassBorderBottom,
        contentPadding = PaddingValues(16.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonogramAvatar(
                label = room.roomName,
                accent = if (room.role == RoomRole.HOST) GlacierMint else IceCyan
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = room.roomName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SnowPrimary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = if (room.isVerified) Icons.Rounded.VerifiedUser else Icons.Rounded.Lock,
                            contentDescription = if (room.isVerified) "Verified E2EE" else "Encrypted",
                            tint = if (room.isVerified) GlacierMint else SlateMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = formatShortTime(room.lastMessageTimestamp),
                        style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = room.lastMessagePreview.ifBlank { "End-to-end encrypted room" },
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formattedCode,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            color = GlacierMint.copy(alpha = 0.85f)
                        )
                    )
                    if (backend != null) {
                        Text(
                            text = "· ${if (backend.backendType == BackendType.SUPABASE) "Supabase" else backend.name}",
                            style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun formatShortTime(timestamp: Long): String {
    return runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }.getOrDefault("")
}
