package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.crypto.VeilCryptoEngine
import com.example.data.local.CommunityEntity
import com.example.data.local.RoomMemberEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.ProfileAvatar
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassDivider
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary

@Composable
fun CommunitiesTabScreen(
    communities: List<CommunityEntity>,
    searchQuery: String,
    onOpenCommunity: (CommunityEntity) -> Unit,
    onOpenCreateCommunity: () -> Unit
) {
    val accent = LocalVeilAccent.current

    val filtered = remember(communities, searchQuery) {
        if (searchQuery.isBlank()) communities
        else {
            val q = searchQuery.trim().lowercase()
            communities.filter {
                it.name.lowercase().contains(q) ||
                    it.description.lowercase().contains(q) ||
                    it.lastMessagePreview.lowercase().contains(q)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            items(filtered, key = { it.id }) { community ->
                CommunityRow(
                    community = community,
                    onClick = { onOpenCommunity(community) }
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
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    containerColor = GlassSurfaceSubtle
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = accent.highlight,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Private Communities require at least 3 profiles total and are completely invisible to non-members.",
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onOpenCreateCommunity,
            shape = RoundedCornerShape(16.dp),
            containerColor = accent.primary,
            contentColor = ObsidianDeep,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("communities_create_fab")
        ) {
            Icon(
                imageVector = Icons.Rounded.GroupAdd,
                contentDescription = "Create Private Community"
            )
        }
    }
}

@Composable
private fun CommunityRow(
    community: CommunityEntity,
    onClick: () -> Unit
) {
    val accent = LocalVeilAccent.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("community_row_${community.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (community.isDefault) accent.container else GlassSurfaceElevated)
                .border(
                    width = 1.dp,
                    color = if (community.isDefault) accent.highlight.copy(alpha = 0.45f) else GlassBorderTop,
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (community.isDefault) Icons.Rounded.Public else Icons.Rounded.Groups,
                contentDescription = null,
                tint = if (community.isDefault) accent.highlight else SnowPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = community.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = SnowPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                GlassPill(
                    text = if (community.isDefault) "Room" else "Private · ${community.memberCount}",
                    accentColor = if (community.isDefault) accent.highlight else SlateSecondary
                )
            }
            Text(
                text = community.lastMessagePreview.ifBlank { community.description },
                style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = formatCompactTime(community.lastMessageTimestamp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    color = if (community.unreadCount > 0) accent.highlight else SlateMuted
                )
            )
            if (community.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent.primary)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCommunitySheet(
    roomMembers: List<RoomMemberEntity>,
    preselectedPeerPublicKey: String? = null,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, selectedPeerKeys: List<String>) -> Unit
) {
    val accent = LocalVeilAccent.current
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedPeers by remember(preselectedPeerPublicKey) {
        mutableStateOf(
            if (!preselectedPeerPublicKey.isNullOrBlank()) setOf(preselectedPeerPublicKey) else emptySet()
        )
    }

    val eligiblePeers = remember(roomMembers) { roomMembers.filter { !it.isBlocked } }
    // Hard validation: minimum 3 profiles total (creator + at least 2 room peers)
    val meetsMinimum = selectedPeers.size >= 2
    val canCreate = name.trim().length >= 2 && meetsMinimum

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GlassSurfaceElevated,
        contentColor = SnowPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Create Private Community",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = SnowPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "Select at least 2 other room profiles (minimum 3 profiles total including you). Only selected members can see or decrypt this community.",
                style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
            )

            GlassTextField(
                value = name,
                onValueChange = { name = VeilCryptoEngine.stripEmojis(it).take(36) },
                label = "Community Name",
                placeholder = "e.g. Weekend Plans",
                tag = "create_community_name_input"
            )

            GlassTextField(
                value = description,
                onValueChange = { description = VeilCryptoEngine.stripEmojis(it).take(120) },
                label = "Description (Optional)",
                placeholder = "What is this private community for?",
                tag = "create_community_desc_input"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Room Profiles",
                    style = MaterialTheme.typography.labelLarge.copy(color = SnowPrimary)
                )
                Text(
                    text = "${selectedPeers.size + 1} / 3+ profiles",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        color = if (meetsMinimum) accent.highlight else AmberWarn
                    ),
                    modifier = Modifier.testTag("create_community_count_badge")
                )
            }

            if (eligiblePeers.size < 2) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = GlassSurfaceSubtle
                ) {
                    Text(
                        text = "At least 2 other peers must join this room before you can create a 3+ profile private community.",
                        style = MaterialTheme.typography.bodySmall.copy(color = AmberWarn)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(eligiblePeers, key = { it.publicKey }) { peer ->
                        val checked = peer.publicKey in selectedPeers
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (checked) accent.container else GlassSurfaceSubtle)
                                .clickable {
                                    selectedPeers = if (checked) {
                                        selectedPeers - peer.publicKey
                                    } else {
                                        selectedPeers + peer.publicKey
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("select_community_peer_${peer.username.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProfileAvatar(
                                localPfpPath = peer.localPfpPath,
                                username = peer.username,
                                size = 38.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = peer.username,
                                    style = MaterialTheme.typography.bodyLarge.copy(color = SnowPrimary)
                                )
                                if (peer.motto.isNotBlank()) {
                                    Text(
                                        text = peer.motto,
                                        style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (checked) accent.highlight else SlateMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            GlassPrimaryButton(
                text = "Create Community",
                icon = Icons.Rounded.GroupAdd,
                enabled = canCreate,
                onClick = { onCreate(name.trim(), description.trim(), selectedPeers.toList()) },
                modifier = Modifier.fillMaxWidth(),
                tag = "create_community_submit_button"
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
