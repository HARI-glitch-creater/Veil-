package com.example.ui.components

import android.view.WindowManager
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import com.example.data.local.MessageContentType
import com.example.data.local.MessageEntity
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary
import java.io.File

/**
 * Full-screen protected viewer for 1x / 2x disappearing messages.
 * Applies WindowManager.LayoutParams.FLAG_SECURE while displayed to block
 * screenshots and screen recordings, and invokes onCloseConsumed() when closed.
 */
@Composable
fun EphemeralMediaViewerDialog(
    message: MessageEntity,
    onCloseConsumed: () -> Unit
) {
    val accent = LocalVeilAccent.current
    val remainingAfterThis = (message.maxViews - (message.viewsConsumed + 1)).coerceAtLeast(0)

    Dialog(
        onDismissRequest = onCloseConsumed,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            dialogWindow?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .safeDrawingPadding()
                .padding(20.dp)
        ) {
            // Top bar with security status and close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassPill(
                    text = if (remainingAfterThis == 0) {
                        "Final View · Wipes on Close"
                    } else {
                        "Protected View · $remainingAfterThis View Left After This"
                    },
                    icon = Icons.Outlined.VisibilityOff,
                    accentColor = accent.highlight
                )
                IconButton(
                    onClick = onCloseConsumed,
                    modifier = Modifier.testTag("ephemeral_viewer_close")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close disappearing message",
                        tint = SnowPrimary
                    )
                }
            }

            // Content body
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp, bottom = 72.dp),
                contentAlignment = Alignment.Center
            ) {
                when (message.contentType) {
                    MessageContentType.IMAGE -> {
                        val file = File(message.localMediaPath)
                        if (file.exists()) {
                            AsyncImage(
                                model = file,
                                contentDescription = "Protected disappearing photo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "Media unavailable or already wiped.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = SlateSecondary)
                            )
                        }
                    }

                    MessageContentType.TEXT -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = message.decryptedText,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = SnowPrimary,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    MessageContentType.LOCATION -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = accent.highlight,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = message.locationLabel.ifBlank { "Shared Location" },
                                    style = MaterialTheme.typography.titleMedium.copy(color = SnowPrimary)
                                )
                                if (message.latitude != null && message.longitude != null) {
                                    Text(
                                        text = "%.5f, %.5f".format(message.latitude, message.longitude),
                                        style = MaterialTheme.typography.bodyMedium.copy(color = SlateSecondary)
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = accent.highlight,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = message.fileName.ifBlank { "Protected Attachment" },
                                    style = MaterialTheme.typography.titleMedium.copy(color = SnowPrimary)
                                )
                                Text(
                                    text = "Encrypted payload will be permanently overwritten when closed.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Bottom security notice
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlassPill(
                    text = "Screenshots Blocked · Zero-Knowledge Ephemeral View",
                    icon = Icons.Outlined.Lock,
                    accentColor = SlateSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}
