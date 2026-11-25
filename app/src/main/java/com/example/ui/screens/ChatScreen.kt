package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.MessageContentType
import com.example.data.local.MessageEntity
import com.example.data.local.SyncStatus
import com.example.media.VoicePlaybackState
import com.example.ui.VeilViewModel
import com.example.ui.components.DocumentMessageContent
import com.example.ui.components.FullScreenImageDialog
import com.example.ui.components.FullScreenVideoDialog
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.ImageMessageContent
import com.example.ui.components.LocationMessageContent
import com.example.ui.components.MonogramAvatar
import com.example.ui.components.TextDocumentPreviewDialog
import com.example.ui.components.VideoMessageContent
import com.example.ui.components.VoiceMessageContent
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.CoralError
import com.example.ui.theme.GlacierMint
import com.example.ui.theme.GlacierMintContainer
import com.example.ui.theme.GlassBorderBottom
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassIncomingSurface
import com.example.ui.theme.GlassOutgoingBorder
import com.example.ui.theme.GlassOutgoingTint
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.IceCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    roomId: String,
    viewModel: VeilViewModel,
    showBackButton: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val room by remember(roomId) { viewModel.observeRoom(roomId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val messages by remember(roomId) { viewModel.observeMessages(roomId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    var draftText by remember { mutableStateOf("") }
    var showAttachTray by remember { mutableStateOf(false) }
    var showVerificationSheet by remember { mutableStateOf(false) }
    var showManualLocationDialog by remember { mutableStateOf(false) }
    var fullScreenImagePath by remember { mutableStateOf<String?>(null) }
    var fullScreenVideoPath by remember { mutableStateOf<String?>(null) }
    var textDocPreview by remember { mutableStateOf<Pair<MessageEntity, String>?>(null) }

    val listState = rememberLazyListState()

    DisposableEffect(roomId) {
        viewModel.startRoomSync(roomId)
        onDispose {
            viewModel.stopRoomSync()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Zero-permission Android Photo Picker (Photos)
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            showAttachTray = false
            viewModel.sendPickedMedia(roomId, uri, isVideo = false)
        }
    }

    // Zero-permission Android Photo Picker (Videos)
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            showAttachTray = false
            viewModel.sendPickedMedia(roomId, uri, isVideo = true)
        }
    }

    // Zero-permission Storage Access Framework (PDF, TXT, ZIP, etc.)
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            showAttachTray = false
            viewModel.sendPickedDocument(roomId, uri)
        }
    }

    // Foreground runtime location permission
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        showAttachTray = false
        if (granted) {
            viewModel.captureAndSendCurrentLocation(roomId) {
                showManualLocationDialog = true
            }
        } else {
            showManualLocationDialog = true
        }
    }

    // Runtime microphone permission for voice notes
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecording()
        } else {
            viewModel.showToast("Microphone permission is needed for voice notes")
        }
    }

    fun openDocumentMessage(msg: MessageEntity) {
        if (msg.localMediaPath.isBlank()) {
            viewModel.showToast("Document file not available locally")
            return
        }
        val lowerName = msg.fileName.lowercase()
        val isPlainText = msg.mimeType.startsWith("text/") ||
            lowerName.endsWith(".txt") ||
            lowerName.endsWith(".md") ||
            lowerName.endsWith(".json") ||
            lowerName.endsWith(".csv") ||
            lowerName.endsWith(".log")
        if (isPlainText) {
            val preview = viewModel.repository.mediaStorage.readTextPreview(msg.localMediaPath)
            if (preview != null) {
                textDocPreview = msg to preview
                return
            }
        }
        openFileExternally(context, viewModel, msg)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Frosted Glass Top Bar
        val activeRoom = room
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(GlassSurfaceElevated)
                .border(1.dp, GlassBorderTop, RoundedCornerShape(22.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("chat_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to rooms",
                        tint = SnowPrimary
                    )
                }
            }

            if (activeRoom != null) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showVerificationSheet = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("chat_header_verify_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MonogramAvatar(label = activeRoom.roomName, size = 38.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeRoom.roomName,
                            style = MaterialTheme.typography.titleMedium.copy(color = SnowPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (activeRoom.isVerified) Icons.Rounded.VerifiedUser else Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = if (activeRoom.isVerified) GlacierMint else SlateSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = viewModel.formatVeilCode(activeRoom.veilCode),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    color = if (activeRoom.isVerified) GlacierMint else SlateSecondary
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            IconButton(
                onClick = { viewModel.syncRoomNow(roomId) },
                modifier = Modifier.testTag("chat_sync_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = "Sync encrypted messages",
                    tint = SlateSecondary
                )
            }
        }

        // Message Feed
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "e2ee_banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(GlassSurfaceSubtle)
                            .border(1.dp, GlacierMint.copy(alpha = 0.22f), RoundedCornerShape(50))
                            .clickable { showVerificationSheet = true }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = GlacierMint,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (activeRoom?.isVerified == true) {
                                "Verified E2EE · DB owner sees only ciphertext"
                            } else {
                                "Zero-knowledge E2EE · Tap to view 12-digit code & safety #"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(color = SlateSecondary)
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                MessageBubbleRow(
                    message = msg,
                    playbackState = playbackState,
                    onOpenImage = { fullScreenImagePath = it },
                    onOpenVideo = { fullScreenVideoPath = it },
                    onOpenDocument = { openDocumentMessage(it) },
                    onToggleVoice = { viewModel.toggleVoicePlayback(msg) },
                    onSeekVoice = { frac -> viewModel.seekVoicePlayback(msg.id, frac) }
                )
            }
        }

        // Expandable Frosted Glass Attachment Tray
        AnimatedVisibility(visible = showAttachTray) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                cornerRadius = 22.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AttachOptionTile(
                        label = "Photo",
                        icon = Icons.Rounded.Image,
                        accent = GlacierMint,
                        testTag = "attach_photo_button",
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    AttachOptionTile(
                        label = "Video",
                        icon = Icons.Rounded.Videocam,
                        accent = IceCyan,
                        testTag = "attach_video_button",
                        onClick = {
                            videoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    )
                    AttachOptionTile(
                        label = "Document",
                        icon = Icons.Rounded.Description,
                        accent = SnowPrimary,
                        testTag = "attach_document_button",
                        onClick = {
                            documentPicker.launch(arrayOf("*/*"))
                        }
                    )
                    AttachOptionTile(
                        label = "Location",
                        icon = Icons.Rounded.LocationOn,
                        accent = GlacierMint,
                        testTag = "attach_location_button",
                        onClick = {
                            val hasFine = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            val hasCoarse = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasFine || hasCoarse) {
                                showAttachTray = false
                                viewModel.captureAndSendCurrentLocation(roomId) {
                                    showManualLocationDialog = true
                                }
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }

        // Frosted Glass Bottom Composer OR Live Voice Note Recorder Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (recordingState.isRecording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(GlassSurfaceElevated)
                        .border(1.dp, CoralError.copy(alpha = 0.45f), RoundedCornerShape(26.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.cancelVoiceRecording() },
                        modifier = Modifier.testTag("voice_cancel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Cancel voice recording",
                            tint = CoralError
                        )
                    }

                    // Live amplitude waveform bars
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val peaks = recordingState.livePeaks.ifEmpty { List(16) { 16 } }
                        peaks.forEach { p ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height((5 + p * 0.2f).dp.coerceIn(5.dp, 24.dp))
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(GlacierMint)
                            )
                        }
                    }

                    Text(
                        text = formatDurationMs(recordingState.elapsedMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            color = CoralError
                        )
                    )

                    IconButton(
                        onClick = { viewModel.finishAndSendVoiceRecording(roomId) },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(GlacierMint)
                            .testTag("voice_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send voice note",
                            tint = ObsidianDeep,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(GlassSurfaceElevated)
                        .border(1.dp, GlassBorderTop, RoundedCornerShape(26.dp))
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showAttachTray = !showAttachTray },
                        modifier = Modifier.testTag("chat_attach_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (showAttachTray) Icons.Rounded.Close else Icons.Rounded.Add,
                            contentDescription = "Attach file, photo, video or location",
                            tint = if (showAttachTray) GlacierMint else SlateSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (draftText.isEmpty()) {
                            Text(
                                text = "Encrypted message...",
                                style = MaterialTheme.typography.bodyLarge.copy(color = SlateMuted)
                            )
                        }
                        BasicTextField(
                            value = draftText,
                            onValueChange = { draftText = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = SnowPrimary),
                            cursorBrush = SolidColor(GlacierMint),
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_message_input")
                        )
                    }

                    if (draftText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val textToSend = draftText
                                draftText = ""
                                viewModel.sendText(roomId, textToSend)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(GlacierMint)
                                .testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send message",
                                tint = ObsidianDeep,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    viewModel.startVoiceRecording()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(GlacierMintContainer)
                                .border(1.dp, GlacierMint.copy(alpha = 0.35f), CircleShape)
                                .testTag("chat_voice_record_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Record voice note",
                                tint = GlacierMint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Verification & 12-Digit Veil Code Sheet
    val currentRoom = room
    if (showVerificationSheet && currentRoom != null) {
        VerificationSheet(
            room = currentRoom,
            viewModel = viewModel,
            onDismiss = { showVerificationSheet = false },
            onRoomDeleted = {
                showVerificationSheet = false
                onBack()
            }
        )
    }

    // Manual Location Fallback Dialog (if GPS unavailable or permission declined)
    if (showManualLocationDialog) {
        ManualLocationDialog(
            onDismiss = { showManualLocationDialog = false },
            onSend = { lat, lon, label ->
                showManualLocationDialog = false
                viewModel.sendManualLocation(roomId, lat, lon, label)
            }
        )
    }

    fullScreenImagePath?.let { path ->
        FullScreenImageDialog(filePath = path, onDismiss = { fullScreenImagePath = null })
    }

    fullScreenVideoPath?.let { path ->
        FullScreenVideoDialog(filePath = path, onDismiss = { fullScreenVideoPath = null })
    }

    textDocPreview?.let { (msg, content) ->
        TextDocumentPreviewDialog(
            title = msg.fileName.ifBlank { "Document" },
            content = content,
            onOpenExternal = { openFileExternally(context, viewModel, msg) },
            onDismiss = { textDocPreview = null }
        )
    }
}

@Composable
private fun MessageBubbleRow(
    message: MessageEntity,
    playbackState: VoicePlaybackState,
    onOpenImage: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenDocument: (MessageEntity) -> Unit,
    onToggleVoice: () -> Unit,
    onSeekVoice: (Float) -> Unit
) {
    val alignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (message.isOutgoing) 20.dp else 6.dp,
        bottomEnd = if (message.isOutgoing) 6.dp else 20.dp
    )
    val bgColor = if (message.isOutgoing) GlassOutgoingTint else GlassIncomingSurface
    val borderColor = if (message.isOutgoing) GlassOutgoingBorder else GlassBorderTop

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 96.dp, max = 304.dp)
                .clip(bubbleShape)
                .background(bgColor, bubbleShape)
                .border(1.dp, borderColor, bubbleShape)
                .padding(horizontal = 13.dp, vertical = 10.dp)
        ) {
            if (!message.isOutgoing && message.senderHandle.isNotBlank()) {
                Text(
                    text = message.senderHandle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = IceCyan,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            when (message.contentType) {
                MessageContentType.TEXT -> {
                    Text(
                        text = message.decryptedText,
                        style = MaterialTheme.typography.bodyLarge.copy(color = SnowPrimary)
                    )
                }
                MessageContentType.IMAGE -> ImageMessageContent(message, onOpenImage)
                MessageContentType.VIDEO -> VideoMessageContent(message, onOpenVideo)
                MessageContentType.DOCUMENT -> DocumentMessageContent(message, onOpenDocument)
                MessageContentType.LOCATION -> LocationMessageContent(message)
                MessageContentType.VOICE -> VoiceMessageContent(
                    message = message,
                    playbackState = playbackState,
                    onTogglePlay = onToggleVoice,
                    onSeek = onSeekVoice
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatMessageTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = SlateMuted
                    )
                )
                if (message.isOutgoing) {
                    val statusIcon = when (message.syncStatus) {
                        SyncStatus.SENT, SyncStatus.SYNCED -> Icons.Rounded.DoneAll
                        SyncStatus.PENDING -> Icons.Rounded.Check
                        SyncStatus.QUEUED_OFFLINE, SyncStatus.FAILED -> Icons.Rounded.Schedule
                    }
                    val statusTint = when (message.syncStatus) {
                        SyncStatus.SENT, SyncStatus.SYNCED -> GlacierMint
                        else -> SlateMuted
                    }
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = message.syncStatus.name,
                        tint = statusTint,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachOptionTile(
    label: String,
    icon: ImageVector,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(accent.copy(alpha = 0.14f))
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(color = SlateSecondary)
        )
    }
}

@Composable
private fun ManualLocationDialog(
    onDismiss: () -> Unit,
    onSend: (Double, Double, String) -> Unit
) {
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("Pinned Location") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianBg,
        titleContentColor = SnowPrimary,
        textContentColor = SlateSecondary,
        title = { Text("Share Coordinates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = "Label",
                    placeholder = "Meeting Point"
                )
                GlassTextField(
                    value = latInput,
                    onValueChange = { latInput = it },
                    label = "Latitude",
                    placeholder = "37.7749",
                    monospace = true,
                    keyboardType = KeyboardType.Decimal
                )
                GlassTextField(
                    value = lonInput,
                    onValueChange = { lonInput = it },
                    label = "Longitude",
                    placeholder = "-122.4194",
                    monospace = true,
                    keyboardType = KeyboardType.Decimal
                )
            }
        },
        confirmButton = {
            val lat = latInput.trim().toDoubleOrNull()
            val lon = lonInput.trim().toDoubleOrNull()
            TextButton(
                enabled = lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0,
                onClick = {
                    if (lat != null && lon != null) {
                        onSend(lat, lon, labelInput)
                    }
                }
            ) {
                Text("Send", color = GlacierMint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SlateSecondary)
            }
        }
    )
}

private fun openFileExternally(
    context: android.content.Context,
    viewModel: VeilViewModel,
    message: MessageEntity
) {
    val uri = viewModel.repository.mediaStorage.getFileProviderUri(message.localMediaPath)
    if (uri == null) {
        viewModel.showToast("File unavailable locally")
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, message.mimeType.ifBlank { "*/*" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Open ${message.fileName}"))
    }.onFailure {
        viewModel.showToast("No app installed to open this file type")
    }
}

private fun formatMessageTime(timestamp: Long): String {
    return runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }.getOrDefault("")
}
