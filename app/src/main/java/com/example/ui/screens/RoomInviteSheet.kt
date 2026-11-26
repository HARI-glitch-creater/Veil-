package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.VeilRoomEntity
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.QrCodeView
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomInviteSheet(
    room: VeilRoomEntity,
    inviteUri: String,
    onDismiss: () -> Unit,
    onCopied: (String) -> Unit
) {
    val context = LocalContext.current
    val accent = LocalVeilAccent.current
    val formattedCode = room.veilCode.chunked(4).joinToString(" - ")

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GlassPill(
                text = "Room Code & QR Invite",
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
            Text(
                text = formattedCode,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    color = accent.highlight,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.testTag("room_invite_code_display")
            )

            if (inviteUri.isNotBlank()) {
                QrCodeView(payload = inviteUri, sizeDp = 200.dp)
            }

            Text(
                text = "Peers who scan this QR code or enter the 12-digit Veil Code join ${room.roomName} with end-to-end encryption.",
                style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary),
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassSecondaryButton(
                    text = "Copy Code",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Veil Room Code", room.veilCode))
                        onCopied("12-digit Veil Code copied.")
                    },
                    modifier = Modifier.weight(1f),
                    tag = "room_invite_copy_code"
                )
                GlassPrimaryButton(
                    text = "Share Invite",
                    icon = Icons.Rounded.Share,
                    onClick = {
                        val shareBody = "Join Veil Room '${room.roomName}'\nCode: $formattedCode\n$inviteUri"
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareBody)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Veil Room Invite"))
                    },
                    modifier = Modifier.weight(1f),
                    tag = "room_invite_share"
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
