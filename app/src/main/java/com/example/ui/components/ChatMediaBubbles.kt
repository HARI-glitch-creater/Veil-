package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.MessageEntity
import com.example.media.VoicePlaybackState
import com.example.ui.theme.GlacierMint
import com.example.ui.theme.GlacierMintContainer
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.IceCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary
import java.io.File

@Composable
fun ImageMessageContent(
    message: MessageEntity,
    onOpenImage: (String) -> Unit
) {
    val file = remember(message.localMediaPath) {
        if (message.localMediaPath.isNotBlank()) File(message.localMediaPath) else null
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(GlassSurfaceSubtle)
                .border(1.dp, GlassBorderTop, RoundedCornerShape(14.dp))
                .clickable(enabled = file != null && file.exists()) {
                    onOpenImage(message.localMediaPath)
                },
            contentAlignment = Alignment.Center
        ) {
            if (file != null && file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = message.fileName.ifBlank { "Encrypted photo" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "Encrypted photo",
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                )
            }
        }
        if (message.fileName.isNotBlank()) {
            Text(
                text = message.fileName,
                style = MaterialTheme.typography.labelSmall.copy(color = SlateSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun VideoMessageContent(
    message: MessageEntity,
    onOpenVideo: (String) -> Unit
) {
    val hasLocal = message.localMediaPath.isNotBlank() && File(message.localMediaPath).exists()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianDeep)
            .border(1.dp, GlassBorderTop, RoundedCornerShape(14.dp))
            .clickable(enabled = hasLocal) { onOpenVideo(message.localMediaPath) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(GlacierMintContainer)
                    .border(1.dp, GlacierMint.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play video",
                    tint = GlacierMint,
                    modifier = Modifier.size(28.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = null,
                    tint = SlateSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = message.fileName.ifBlank { "Encrypted Video" },
                    style = MaterialTheme.typography.bodySmall.copy(color = SnowPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (message.fileSizeBytes > 0L) {
                Text(
                    text = formatFileSize(message.fileSizeBytes),
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                )
            }
        }
    }
}

@Composable
fun DocumentMessageContent(
    message: MessageEntity,
    onOpenDocument: (MessageEntity) -> Unit
) {
    val ext = remember(message.fileName) {
        message.fileName.substringAfterLast('.', "FILE").uppercase().take(5)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurfaceSubtle)
            .border(1.dp, GlassBorderTop, RoundedCornerShape(14.dp))
            .clickable { onOpenDocument(message) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(IceCyan.copy(alpha = 0.14f))
                .border(1.dp, IceCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = IceCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = ext,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        color = IceCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message.fileName.ifBlank { "Encrypted Document" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = SnowPrimary,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${formatFileSize(message.fileSizeBytes)} · Tap to open",
                style = MaterialTheme.typography.labelSmall.copy(color = SlateSecondary)
            )
        }

        Icon(
            imageVector = Icons.Rounded.OpenInNew,
            contentDescription = "Open document",
            tint = SlateSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun LocationMessageContent(
    message: MessageEntity
) {
    val context = LocalContext.current
    val lat = message.latitude ?: 0.0
    val lon = message.longitude ?: 0.0
    val coords = remember(lat, lon) { "%.5f, %.5f".format(lat, lon) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianDeep)
            .border(1.dp, GlassBorderTop, RoundedCornerShape(14.dp))
            .clickable {
                val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(message.locationLabel.ifBlank { "Shared Location" })})")
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                }
            }
    ) {
        // Minimalist dark vector radar grid card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 22.dp.toPx()
                val gridColor = Color(0x16FFFFFF)
                var x = step
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
                drawCircle(
                    color = GlacierMint.copy(alpha = 0.16f),
                    radius = 34.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = GlacierMint.copy(alpha = 0.34f),
                    radius = 18.dp.toPx(),
                    center = center
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(GlacierMint)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = "Shared location pin",
                    tint = ObsidianDeep,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = message.locationLabel.ifBlank { "Encrypted Location" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = SnowPrimary,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = coords,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    color = GlacierMint
                )
            )
        }
    }
}

@Composable
fun VoiceMessageContent(
    message: MessageEntity,
    playbackState: VoicePlaybackState,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit
) {
    val isThisPlaying = playbackState.playingMessageId == message.id
    val progress = if (isThisPlaying) playbackState.progress else 0f
    val peaks = remember(message.waveformPeaks) {
        val parsed = message.waveformPeaks
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
        if (parsed.size >= 8) parsed else List(24) { 22 + (it * 19) % 68 }
    }

    val displayDurationMs = if (isThisPlaying && playbackState.isPlaying) {
        playbackState.currentPositionMs
    } else {
        message.audioDurationMs.coerceAtLeast(1000L)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(
            onClick = onTogglePlay,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GlacierMintContainer)
                .border(1.dp, GlacierMint.copy(alpha = 0.45f), CircleShape)
        ) {
            Icon(
                imageVector = if (isThisPlaying && playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isThisPlaying && playbackState.isPlaying) "Pause voice note" else "Play voice note",
                tint = GlacierMint,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                peaks.forEachIndexed { idx, peak ->
                    val frac = idx.toFloat() / peaks.size.coerceAtLeast(1).toFloat()
                    val active = isThisPlaying && frac <= progress
                    val barHeight = (6 + (peak * 0.22f)).dp.coerceIn(5.dp, 26.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(barHeight)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) GlacierMint else SlateMuted.copy(alpha = 0.55f))
                            .clickable { onSeek(frac) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDurationMs(displayDurationMs),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    color = SlateSecondary
                )
            )
        }
    }
}

@Composable
fun FullScreenImageDialog(
    filePath: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            AsyncImage(
                model = File(filePath),
                contentDescription = "Full screen encrypted photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(GlassSurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close image",
                    tint = SnowPrimary
                )
            }
        }
    }
}

@Composable
fun FullScreenVideoDialog(
    filePath: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(filePath)
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setOnPreparedListener { start() }
                    }
                }
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(GlassSurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close video",
                    tint = SnowPrimary
                )
            }
        }
    }
}

@Composable
fun TextDocumentPreviewDialog(
    title: String,
    content: String,
    onOpenExternal: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(color = SnowPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenExternal) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = "Open in external app",
                        tint = GlacierMint
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close document",
                        tint = SnowPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            GlassCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        color = SnowPrimary,
                        lineHeight = 19.sp
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return "%.2f MB".format(mb)
}

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return "%d:%02d".format(mins, secs)
}
