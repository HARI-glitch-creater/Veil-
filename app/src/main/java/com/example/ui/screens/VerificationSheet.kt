package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.VeilRoomEntity
import com.example.ui.VeilViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.QrCodeView
import com.example.ui.theme.CoralError
import com.example.ui.theme.GlacierMint
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationSheet(
    room: VeilRoomEntity,
    viewModel: VeilViewModel,
    onDismiss: () -> Unit,
    onRoomDeleted: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    var inviteUri by remember(room.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(room.id) {
        viewModel.buildRoomInviteUri(room) { uri ->
            inviteUri = uri
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBg,
        contentColor = SnowPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = room.roomName,
                style = MaterialTheme.typography.headlineSmall.copy(color = SnowPrimary)
            )

            // 12-Digit Veil Code & Room QR
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderTopColor = GlacierMint.copy(alpha = 0.3f),
                contentPadding = PaddingValues(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "12-DIGIT VEIL CODE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SlateSecondary,
                            letterSpacing = 1.4.sp
                        )
                    )
                    Text(
                        text = viewModel.formatVeilCode(room.veilCode),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            color = GlacierMint,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    inviteUri?.let { uri ->
                        QrCodeView(payload = uri, sizeDp = 172.dp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassSecondaryButton(
                            text = "Copy Code",
                            icon = Icons.Rounded.ContentCopy,
                            onClick = {
                                clipboard.setText(AnnotatedString(room.veilCode))
                                viewModel.showToast("12-digit Veil Code copied")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        GlassSecondaryButton(
                            text = "Copy Invite",
                            icon = Icons.Rounded.Link,
                            enabled = inviteUri != null,
                            onClick = {
                                inviteUri?.let {
                                    clipboard.setText(AnnotatedString(it))
                                    viewModel.showToast("Room Invite copied")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 60-Digit Safety Number Verification
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (room.isVerified) Icons.Rounded.VerifiedUser else Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (room.isVerified) GlacierMint else SlateSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "60-Digit Safety Number",
                            style = MaterialTheme.typography.titleMedium.copy(color = SnowPrimary)
                        )
                    }

                    if (room.safetyNumber.length == 60) {
                        val groups = remember(room.safetyNumber) {
                            viewModel.formatSafetyNumberGroups(room.safetyNumber)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(GlassSurfaceSubtle)
                                .border(1.dp, GlassBorderTop, RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            groups.chunked(4).forEach { rowBlocks ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    rowBlocks.forEach { block ->
                                        Text(
                                            text = block,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = JetBrainsMonoFamily,
                                                color = SnowPrimary,
                                                letterSpacing = 1.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Compare these 60 digits with ${room.peerHandle.ifBlank { "your peer" }} in person or over a call to confirm zero server tampering.",
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary),
                            textAlign = TextAlign.Center
                        )

                        GlassPrimaryButton(
                            text = if (room.isVerified) "Verified · Tap to Clear" else "Mark Safety Number Verified",
                            icon = Icons.Rounded.VerifiedUser,
                            onClick = { viewModel.setRoomVerified(room.id, !room.isVerified) },
                            modifier = Modifier.fillMaxWidth(),
                            tag = "toggle_verify_safety_number_button"
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(GlassSurfaceSubtle)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "Waiting for a peer to join this Veil Room with the 12-digit code so your 60-digit ECDH safety number can be derived.",
                                style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            GlassSecondaryButton(
                text = "Remove Room from Device",
                icon = Icons.Rounded.DeleteOutline,
                accentColor = CoralError,
                onClick = { viewModel.deleteRoom(room.id, onRoomDeleted) },
                modifier = Modifier.fillMaxWidth(),
                tag = "delete_room_button"
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
