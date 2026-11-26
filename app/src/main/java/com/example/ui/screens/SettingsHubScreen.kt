package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.crypto.VeilCryptoEngine
import com.example.data.local.UserProfileEntity
import com.example.data.local.VeilRoomEntity
import com.example.data.network.VeilBackendClient
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.QrCodeView
import com.example.ui.components.VeilColorThemeCustomizer
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralError
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsHubScreen(
    userProfile: UserProfileEntity?,
    publicKeyFingerprint: String,
    identityUri: String,
    rooms: List<VeilRoomEntity>,
    activeRoomId: String?,
    accentColor: Color,
    isCheckingUpdate: Boolean,
    onBack: () -> Unit,
    onCreateCameraTarget: () -> Pair<File, Uri>,
    onCompressAvatarUri: (Uri, (String) -> Unit) -> Unit,
    onSaveProfile: (username: String, motto: String, gender: String, localPfpPath: String) -> Unit,
    onSwitchRoom: (String) -> Unit,
    onDeleteRoom: (String) -> Unit,
    onHostNewRoom: () -> Unit,
    onJoinNewRoom: () -> Unit,
    onSelectAccentColor: (Color) -> Unit,
    onOpenBackendSettings: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onPostStatus: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val accent = LocalVeilAccent.current

    var username by remember(userProfile) { mutableStateOf(userProfile?.username.orEmpty()) }
    var motto by remember(userProfile) { mutableStateOf(userProfile?.motto.orEmpty()) }
    var gender by remember(userProfile) { mutableStateOf(userProfile?.gender ?: "Prefer not to say") }
    var localPfpPath by remember(userProfile) { mutableStateOf(userProfile?.localPfpPath.orEmpty()) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onCompressAvatarUri(uri) { path -> localPfpPath = path }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        val uri = pendingCameraUri
        if (captured && uri != null) {
            onCompressAvatarUri(uri) { path -> localPfpPath = path }
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (_, uri) = onCreateCameraTarget()
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    val wordCount = remember(motto) { VeilCryptoEngine.countWords(motto) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = SnowPrimary
                    )
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = SnowPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // SECTION 1: Edit My Profile + Device Binding Badge + Safety QR
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "My Profile",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SnowPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        GlassPill(
                            text = "Bound to this device",
                            icon = Icons.Rounded.VerifiedUser,
                            accentColor = accent.highlight
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ProfileAvatar(
                            localPfpPath = localPfpPath,
                            username = username.ifBlank { "Veil" },
                            size = 76.dp
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassSecondaryButton(
                                    text = "Gallery",
                                    icon = Icons.Outlined.PhotoLibrary,
                                    onClick = {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    tag = "settings_pfp_gallery"
                                )
                                GlassSecondaryButton(
                                    text = "Camera",
                                    icon = Icons.Outlined.PhotoCamera,
                                    onClick = {
                                        val ok = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (ok) {
                                            val (_, uri) = onCreateCameraTarget()
                                            pendingCameraUri = uri
                                            cameraLauncher.launch(uri)
                                        } else {
                                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    tag = "settings_pfp_camera"
                                )
                            }
                            Text(
                                text = "Encrypted 512x512 JPEG synced across rooms",
                                style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                            )
                        }
                    }

                    GlassTextField(
                        value = username,
                        onValueChange = { username = VeilCryptoEngine.stripEmojis(it).take(24) },
                        label = "Username",
                        placeholder = "3 to 24 characters",
                        tag = "settings_username_input"
                    )

                    Column {
                        GlassTextField(
                            value = motto,
                            onValueChange = { raw ->
                                val stripped = VeilCryptoEngine.stripEmojis(raw)
                                motto = if (VeilCryptoEngine.countWords(stripped) <= 20) {
                                    stripped
                                } else {
                                    VeilCryptoEngine.enforceMaxWords(stripped, 20)
                                }
                            },
                            label = "Motto / Subline",
                            placeholder = "Up to 20 words",
                            singleLine = false,
                            minLines = 2,
                            tag = "settings_motto_input"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "$wordCount / 20 words",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    color = if (wordCount >= 20) AmberWarn else accent.highlight
                                )
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GENDER_OPTIONS.forEach { option ->
                            val selected = gender == option
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) accent.container else GlassSurfaceSubtle)
                                    .border(
                                        1.dp,
                                        if (selected) accent.highlight else GlassBorderTop,
                                        RoundedCornerShape(50)
                                    )
                                    .clickable { gender = option }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = accent.highlight,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (selected) accent.highlight else SlateSecondary
                                    )
                                )
                            }
                        }
                    }

                    GlassPrimaryButton(
                        text = "Save & Sync Profile",
                        icon = Icons.Rounded.Save,
                        enabled = username.trim().length in 3..24,
                        onClick = { onSaveProfile(username.trim(), motto.trim(), gender, localPfpPath) },
                        modifier = Modifier.fillMaxWidth(),
                        tag = "settings_save_profile_button"
                    )

                    // Device-bound fingerprint & QR
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(GlassSurfaceSubtle)
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            QrCodeView(payload = identityUri, sizeDp = 96.dp)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "MY EC P-256 FINGERPRINT",
                                    style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                                )
                                Text(
                                    text = publicKeyFingerprint,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        color = accent.highlight
                                    )
                                )
                                Text(
                                    text = "Device ID: ${userProfile?.deviceBindingId?.take(12).orEmpty()}...",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        color = SlateSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 2: My Rooms & Room Switcher
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "My Rooms & Switcher",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = SnowPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "Tap any room below to switch your active workspace immediately.",
                        style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                    )

                    rooms.forEach { room ->
                        val isActive = room.id == activeRoomId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isActive) accent.container else GlassSurfaceSubtle)
                                .border(
                                    1.dp,
                                    if (isActive) accent.highlight else GlassBorderTop,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onSwitchRoom(room.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("settings_room_item_${room.veilCode}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isActive) Icons.Rounded.CheckCircle else Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = if (isActive) accent.highlight else SlateMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = room.roomName,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = SnowPrimary,
                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                )
                                Text(
                                    text = room.veilCode.chunked(4).joinToString(" - "),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        color = SlateSecondary
                                    )
                                )
                            }
                            if (rooms.size > 1) {
                                IconButton(
                                    onClick = { onDeleteRoom(room.id) },
                                    modifier = Modifier.testTag("settings_delete_room_${room.veilCode}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Leave room",
                                        tint = CoralError
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassPrimaryButton(
                            text = "Host New Room",
                            icon = Icons.Rounded.Add,
                            onClick = onHostNewRoom,
                            modifier = Modifier.weight(1f),
                            tag = "settings_host_new_room"
                        )
                        GlassSecondaryButton(
                            text = "Join a Room",
                            icon = Icons.Rounded.Login,
                            accentColor = accent.highlight,
                            onClick = onJoinNewRoom,
                            modifier = Modifier.weight(1f),
                            tag = "settings_join_new_room"
                        )
                    }
                }
            }

            // SECTION 3: Color Theme Customizer (Obsidian Black + Any Custom Accent)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = accent.highlight
                        )
                        Text(
                            text = "Color Theme Customizer",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SnowPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Text(
                        text = "Keep pure Obsidian Black surfaces while customizing your accent color across tabs, bubbles, buttons, and icons.",
                        style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                    )
                    VeilColorThemeCustomizer(
                        currentColor = accentColor,
                        onColorSelected = onSelectAccentColor
                    )
                }
            }

            // SECTION 4: Backend & About / In-App Updates
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Backend & Updates",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SnowPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        GlassPill(
                            text = "Veil v${BuildConfig.VERSION_NAME}",
                            monospace = true,
                            accentColor = accent.highlight
                        )
                    }

                    GlassSecondaryButton(
                        text = "Manage Supabase & Self-Hosted Backends",
                        icon = Icons.Outlined.Storage,
                        onClick = onOpenBackendSettings,
                        modifier = Modifier.fillMaxWidth(),
                        tag = "settings_manage_backends"
                    )

                    GlassSecondaryButton(
                        text = "Copy v1.0.1 Supabase SQL Schema (8 Tables)",
                        icon = Icons.Rounded.ContentCopy,
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Veil SQL Schema", VeilBackendClient.SUPABASE_SQL_SCHEMA))
                            onPostStatus("8-table Veil v1.0.1 SQL schema copied to clipboard.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        tag = "settings_copy_sql_schema"
                    )

                    if (isCheckingUpdate) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = accent.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    } else {
                        GlassPrimaryButton(
                            text = "Check for Updates",
                            icon = Icons.Rounded.SystemUpdate,
                            onClick = onCheckForUpdates,
                            modifier = Modifier.fillMaxWidth(),
                            tag = "settings_check_updates"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
