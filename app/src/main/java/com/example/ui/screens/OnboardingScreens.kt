package com.example.ui.screens

import android.Manifest
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.crypto.VeilCryptoEngine
import com.example.ui.OnboardingDraft
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.ProfileAvatar
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary
import java.io.File

val GENDER_OPTIONS = listOf("Male", "Female", "Non-binary", "Prefer not to say")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateProfileScreen(
    draft: OnboardingDraft,
    onDraftChange: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
    onCreateCameraTarget: () -> Pair<File, Uri>,
    onCompressAvatarUri: (Uri, (String) -> Unit) -> Unit,
    onContinueToConfirm: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalVeilAccent.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onCompressAvatarUri(uri) { storedPath ->
                onDraftChange { it.copy(localPfpPath = storedPath) }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        val uri = pendingCameraUri
        if (captured && uri != null) {
            onCompressAvatarUri(uri) { storedPath ->
                onDraftChange { it.copy(localPfpPath = storedPath) }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (_, uri) = onCreateCameraTarget()
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    val wordCount = remember(draft.motto) { VeilCryptoEngine.countWords(draft.motto) }
    val isUsernameValid = draft.username.trim().length in 3..24

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassPill(
                text = "Step 1 of 2 · Single-Device Identity",
                icon = Icons.Outlined.Lock,
                accentColor = accent.highlight
            )

            Text(
                text = "Create Your Veil Profile",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = SnowPrimary,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Your profile picture, username, motto, and keys are encrypted end-to-end before leaving this phone.",
                style = MaterialTheme.typography.bodyMedium.copy(color = SlateSecondary),
                textAlign = TextAlign.Center
            )

            // 104.dp Circular Profile Picture Picker
            Box(contentAlignment = Alignment.BottomEnd) {
                ProfileAvatar(
                    localPfpPath = draft.localPfpPath,
                    username = draft.username.ifBlank { "Veil" },
                    size = 104.dp,
                    accent = accent.highlight,
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accent.primary)
                        .border(2.dp, ObsidianDeep, CircleShape)
                        .clickable {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = "Choose profile photo",
                        tint = ObsidianDeep,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassSecondaryButton(
                    text = "Gallery",
                    icon = Icons.Outlined.PhotoLibrary,
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    tag = "onboarding_pfp_gallery"
                )
                GlassSecondaryButton(
                    text = "Camera",
                    icon = Icons.Outlined.PhotoCamera,
                    onClick = {
                        val hasCam = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCam) {
                            val (_, uri) = onCreateCameraTarget()
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    tag = "onboarding_pfp_camera"
                )
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GlassTextField(
                        value = draft.username,
                        onValueChange = { raw ->
                            val clean = VeilCryptoEngine.stripEmojis(raw).take(24)
                            onDraftChange { it.copy(username = clean) }
                        },
                        label = "Username (3 to 24 characters)",
                        placeholder = "e.g. avery.k",
                        supportingText = if (draft.username.isNotBlank() && !isUsernameValid) {
                            "Enter 3 to 24 characters without emojis."
                        } else {
                            "Displayed inside rooms and communities."
                        },
                        tag = "onboarding_username_input"
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        GlassTextField(
                            value = draft.motto,
                            onValueChange = { raw ->
                                val stripped = VeilCryptoEngine.stripEmojis(raw)
                                val nextWords = VeilCryptoEngine.countWords(stripped)
                                val enforced = if (nextWords <= 20) {
                                    stripped
                                } else {
                                    VeilCryptoEngine.enforceMaxWords(stripped, 20)
                                }
                                onDraftChange { it.copy(motto = enforced) }
                            },
                            label = "Motto / Subline (Optional)",
                            placeholder = "Short personal note shown beneath your name",
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                            tag = "onboarding_motto_input"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Hard 20-word limit",
                                style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                            )
                            Text(
                                text = "$wordCount / 20 words",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    color = if (wordCount >= 20) AmberWarn else accent.highlight
                                ),
                                modifier = Modifier.testTag("onboarding_motto_counter")
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Gender",
                            style = MaterialTheme.typography.labelLarge.copy(color = SnowPrimary)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GENDER_OPTIONS.forEach { option ->
                                val selected = draft.gender == option
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selected) accent.container else GlassSurfaceSubtle)
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) accent.highlight else GlassBorderTop,
                                            shape = RoundedCornerShape(50)
                                        )
                                        .clickable { onDraftChange { it.copy(gender = option) } }
                                        .padding(horizontal = 14.dp, vertical = 9.dp)
                                        .testTag("gender_option_${option.lowercase().replace(" ", "_")}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = accent.highlight,
                                            modifier = Modifier.size(14.dp)
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
                    }
                }
            }

            GlassPrimaryButton(
                text = "Continue to Confirm",
                icon = Icons.Rounded.ArrowForward,
                enabled = isUsernameValid,
                onClick = onContinueToConfirm,
                modifier = Modifier.fillMaxWidth(),
                tag = "onboarding_continue_button"
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ConfirmBindProfileScreen(
    draft: OnboardingDraft,
    publicKeyFingerprint: String,
    deviceBindingShortId: String,
    isBinding: Boolean,
    onEditProfile: () -> Unit,
    onConfirmAndBind: () -> Unit
) {
    BackHandler(onBack = onEditProfile)
    val accent = LocalVeilAccent.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassPill(
                text = "Step 2 of 2 · Permanent Phone Binding",
                icon = Icons.Outlined.Shield,
                accentColor = accent.highlight
            )

            Text(
                text = "Confirm & Bind to Phone",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = SnowPrimary,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )

            // Profile Preview Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProfileAvatar(
                        localPfpPath = draft.localPfpPath,
                        username = draft.username.ifBlank { "Veil" },
                        size = 96.dp,
                        accent = accent.highlight
                    )
                    Text(
                        text = draft.username.trim(),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = SnowPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (draft.motto.isNotBlank()) {
                        Text(
                            text = draft.motto.trim(),
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateSecondary),
                            textAlign = TextAlign.Center
                        )
                    }
                    GlassPill(
                        text = draft.gender,
                        accentColor = SlateSecondary
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassSurfaceElevated)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "EC P-256 PUBLIC KEY FINGERPRINT",
                                style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                            )
                            Text(
                                text = publicKeyFingerprint,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    color = accent.highlight
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "DEVICE BINDING ID",
                                style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                            )
                            Text(
                                text = deviceBindingShortId,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    color = SlateSecondary
                                )
                            )
                        }
                    }
                }
            }

            // Permanent Single-Account Notice
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = AmberContainer,
                borderTopColor = AmberWarn.copy(alpha = 0.45f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = AmberWarn,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "One Account Per Physical Phone",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = SnowPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "This profile is cryptographically bound to this phone. Reinstalling Veil will automatically detect this device and restore your existing account rather than creating a duplicate.",
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassSecondaryButton(
                    text = "Edit Profile",
                    icon = Icons.Rounded.Edit,
                    enabled = !isBinding,
                    onClick = onEditProfile,
                    modifier = Modifier.weight(1f),
                    tag = "confirm_edit_profile_button"
                )
                if (isBinding) {
                    Box(
                        modifier = Modifier.weight(1.3f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = accent.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    GlassPrimaryButton(
                        text = "Confirm & Bind",
                        icon = Icons.Rounded.VerifiedUser,
                        onClick = onConfirmAndBind,
                        modifier = Modifier.weight(1.3f),
                        tag = "confirm_bind_phone_button"
                    )
                }
            }
        }
    }
}
