package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.VeilRoomEntity
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomInviteSheet(
    room: VeilRoomEntity,
    onDismiss: () -> Unit,
    onCopied: (String) -> Unit
) {
    val context = LocalContext.current
    val accent = LocalVeilAccent.current
    val codeBlocks = room.veilCode.chunked(4)
    val formattedCode = codeBlocks.joinToString(" - ")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GlassSurfaceElevated,
        contentColor = SnowPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassPill(
                text = "16-Character Veil Code",
                icon = Icons.Outlined.Lock,
                accentColor = accent.highlight
            )
            Text(
                text = room.roomName,
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = SnowPrimary,
                    fontWeight = FontWeight.Bold
                )
            )

            // 4 blocks of 4 alphanumeric + symbol characters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("room_invite_code_display"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                codeBlocks.forEach { block ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassSurfaceSubtle)
                            .border(1.dp, accent.highlight.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = block,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                color = accent.highlight,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Text(
                text = formattedCode,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = JetBrainsMonoFamily,
                    color = SnowPrimary,
                    letterSpacing = 0.8.sp
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Code-only invite: share this exact 16-character Veil Code (and your Supabase or self-hosted server details if not already saved) so peers can join ${room.roomName} privately.",
                style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary),
                textAlign = TextAlign.Center
            )

            GlassPrimaryButton(
                text = "Copy 16-Character Veil Code",
                icon = Icons.Rounded.ContentCopy,
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Veil Room Code", room.veilCode))
                    onCopied("16-character Veil Code copied.")
                },
                modifier = Modifier.fillMaxWidth(),
                tag = "room_invite_copy_code"
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
