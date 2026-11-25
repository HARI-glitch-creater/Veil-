package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.QrCodeScanner
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.crypto.ParsedRoomInvite
import com.example.data.BackendProfileDraft
import com.example.data.local.AuthType
import com.example.data.local.BackendType
import com.example.ui.VeilViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.QrScannerDialog
import com.example.ui.theme.GlacierMint
import com.example.ui.theme.GlacierMintContainer
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.IceCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary

@Composable
fun JoinRoomScreen(
    viewModel: VeilViewModel,
    onBack: () -> Unit,
    onJoinedRoom: (String) -> Unit
) {
    val profiles by viewModel.backendProfiles.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var rawCode by remember { mutableStateOf("") }
    var parsedInvite by remember { mutableStateOf<ParsedRoomInvite?>(null) }
    var selectedProfileId by remember(profiles) {
        mutableStateOf(profiles.firstOrNull { it.isActive }?.id ?: profiles.firstOrNull()?.id)
    }

    var showManualBackend by remember(profiles) { mutableStateOf(profiles.isEmpty()) }
    var backendType by remember { mutableStateOf(BackendType.SUPABASE) }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }

    fun applyInviteString(raw: String) {
        val invite = viewModel.parseRoomInvite(raw)
        if (invite != null) {
            parsedInvite = invite
            rawCode = invite.veilCode
            backendType = invite.backendType
            baseUrl = invite.baseUrl
            apiKey = invite.apiKey
            showManualBackend = false
            viewModel.showToast("Loaded invite for ${invite.roomName}")
        } else {
            val digitsOnly = raw.filter { it.isDigit() }
            if (digitsOnly.length == 12) {
                rawCode = digitsOnly
                viewModel.showToast("12-digit Veil Code pasted")
            } else {
                viewModel.showToast("No valid Veil Code or Room Invite found")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("join_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = SnowPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Join a Veil Room",
                style = MaterialTheme.typography.titleLarge.copy(color = SnowPrimary)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero 12-Digit Veil Code Card
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderTopColor = IceCyan.copy(alpha = 0.3f),
                    contentPadding = PaddingValues(20.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ENTER 12-DIGIT VEIL CODE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SlateSecondary,
                                letterSpacing = 1.4.sp
                            )
                        )

                        // Formatted 3x4 monospace display preview
                        val padded = rawCode.filter { it.isDigit() }.take(12).padEnd(12, '•')
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf(padded.substring(0, 4), padded.substring(4, 8), padded.substring(8, 12)).forEach { block ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(GlassSurfaceSubtle)
                                        .border(1.dp, GlassBorderTop, RoundedCornerShape(14.dp))
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = block,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            color = if (block.contains('•')) SlateMuted else GlacierMint,
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 2.sp
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        GlassTextField(
                            value = rawCode,
                            onValueChange = { input ->
                                rawCode = input.filter { it.isDigit() }.take(12)
                                if (parsedInvite != null && parsedInvite?.veilCode != rawCode) {
                                    parsedInvite = null
                                }
                            },
                            label = "12-Digit Veil Code",
                            placeholder = "482910579364",
                            monospace = true,
                            keyboardType = KeyboardType.Number,
                            tag = "join_veil_code_input"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassSecondaryButton(
                                text = "Scan QR",
                                icon = Icons.Rounded.QrCodeScanner,
                                onClick = { showScanner = true },
                                modifier = Modifier.weight(1f),
                                accentColor = IceCyan,
                                tag = "join_scan_qr_button"
                            )
                            GlassSecondaryButton(
                                text = "Paste Invite",
                                icon = Icons.Rounded.ContentPaste,
                                onClick = {
                                    val pasted = clipboard.getText()?.text.orEmpty()
                                    applyInviteString(pasted)
                                },
                                modifier = Modifier.weight(1f),
                                tag = "join_paste_invite_button"
                            )
                        }
                    }
                }

                // Loaded Invite indicator OR Backend Selection
                if (parsedInvite != null) {
                    val inv = parsedInvite!!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(GlacierMintContainer)
                            .border(1.dp, GlacierMint.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = GlacierMint,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${inv.roomName} · ${inv.backendType.name.lowercase().replaceFirstChar { it.uppercase() }}",
                                style = MaterialTheme.typography.titleSmall.copy(color = SnowPrimary)
                            )
                            Text(
                                text = "Host key & endpoint loaded automatically from invite",
                                style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                            )
                        }
                    }
                } else {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showManualBackend = !showManualBackend },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Room Database Connection",
                                        style = MaterialTheme.typography.titleSmall.copy(color = SnowPrimary)
                                    )
                                    Text(
                                        text = if (profiles.isNotEmpty() && !showManualBackend) {
                                            "Using saved backend: ${profiles.firstOrNull { it.id == selectedProfileId }?.name ?: profiles.first().name}"
                                        } else {
                                            "Enter the host's Supabase or self-hosted URL & API key"
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                                    )
                                }
                                Icon(
                                    imageVector = if (showManualBackend) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = "Toggle backend details",
                                    tint = SlateSecondary
                                )
                            }

                            if (profiles.isNotEmpty() && !showManualBackend) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    profiles.take(3).forEach { p ->
                                        val active = p.id == selectedProfileId
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (active) GlacierMintContainer else GlassSurfaceSubtle)
                                                .border(
                                                    1.dp,
                                                    if (active) GlacierMint.copy(alpha = 0.45f) else GlassBorderTop,
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable { selectedProfileId = p.id }
                                                .padding(vertical = 8.dp, horizontal = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = p.name,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    color = if (active) GlacierMint else SlateSecondary
                                                ),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(visible = showManualBackend) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    BackendTypeSegment(
                                        selected = backendType,
                                        onSelect = { backendType = it }
                                    )
                                    GlassTextField(
                                        value = baseUrl,
                                        onValueChange = { baseUrl = it },
                                        label = if (backendType == BackendType.SUPABASE) {
                                            "Host Supabase URL"
                                        } else {
                                            "Host Server URL"
                                        },
                                        placeholder = if (backendType == BackendType.SUPABASE) {
                                            "https://your-project.supabase.co"
                                        } else {
                                            "https://veil.homelab.local"
                                        },
                                        monospace = true,
                                        keyboardType = KeyboardType.Uri,
                                        tag = "join_backend_url_input"
                                    )
                                    GlassTextField(
                                        value = apiKey,
                                        onValueChange = { apiKey = it },
                                        label = if (backendType == BackendType.SUPABASE) {
                                            "Supabase API Key"
                                        } else {
                                            "Server Token / API Key"
                                        },
                                        placeholder = "API key or token",
                                        isSecret = true,
                                        monospace = true,
                                        tag = "join_backend_key_input"
                                    )
                                }
                            }
                        }
                    }
                }

                val codeReady = rawCode.filter { it.isDigit() }.length == 12
                GlassPrimaryButton(
                    text = if (isBusy) "Connecting & Deriving Keys..." else "Enter Veil Room",
                    icon = if (isBusy) null else Icons.Rounded.Key,
                    enabled = !isBusy && codeReady,
                    onClick = {
                        val inlineDraft = if (parsedInvite == null && baseUrl.isNotBlank()) {
                            BackendProfileDraft(
                                name = if (backendType == BackendType.SUPABASE) "Shared Supabase" else "Shared Homelab",
                                backendType = backendType,
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                authType = AuthType.BEARER
                            )
                        } else null

                        viewModel.joinVeilRoom(
                            rawCode = rawCode,
                            existingProfileId = if (inlineDraft == null) selectedProfileId else null,
                            inlineDraft = inlineDraft,
                            parsedInvite = parsedInvite
                        ) { joinedRoom ->
                            onJoinedRoom(joinedRoom.id)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    tag = "join_confirm_button"
                )

                if (isBusy) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = GlacierMint,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    if (showScanner) {
        QrScannerDialog(
            title = "Scan Veil Room QR",
            onDismiss = { showScanner = false },
            onScanned = { scanned ->
                showScanner = false
                applyInviteString(scanned)
            }
        )
    }
}
