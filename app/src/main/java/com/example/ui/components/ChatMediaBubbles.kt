package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.MessageContentType
import com.example.data.local.MessageEntity
import com.example.data.local.SyncStatus
import com.example.media.VoicePlaybackState
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassIncomingSurface
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubbleCard(
    message: MessageEntity,
    showSenderName: Boolean,
    playbackState: VoicePlaybackState,
    isDownloading: Boolean = false,
    onAutoLoadMedia: (MessageEntity) -> Unit = {},
    onOpenEphemeral: (MessageEntity) -> Unit,
    onOpenMedia: (MessageEntity) -> Unit,
    onOpenLocation: (Double, Double, String) -> Unit,
    onToggleVoicePlay: (MessageEntity) -> Unit,
    onSeekVoice: (String, Float) -> Unit
) {
    val accent = LocalVeilAccent.current
    val isOutgoing = message.isOutgoing
    val shape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isOutgoing) 20.dp else 6.dp,
        bottomEnd = if (isOutgoing) 6.dp else 20.dp
    )
    val bgBrush = if (isOutgoing) {
        Brush.verticalGradient(listOf(accent.outgoingBubble.copy(alpha = 0.28f), accent.outgoingBubble))
    } else {
        Brush.verticalGradient(listOf(GlassSurfaceElevated, GlassIncomingSurface))
    }
    val borderColor = if (isOutgoing) accent.outgoingBorder else GlassBorderTop

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 96.dp, max = 304.dp)
                .clip(shape)
                .background(bgBrush, shape)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showSenderName && !isOutgoing) {
                Text(
                    text = message.senderHandle,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = accent.highlight,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // Ephemeral / Disappearing message state handling (1x or 2x views)
            if (message.maxViews > 0) {
                EphemeralMessageBubbleBody(
                    message = message,
                    onTapOpen = { onOpenEphemeral(message) }
                )
            } else {
                when (message.contentType) {
                    MessageContentType.TEXT -> {
                        Text(
                            text = message.decryptedText.ifBlank { "[Encrypted payload]" },
                            style = MaterialTheme.typography.bodyMedium.copy(color = SnowPrimary)
                        )
                    }

                    MessageContentType.IMAGE -> {
                        val context = LocalContext.current
                        val file = message.localMediaPath
                            .takeIf { it.isNotBlank() }
                            ?.let { File(it) }
                            ?.takeIf { it.exists() && it.length() > 0L }
                        if (file == null && (message.remoteMediaUrl.isNotBlank() || message.ciphertextPayload.isNotBlank())) {
                            LaunchedEffect(message.id, message.remoteMediaUrl) {
                                onAutoLoadMedia(message)
                            }
                        }
                        if (file != null) {
                            val stamp = file.lastModified()
                            val request = remember(file.absolutePath, stamp) {
                                ImageRequest.Builder(context)
                                    .data(file)
                                    .memoryCacheKey("${file.absolutePath}:$stamp")
                                    .diskCacheKey("${file.absolutePath}:$stamp")
                                    .crossfade(true)
                                    .build()
                            }
                            AsyncImage(
                                model = request,
                                contentDescription = message.fileName.ifBlank { "Encrypted photo" },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onOpenMedia(message) }
                            )
                        } else {
                            MediaFallbackTile(
                                icon = Icons.Rounded.Description,
                                title = message.fileName.ifBlank { "Encrypted Photo" },
                                subtitle = if (isDownloading) {
                                    "Downloading & decrypting..."
                                } else {
                                    "Tap to download · ${formatBytes(message.fileSizeBytes)}"
                                },
                                isLoading = isDownloading,
                                onClick = { onOpenMedia(message) }
                            )
                        }
                    }

                    MessageContentType.VIDEO -> {
                        val isCached = message.localMediaPath.isNotBlank() &&
                            File(message.localMediaPath).let { it.exists() && it.length() > 0L }
                        MediaFallbackTile(
                            icon = Icons.Rounded.Videocam,
                            title = message.fileName.ifBlank { "Encrypted Video" },
                            subtitle = when {
                                isDownloading -> "Downloading & decrypting..."
                                isCached -> "Tap to play · ${formatBytes(message.fileSizeBytes)}"
                                else -> "Tap to download & play · ${formatBytes(message.fileSizeBytes)}"
                            },
                            isLoading = isDownloading,
                            onClick = { onOpenMedia(message) }
                        )
                    }

                    MessageContentType.DOCUMENT -> {
                        val isCached = message.localMediaPath.isNotBlank() &&
                            File(message.localMediaPath).let { it.exists() && it.length() > 0L }
                        MediaFallbackTile(
                            icon = Icons.Rounded.Description,
                            title = message.fileName.ifBlank { "Encrypted File" },
                            subtitle = when {
                                isDownloading -> "Downloading & decrypting..."
                                isCached -> "${message.mimeType.ifBlank { "Document" }} · ${formatBytes(message.fileSizeBytes)}"
                                else -> "Tap to download · ${formatBytes(message.fileSizeBytes)}"
                            },
                            isLoading = isDownloading,
                            onClick = { onOpenMedia(message) }
                        )
                    }

                    MessageContentType.LOCATION -> {
                        val lat = message.latitude ?: 0.0
                        val lng = message.longitude ?: 0.0
                        val label = message.locationLabel.ifBlank { "Shared Location" }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(GlassSurfaceSubtle)
                                .clickable { onOpenLocation(lat, lng, label) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(accent.container),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = accent.highlight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge.copy(color = SnowPrimary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "%.5f, %.5f".format(lat, lng),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        color = SlateSecondary
                                    )
                                )
                            }
                        }
                    }

                    MessageContentType.VOICE -> {
                        VoiceNoteBubbleBody(
                            message = message,
                            playbackState = playbackState,
                            isDownloading = isDownloading,
                            onTogglePlay = { onToggleVoicePlay(message) },
                            onSeek = { frac -> onSeekVoice(message.id, frac) }
                        )
                    }
                }
            }

            // Timestamp + sync ticks
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatMessageTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        color = SlateMuted
                    )
                )
                if (isOutgoing) {
                    val tickIcon = when (message.syncStatus) {
                        SyncStatus.SENT, SyncStatus.SYNCED -> Icons.Rounded.DoneAll
                        SyncStatus.PENDING -> Icons.Rounded.Done
                        else -> Icons.Rounded.Schedule
                    }
                    Icon(
                        imageVector = tickIcon,
                        contentDescription = message.syncStatus.name,
                        tint = if (message.syncStatus == SyncStatus.SENT || message.syncStatus == SyncStatus.SYNCED) {
                            accent.highlight
                        } else SlateMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EphemeralMessageBubbleBody(
    message: MessageEntity,
    onTapOpen: () -> Unit
) {
    val accent = LocalVeilAccent.current
    val remaining = (message.maxViews - message.viewsConsumed).coerceAtLeast(0)
    val disappeared = message.isDisappeared || remaining == 0

    if (disappeared) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(GlassSurfaceSubtle)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.VisibilityOff,
                contentDescription = null,
                tint = SlateMuted,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Opened · Disappeared",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = SlateMuted,
                    fontStyle = FontStyle.Italic
                ),
                modifier = Modifier.testTag("ephemeral_disappeared_${message.id}")
            )
        }
    } else {
        val noun = when (message.contentType) {
            MessageContentType.IMAGE -> "Photo"
            MessageContentType.VIDEO -> "Video"
            MessageContentType.VOICE -> "Voice Note"
            MessageContentType.DOCUMENT -> "File"
            MessageContentType.LOCATION -> "Location"
            MessageContentType.TEXT -> "Message"
        }
        val title = if (message.maxViews == 1) "View-Once $noun" else "${message.maxViews}-View $noun"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(accent.container)
                .border(1.dp, accent.highlight.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .clickable(onClick = onTapOpen)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("ephemeral_locked_${message.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = ObsidianDeep,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = SnowPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = if (remaining == 1) "Tap to open · 1 view remaining" else "Tap to open · $remaining views remaining",
                    style = MaterialTheme.typography.labelSmall.copy(color = accent.highlight)
                )
            }
        }
    }
}

@Composable
private fun MediaFallbackTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val accent = LocalVeilAccent.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurfaceSubtle)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.container),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = accent.highlight
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent.highlight,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(color = SnowPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(color = SlateSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VoiceNoteBubbleBody(
    message: MessageEntity,
    playbackState: VoicePlaybackState,
    isDownloading: Boolean = false,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit
) {
    val accent = LocalVeilAccent.current
    val isThisPlaying = playbackState.playingMessageId == message.id && playbackState.isPlaying
    val progress = if (playbackState.playingMessageId == message.id) playbackState.progress else 0f
    val peaks = remember(message.waveformPeaks) {
        message.waveformPeaks.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .ifEmpty { List(24) { 25 + (it * 17) % 60 } }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(
            onClick = onTogglePlay,
            enabled = !isDownloading,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accent.primary)
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = ObsidianDeep
                )
            } else {
                Icon(
                    imageVector = if (isThisPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isThisPlaying) "Pause voice note" else "Play voice note",
                    tint = ObsidianDeep,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(message.id) {
                        detectTapGestures { offset ->
                            if (size.width > 0) {
                                onSeek((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
            ) {
                val count = peaks.size
                val barW = (size.width / (count * 1.6f)).coerceAtLeast(3f)
                val gap = (size.width - count * barW) / (count - 1).coerceAtLeast(1)
                peaks.forEachIndexed { index, amp ->
                    val frac = index.toFloat() / count.toFloat()
                    val h = (size.height * (amp / 100f)).coerceIn(6f, size.height)
                    val x = index * (barW + gap)
                    val y = (size.height - h) / 2f
                    drawRoundRect(
                        color = if (frac <= progress) accent.highlight else SlateMuted.copy(alpha = 0.55f),
                        topLeft = Offset(x, y),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                    )
                }
            }
            Text(
                text = if (isDownloading) "Decrypting voice note..." else formatAudioDuration(message.audioDurationMs),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    color = SlateSecondary
                )
            )
        }
    }
}

private fun formatMessageTime(timestampMs: Long): String {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return fmt.format(Date(timestampMs))
}

private fun formatAudioDuration(durationMs: Long): String {
    val secs = (durationMs / 1000L).coerceAtLeast(1L)
    return "%d:%02d".format(secs / 60L, secs % 60L)
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes <= 0L -> "Encrypted"
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
