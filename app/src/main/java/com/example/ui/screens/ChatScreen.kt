package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.crypto.VeilCryptoEngine
import com.example.data.local.ChannelType
import com.example.data.local.CommunityEntity
import com.example.data.local.MessageContentType
import com.example.data.local.MessageEntity
import com.example.data.local.RoomMemberEntity
import com.example.media.VoicePlaybackState
import com.example.media.VoiceRecordingState
import com.example.ui.components.EphemeralMediaViewerDialog
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.MessageBubbleCard
import com.example.ui.components.ProfileAvatar
import com.example.ui.theme.CoralError
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File

@Composable
fun ChatScreen(
    roomName: String,
    channelType: ChannelType,
    directPeer: RoomMemberEntity?,
    community: CommunityEntity?,
    messages: List<MessageEntity>,
    recordingState: VoiceRecordingState,
    playbackState: VoicePlaybackState,
    onBack: () -> Unit,
    onInspectPeer: (() -> Unit)?,
    onStartVoiceCall: (() -> Unit)?,
    onSendText: (String, Int) -> Unit,
    onSendMediaUri: (Uri, MessageContentType, Int) -> Unit,
    onCreateCameraTarget: () -> Pair<File, Uri>,
    onSendCapturedPhoto: (File, Int) -> Unit,
    onSendLocation: (Double, Double, String, Int) -> Unit,
    onStartVoiceRecord: () -> Unit,
    onStopSendVoiceRecord: (Int) -> Unit,
    onCancelVoiceRecord: () -> Unit,
    onToggleVoicePlay: (String, String) -> Unit,
    onSeekVoice: (String, Float) -> Unit,
    onOpenStoredMedia: (MessageEntity) -> Unit,
    onConsumeEphemeralView: (String) -> Unit,
    onPostStatus: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val accent = LocalVeilAccent.current
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var showAttachmentDrawer by remember { mutableStateOf(false) }
    // 0 = Normal, 1 = 1x View, 2 = 2x View
    var viewLimitMode by remember { mutableIntStateOf(0) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var activeEphemeralMessage by remember { mutableStateOf<MessageEntity?>(null) }

    val isPeerBlocked = channelType == ChannelType.DIRECT && directPeer?.isBlocked == true

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Media / Camera / Files / Mic / Location Launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSendMediaUri(uri, MessageContentType.IMAGE, viewLimitMode)
            showAttachmentDrawer = false
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onSendMediaUri(uri, MessageContentType.DOCUMENT, viewLimitMode)
            showAttachmentDrawer = false
        }
    }

    val browseFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onSendMediaUri(uri, MessageContentType.DOCUMENT, viewLimitMode)
            showAttachmentDrawer = false
        }
    }

    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val file = pendingCameraFile
        if (ok && file != null && file.exists()) {
            onSendCapturedPhoto(file, viewLimitMode)
            showAttachmentDrawer = false
        }
        pendingCameraFile = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (file, uri) = onCreateCameraTarget()
            pendingCameraFile = file
            cameraCaptureLauncher.launch(uri)
        } else {
            onPostStatus("Camera permission is required to capture a live photo.")
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartVoiceRecord()
        else onPostStatus("Microphone permission is required for voice notes.")
    }

    @SuppressLint("MissingPermission")
    fun fetchAndSendLocation() {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    onSendLocation(loc.latitude, loc.longitude, "Current Location", viewLimitMode)
                    showAttachmentDrawer = false
                } else {
                    fused.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            onSendLocation(last.latitude, last.longitude, "Last Known Location", viewLimitMode)
                            showAttachmentDrawer = false
                        } else {
                            onPostStatus("Location fix unavailable. Ensure GPS is enabled.")
                        }
                    }
                }
            }
            .addOnFailureListener {
                onPostStatus("Unable to read location: ${it.localizedMessage}")
            }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            fetchAndSendLocation()
        } else {
            onPostStatus("Location permission is required to share coordinates.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Chat Top Bar (NO video call button — strictly Voice Call only for DIRECT)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassSurfaceElevated.copy(alpha = 0.88f))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("chat_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = SnowPrimary
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (onInspectPeer != null) Modifier.clickable(onClick = onInspectPeer) else Modifier
                    )
                    .padding(vertical = 2.dp, horizontal = 4.dp)
                    .testTag("chat_header_title"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (channelType == ChannelType.DIRECT && directPeer != null) {
                    ProfileAvatar(
                        localPfpPath = directPeer.localPfpPath,
                        username = directPeer.username,
                        size = 40.dp,
                        showOnlineDot = directPeer.isOnline && !directPeer.isBlocked
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.container),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (community?.isDefault == true) Icons.Rounded.Public else Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = accent.highlight,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (channelType == ChannelType.DIRECT) {
                            directPeer?.username ?: "Direct Chat"
                        } else {
                            community?.name ?: "$roomName — General"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = SnowPrimary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = accent.highlight,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (channelType == ChannelType.DIRECT) {
                                if (isPeerBlocked) "Blocked · Tap header to manage" else "Encrypted · Tap to inspect profile"
                            } else {
                                "${community?.memberCount ?: 1} profiles · $roomName"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(color = SlateSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (channelType == ChannelType.DIRECT && onStartVoiceCall != null && !isPeerBlocked) {
                IconButton(
                    onClick = onStartVoiceCall,
                    modifier = Modifier.testTag("chat_voice_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = "Start encrypted voice call",
                        tint = accent.highlight
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassDivider))

        // Message Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassPill(
                        text = "Zero-Knowledge AES-256-GCM Encrypted",
                        icon = Icons.Outlined.Lock,
                        accentColor = SlateSecondary
                    )
                }
            }

            items(messages, key = { it.id }) { msg ->
                MessageBubbleCard(
                    message = msg,
                    showSenderName = channelType == ChannelType.COMMUNITY,
                    playbackState = playbackState,
                    onOpenEphemeral = { activeEphemeralMessage = it },
                    onOpenMedia = onOpenStoredMedia,
                    onOpenLocation = { lat, lng, label ->
                        val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                        }
                    },
                    onToggleVoicePlay = onToggleVoicePlay,
                    onSeekVoice = onSeekVoice
                )
            }
        }

        // Expandable 6-Tile '+' Attachment Drawer
        AnimatedVisibility(visible = showAttachmentDrawer && !isPeerBlocked) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                cornerRadius = 22.dp,
                containerColor = GlassSurfaceElevated,
                contentPadding = PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Encrypted Attachments",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = SnowPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        GlassPill(
                            text = viewTimerLabel(viewLimitMode),
                            icon = Icons.Outlined.VisibilityOff,
                            accentColor = if (viewLimitMode > 0) accent.highlight else SlateSecondary,
                            onClick = { viewLimitMode = (viewLimitMode + 1) % 3 }
                        )
                    }

                    // Row 1: Live Photo, Gallery, Document
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AttachmentTile(
                            icon = Icons.Rounded.PhotoCamera,
                            label = "Live Photo",
                            onClick = {
                                val hasCam = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasCam) {
                                    val (file, uri) = onCreateCameraTarget()
                                    pendingCameraFile = file
                                    cameraCaptureLauncher.launch(uri)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            tag = "attach_tile_live_photo"
                        )
                        AttachmentTile(
                            icon = Icons.Rounded.PhotoLibrary,
                            label = "Gallery",
                            onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            tag = "attach_tile_gallery"
                        )
                        AttachmentTile(
                            icon = Icons.Rounded.Description,
                            label = "Document",
                            onClick = {
                                documentLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "text/plain",
                                        "application/msword",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            tag = "attach_tile_document"
                        )
                    }

                    // Row 2: Browse Files, Location, View Timer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AttachmentTile(
                            icon = Icons.Rounded.FolderOpen,
                            label = "Browse Files",
                            onClick = { browseFilesLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f),
                            tag = "attach_tile_browse_files"
                        )
                        AttachmentTile(
                            icon = Icons.Rounded.LocationOn,
                            label = "Location",
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
                                    fetchAndSendLocation()
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            tag = "attach_tile_location"
                        )
                        AttachmentTile(
                            icon = Icons.Outlined.VisibilityOff,
                            label = viewTimerLabel(viewLimitMode),
                            highlighted = viewLimitMode > 0,
                            onClick = { viewLimitMode = (viewLimitMode + 1) % 3 },
                            modifier = Modifier.weight(1f),
                            tag = "attach_tile_view_timer"
                        )
                    }
                }
            }
        }

        // Composer Bar
        if (isPeerBlocked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassSurfaceElevated)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "You blocked ${directPeer?.username}. Unblock them in Profile Inspector to message again.",
                    style = MaterialTheme.typography.bodySmall.copy(color = CoralError)
                )
            }
        } else if (recordingState.isRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onCancelVoiceRecord,
                    modifier = Modifier.testTag("voice_record_cancel")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Cancel voice recording",
                        tint = CoralError
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(CoralError)
                )
                Text(
                    text = "Recording Voice · %d:%02d".format(
                        (recordingState.elapsedMs / 1000L) / 60L,
                        (recordingState.elapsedMs / 1000L) % 60L
                    ),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFamily,
                        color = SnowPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onStopSendVoiceRecord(viewLimitMode) },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.primary)
                        .testTag("voice_record_send")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send encrypted voice note",
                        tint = ObsidianDeep
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassSurfaceElevated.copy(alpha = 0.90f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Compact Disappearing View Timer pill when active (or inline toggle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { showAttachmentDrawer = !showAttachmentDrawer },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (showAttachmentDrawer) accent.container else GlassSurfaceSubtle)
                            .testTag("chat_attach_plus_button")
                    ) {
                        Icon(
                            imageVector = if (showAttachmentDrawer) Icons.Rounded.Close else Icons.Rounded.Add,
                            contentDescription = "Open attachment drawer",
                            tint = if (showAttachmentDrawer) accent.highlight else SnowPrimary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(GlassSurfaceSubtle)
                            .border(1.dp, GlassBorderTop, RoundedCornerShape(22.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "Encrypted message...",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                                )
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = VeilCryptoEngine.stripEmojis(it) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = SnowPrimary),
                                cursorBrush = SolidColor(accent.highlight),
                                maxLines = 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("chat_message_input")
                            )
                        }

                        // Compact View-Limit Cycle Pill (Normal -> 1x View -> 2x View)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (viewLimitMode > 0) accent.container else GlassSurfaceElevated)
                                .border(
                                    width = 1.dp,
                                    color = if (viewLimitMode > 0) accent.highlight else GlassBorderTop,
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable { viewLimitMode = (viewLimitMode + 1) % 3 }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("chat_view_limit_toggle")
                        ) {
                            Text(
                                text = when (viewLimitMode) {
                                    1 -> "1x"
                                    2 -> "2x"
                                    else -> "Off"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    color = if (viewLimitMode > 0) accent.highlight else SlateMuted,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    if (inputText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val toSend = inputText.trim()
                                inputText = ""
                                onSendText(toSend, viewLimitMode)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(accent.primary)
                                .testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send encrypted message",
                                tint = ObsidianDeep
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) onStartVoiceRecord()
                                else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(accent.primary)
                                .testTag("chat_voice_record_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Record encrypted voice note",
                                tint = ObsidianDeep
                            )
                        }
                    }
                }
            }
        }
    }

    // FLAG_SECURE Ephemeral Media & Text Viewer
    val viewingEphemeral = activeEphemeralMessage
    if (viewingEphemeral != null) {
        EphemeralMediaViewerDialog(
            message = viewingEphemeral,
            onCloseConsumed = {
                val id = viewingEphemeral.id
                activeEphemeralMessage = null
                onConsumeEphemeralView(id)
            }
        )
    }
}

@Composable
private fun AttachmentTile(
    icon: ImageVector,
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String
) {
    val accent = LocalVeilAccent.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) accent.container else GlassSurfaceSubtle)
            .border(
                width = 1.dp,
                color = if (highlighted) accent.highlight else GlassBorderTop,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (highlighted) accent.highlight else SnowPrimary,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (highlighted) accent.highlight else SlateSecondary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun viewTimerLabel(mode: Int): String = when (mode) {
    1 -> "1x View"
    2 -> "2x View"
    else -> "View Timer: Off"
}
