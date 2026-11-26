package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.crypto.VeilCryptoEngine
import com.example.data.BackendProfileDraft
import com.example.data.local.AuthType
import com.example.data.local.BackendType
import com.example.ui.VeilViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.GlassTextField
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.IceCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary

@Composable
fun JoinRoomScreen(
    viewModel: VeilViewModel,
    onBack: () -> Unit,
    onJoinedRoom: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val accent = LocalVeilAccent.current
    val profiles by viewModel.backendProfiles.collectAsStateWithLifecycle()

    var rawCode by remember { mutableStateOf("") }
    var selectedProfileId by remember(profiles) {
        mutableStateOf(profiles.firstOrNull { it.isActive }?.id ?: profiles.firstOrNull()?.id)
    }

    var showManualBackend by remember(profiles) { mutableStateOf(profiles.isEmpty()) }
    var backendType by remember { mutableStateOf(BackendType.SUPABASE) }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }

    val normalizedCode = remember(rawCode) { viewModel.repository.crypto.normalizeVeilCode(rawCode) }
    val codeReady = normalizedCode.length == VeilCryptoEngine.VEIL_CODE_LENGTH

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
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderTopColor = IceCyan.copy(alpha = 0.3f),
                    contentPadding = PaddingValues(20.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ENTER 16-CHARACTER VEIL CODE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = SlateSecondary,
                                    letterSpacing = 1.2.sp
                                )
                            )
                            Text(
                                text = "${normalizedCode.length} / 16",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    color = if (codeReady) accent.highlight else SlateMuted
                                )
                            )
                        }

                        // 4 blocks of 4 characters preview
                        val padded = normalizedCode.padEnd(16, '•')
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                padded.substring(0, 4),
                                padded.substring(4, 8),
                                padded.substring(8, 12),
                                padded.substring(12, 16)
                            ).forEach { block ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(GlassSurfaceSubtle)
                                        .border(
                                            1.dp,
                                            if (block.contains('•')) GlassBorderTop else accent.highlight.copy(alpha = 0.4f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(vertical = 12.dp, horizontal = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = block,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            color = if (block.contains('•')) SlateMuted else accent.highlight,
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 1.sp
                                        ),
                                        maxLines = 1,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        GlassTextField(
                            value = rawCode,
                            onValueChange = { input ->
                                rawCode = viewModel.repository.crypto.normalizeVeilCode(input)
                            },
                            label = "16-Character Veil Code (Letters, Digits & Symbols)",
                            placeholder = "A7#K9m@P2\$xQ4!vZ",
                            monospace = true,
                            keyboardType = KeyboardType.Ascii,
                            supportingText = "Case-sensitive. Supports A-Z, a-z, 0-9, and symbols (!@#\$%^&*-_+=?).",
                            tag = "join_veil_code_input"
                        )

                        GlassSecondaryButton(
                            text = "Paste Veil Code",
                            icon = Icons.Rounded.ContentPaste,
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val pasted = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                val cleaned = viewModel.repository.crypto.normalizeVeilCode(pasted)
                                if (cleaned.length == VeilCryptoEngine.VEIL_CODE_LENGTH) {
                                    rawCode = cleaned
                                    viewModel.postBanner("16-character Veil Code pasted.")
                                } else {
                                    viewModel.postBanner("Clipboard does not contain a valid 16-character Veil Code.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            tag = "join_paste_code_button"
                        )
                    }
                }

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
                                        "Enter the host's Supabase or self-hosted URL and API key"
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
                                            .background(if (active) accent.container else GlassSurfaceSubtle)
                                            .border(
                                                1.dp,
                                                if (active) accent.highlight.copy(alpha = 0.45f) else GlassBorderTop,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { selectedProfileId = p.id }
                                            .padding(vertical = 8.dp, horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = p.name,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = if (active) accent.highlight else SlateSecondary
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
                                    label = if (backendType == BackendType.SUPABASE) "Host Supabase URL" else "Host Server URL",
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
                                    label = if (backendType == BackendType.SUPABASE) "Supabase API Key" else "Server Token / API Key",
                                    isSecret = true,
                                    monospace = true,
                                    tag = "join_backend_key_input"
                                )
                            }
                        }
                    }
                }

                GlassPrimaryButton(
                    text = if (isBusy) "Connecting & Deriving Keys..." else "Enter Veil Room",
                    icon = if (isBusy) null else Icons.Rounded.Key,
                    enabled = !isBusy && codeReady,
                    onClick = {
                        isBusy = true
                        if (baseUrl.isNotBlank()) {
                            viewModel.saveBackendProfile(
                                BackendProfileDraft(
                                    name = if (backendType == BackendType.SUPABASE) "Shared Supabase" else "Shared Server",
                                    backendType = backendType,
                                    baseUrl = baseUrl,
                                    apiKey = apiKey,
                                    authType = AuthType.BEARER
                                )
                            ) { savedRes ->
                                savedRes.onSuccess { savedBackend ->
                                    viewModel.joinRoom(
                                        veilCode = normalizedCode,
                                        roomName = "Veil Room",
                                        backendProfileId = savedBackend.id
                                    ) { joinRes ->
                                        isBusy = false
                                        joinRes.onSuccess { onJoinedRoom() }
                                            .onFailure { viewModel.postBanner(it.message ?: "Unable to join room.") }
                                    }
                                }.onFailure { err ->
                                    isBusy = false
                                    viewModel.postBanner(err.message ?: "Invalid server URL.")
                                }
                            }
                        } else if (!selectedProfileId.isNullOrBlank()) {
                            viewModel.joinRoom(
                                veilCode = normalizedCode,
                                roomName = "Veil Room",
                                backendProfileId = selectedProfileId!!
                            ) { joinRes ->
                                isBusy = false
                                joinRes.onSuccess { onJoinedRoom() }
                                    .onFailure { viewModel.postBanner(it.message ?: "Unable to join room.") }
                            }
                        } else {
                            isBusy = false
                            viewModel.postBanner("Enter the host's Supabase or self-hosted server details.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    tag = "join_confirm_button"
                )

                if (isBusy) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = accent.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
