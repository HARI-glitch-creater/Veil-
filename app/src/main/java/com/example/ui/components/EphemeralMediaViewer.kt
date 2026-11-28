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
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.MessageContentType
import com.example.data.local.MessageEntity
import com.example.ui.theme.CoralError
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary
import java.io.File

/**
 * Full-screen protected viewer for 1x / 2x disappearing messages.
 * Applies WindowManager.LayoutParams.FLAG_SECURE while displayed to block
 * screenshots and screen recordings.
 *
 * CRITICAL: Never consumes/wipes a view until encrypted media has finished
 * downloading, decrypting, and rendering on screen AND the user closes the viewer.
 */
@Composable
fun EphemeralMediaViewerDialog(
    message: MessageEntity,
    onEnsureMediaReady: (String, (Result<MessageEntity>) -> Unit) -> Unit,
    onOpenDecryptedFile: (MessageEntity) -> Unit,
    onCloseConsumed: () -> Unit,
    onDismissWithoutConsuming: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalVeilAccent.current
    val remainingAfterThis = (message.maxViews - (message.viewsConsumed + 1)).coerceAtLeast(0)

    var resolvedMessage by remember(message.id) { mutableStateOf(message) }
    var isLoading by remember(message.id) { mutableStateOf(false) }
    var loadError by remember(message.id) { mutableStateOf<String?>(null) }
    var hasRenderedContent by remember(message.id) {
        mutableStateOf(
            message.contentType == MessageContentType.TEXT ||
                message.contentType == MessageContentType.LOCATION
        )
    }
    var retryNonce by remember(message.id) { mutableIntStateOf(0) }

    LaunchedEffect(message.localMediaPath) {
        if (message.localMediaPath.isNotBlank() && resolvedMessage.localMediaPath != message.localMediaPath) {
            resolvedMessage = message
        }
    }

    val needsMediaFile = resolvedMessage.contentType == MessageContentType.IMAGE ||
        resolvedMessage.contentType == MessageContentType.VIDEO ||
        resolvedMessage.contentType == MessageContentType.DOCUMENT ||
        resolvedMessage.contentType == MessageContentType.VOICE

    LaunchedEffect(message.id, retryNonce) {
        if (!needsMediaFile) return@LaunchedEffect
        val existing = resolvedMessage.localMediaPath
            .takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.exists() && it.length() > 0L }
        if (existing != null) {
            isLoading = false
            loadError = null
            if (resolvedMessage.contentType != MessageContentType.IMAGE) {
                hasRenderedContent = true
            }
        } else {
            isLoading = true
            loadError = null
            onEnsureMediaReady(message.id) { result ->
                result.onSuccess { updated ->
                    resolvedMessage = updated
                    val readyFile = updated.localMediaPath
                        .takeIf { it.isNotBlank() }
                        ?.let { File(it) }
                        ?.takeIf { it.exists() && it.length() > 0L }
                    isLoading = false
                    if (readyFile != null) {
                        if (updated.contentType != MessageContentType.IMAGE) {
                            hasRenderedContent = true
                        }
                    } else {
                        loadError = "Unable to locate decrypted file after download."
                    }
                }.onFailure { err ->
                    isLoading = false
                    loadError = err.message ?: "Unable to download and decrypt media."
                }
            }
        }
    }

    val handleClose: () -> Unit = {
        if (hasRenderedContent) {
            onCloseConsumed()
        } else {
            onDismissWithoutConsuming()
        }
    }

    Dialog(
        onDismissRequest = handleClose,
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
                val statusLabel = when {
                    !hasRenderedContent && isLoading -> "Decrypting Protected View..."
                    !hasRenderedContent && loadError != null -> "View Not Consumed"
                    remainingAfterThis == 0 -> "Final View · Wipes on Close"
                    else -> "Protected View · $remainingAfterThis View Left After This"
                }
                GlassPill(
                    text = statusLabel,
                    icon = Icons.Outlined.VisibilityOff,
                    accentColor = accent.highlight
                )
                IconButton(
                    onClick = handleClose,
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
                when (resolvedMessage.contentType) {
                    MessageContentType.IMAGE -> {
                        val readyFile = resolvedMessage.localMediaPath
                            .takeIf { it.isNotBlank() }
                            ?.let { File(it) }
                            ?.takeIf { it.exists() && it.length() > 0L }

                        when {
                            isLoading -> {
                                EphemeralLoadingState(label = "Downloading & decrypting photo...")
                            }
                            loadError != null -> {
                                EphemeralErrorState(
                                    error = loadError.orEmpty(),
                                    onRetry = { retryNonce++ }
                                )
                            }
                            readyFile != null -> {
                                val stamp = readyFile.lastModified()
                                val imageRequest = remember(readyFile.absolutePath, stamp) {
                                    ImageRequest.Builder(context)
                                        .data(readyFile)
                                        .memoryCacheKey("${readyFile.absolutePath}:$stamp")
                                        .diskCacheKey("${readyFile.absolutePath}:$stamp")
                                        .crossfade(true)
                                        .build()
                                }
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!hasRenderedContent) {
                                        EphemeralLoadingState(label = "Rendering decrypted photo...")
                                    }
                                    AsyncImage(
                                        model = imageRequest,
                                        contentDescription = "Protected disappearing photo",
                                        contentScale = ContentScale.Fit,
                                        onSuccess = { hasRenderedContent = true },
                                        onError = { loadError = "Unable to decode decrypted image." },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            else -> {
                                EphemeralLoadingState(label = "Preparing encrypted photo...")
                            }
                        }
                    }

                    MessageContentType.TEXT -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = resolvedMessage.decryptedText,
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
                                    text = resolvedMessage.locationLabel.ifBlank { "Shared Location" },
                                    style = MaterialTheme.typography.titleMedium.copy(color = SnowPrimary)
                                )
                                if (resolvedMessage.latitude != null && resolvedMessage.longitude != null) {
                                    Text(
                                        text = "%.5f, %.5f".format(resolvedMessage.latitude, resolvedMessage.longitude),
                                        style = MaterialTheme.typography.bodyMedium.copy(color = SlateSecondary)
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        when {
                            isLoading -> {
                                EphemeralLoadingState(label = "Downloading & decrypting attachment...")
                            }
                            loadError != null -> {
                                EphemeralErrorState(
                                    error = loadError.orEmpty(),
                                    onRetry = { retryNonce++ }
                                )
                            }
                            else -> {
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Description,
                                            contentDescription = null,
                                            tint = accent.highlight,
                                            modifier = Modifier.size(38.dp)
                                        )
                                        Text(
                                            text = resolvedMessage.fileName.ifBlank { "Protected Attachment" },
                                            style = MaterialTheme.typography.titleMedium.copy(color = SnowPrimary),
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "Decrypted locally. Encrypted payload will be wiped when closed.",
                                            style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary),
                                            textAlign = TextAlign.Center
                                        )
                                        GlassPrimaryButton(
                                            text = "Open Protected File",
                                            icon = Icons.Rounded.OpenInNew,
                                            onClick = { onOpenDecryptedFile(resolvedMessage) },
                                            tag = "ephemeral_open_file_button"
                                        )
                                    }
                                }
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

@Composable
private fun EphemeralLoadingState(label: String) {
    val accent = LocalVeilAccent.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircularProgressIndicator(
            color = accent.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(38.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(color = SlateSecondary),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EphemeralErrorState(
    error: String,
    onRetry: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = CoralError,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Could not load encrypted media",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = SnowPrimary,
                    fontWeight = FontWeight.SemiBold
                ),
                textAlign = TextAlign.Center
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Your remaining views have not been consumed.",
                style = MaterialTheme.typography.labelSmall.copy(color = LocalVeilAccent.current.highlight),
                textAlign = TextAlign.Center
            )
            GlassPrimaryButton(
                text = "Retry Download",
                icon = Icons.Rounded.Refresh,
                onClick = onRetry,
                tag = "ephemeral_retry_download_button"
            )
        }
    }
}
