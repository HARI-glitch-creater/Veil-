package com.example.ui.screens

import android.content.ClipData
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.crypto.VeilCryptoEngine
import com.example.data.BackendProfileDraft
import com.example.data.HostRoomOutcome
import com.example.data.local.AuthType
import com.example.data.local.BackendType
import com.example.data.network.ConnectionTestResult
import com.example.data.network.VeilBackendClient
import com.example.ui.VeilViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.GlassTextField
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostRoomScreen(
    viewModel: VeilViewModel,
    onBack: () -> Unit,
    onEnterRoom: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val accent = LocalVeilAccent.current
    val savedProfiles by viewModel.backendProfiles.collectAsStateWithLifecycle()

    var selectedType by remember { mutableStateOf(BackendType.SUPABASE) }
    var roomName by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var supabaseBucket by remember { mutableStateOf("veil-media") }

    var showAdvanced by remember { mutableStateOf(false) }
    var authType by remember { mutableStateOf(AuthType.BEARER) }
    var authHeaderName by remember { mutableStateOf("X-API-Key") }
    var customHeadersRaw by remember { mutableStateOf("") }
    var webSocketUrl by remember { mutableStateOf("") }
    var mediaUploadUrl by remember { mutableStateOf("") }
    var webhookUrl by remember { mutableStateOf("") }

    var showSqlSheet by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var hostOutcome by remember { mutableStateOf<HostRoomOutcome?>(null) }

    fun parseHeadersMap(raw: String): Map<String, String> =
        raw.lines().mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) null
            else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }.filter { it.first.isNotBlank() && it.second.isNotBlank() }.toMap()

    fun buildDraft(): BackendProfileDraft = BackendProfileDraft(
        name = profileName.ifBlank {
            if (selectedType == BackendType.SUPABASE) "My Supabase" else "Self-Hosted Server"
        },
        backendType = selectedType,
        baseUrl = baseUrl,
        apiKey = apiKey,
        supabaseBucket = supabaseBucket,
        authType = if (selectedType == BackendType.SUPABASE) AuthType.BEARER else authType,
        authHeaderName = authHeaderName,
        customHeaders = parseHeadersMap(customHeadersRaw),
        webSocketUrl = webSocketUrl,
        mediaUploadUrl = mediaUploadUrl,
        webhookUrl = webhookUrl
    )

    fun copyToClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    val roomNameValid = roomName.trim().length >= 2

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
                modifier = Modifier.testTag("host_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = SnowPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (hostOutcome == null) "Host a Veil Room" else "Veil Room Ready",
                style = MaterialTheme.typography.titleLarge.copy(color = SnowPrimary)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val outcome = hostOutcome
            if (outcome != null) {
                HostRoomCreatedContent(
                    outcome = outcome,
                    formattedCode = viewModel.repository.crypto.formatVeilCode(outcome.room.veilCode),
                    onCopyCode = {
                        copyToClipboard("Veil Code", outcome.room.veilCode)
                        viewModel.postBanner("16-character Veil Code copied.")
                    },
                    onEnterRoom = onEnterRoom
                )
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BackendTypeSegment(
                        selected = selectedType,
                        onSelect = {
                            selectedType = it
                            testResult = null
                        }
                    )

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            GlassTextField(
                                value = roomName,
                                onValueChange = { roomName = VeilCryptoEngine.stripEmojis(it).take(32) },
                                label = "Room Name (Required)",
                                placeholder = "Family Vault, Studio Team...",
                                supportingText = "Displayed directly beneath Veil in the top bar.",
                                tag = "host_room_name_input"
                            )

                            if (selectedType == BackendType.SUPABASE) {
                                GlassTextField(
                                    value = baseUrl,
                                    onValueChange = { baseUrl = it },
                                    label = "Supabase Project URL",
                                    placeholder = "https://your-project.supabase.co",
                                    monospace = true,
                                    keyboardType = KeyboardType.Uri,
                                    tag = "host_url_input"
                                )
                                GlassTextField(
                                    value = apiKey,
                                    onValueChange = { apiKey = it },
                                    label = "Supabase API Key (anon / publishable)",
                                    placeholder = "eyJhbGciOi...",
                                    isSecret = true,
                                    monospace = true,
                                    supportingText = "All messages, PFPs, files, and call signals are encrypted on-device before upload.",
                                    tag = "host_api_key_input"
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GlassSecondaryButton(
                                        text = "SQL Schema",
                                        icon = Icons.Rounded.Code,
                                        onClick = { showSqlSheet = true },
                                        tag = "host_sql_schema_button"
                                    )
                                    GlassSecondaryButton(
                                        text = "Test",
                                        icon = Icons.Rounded.NetworkCheck,
                                        enabled = !isBusy && baseUrl.isNotBlank(),
                                        onClick = {
                                            isBusy = true
                                            viewModel.testBackendDraft(buildDraft()) { res ->
                                                isBusy = false
                                                testResult = res
                                            }
                                        },
                                        tag = "host_test_connection_button"
                                    )
                                }
                            } else {
                                GlassTextField(
                                    value = profileName,
                                    onValueChange = { profileName = VeilCryptoEngine.stripEmojis(it) },
                                    label = "Server Label (Optional)",
                                    placeholder = "Home Rack, VPS Relay..."
                                )
                                GlassTextField(
                                    value = baseUrl,
                                    onValueChange = { baseUrl = it },
                                    label = "Server REST Base URL",
                                    placeholder = "https://veil.homelab.local",
                                    monospace = true,
                                    keyboardType = KeyboardType.Uri,
                                    tag = "host_url_input"
                                )
                                if (baseUrl.trim().lowercase().startsWith("http://")) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AmberContainer)
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.WarningAmber,
                                            contentDescription = null,
                                            tint = AmberWarn,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Cleartext HTTP — fine over WireGuard/Tailscale/LAN; E2EE protects payloads.",
                                            style = MaterialTheme.typography.bodySmall.copy(color = AmberWarn)
                                        )
                                    }
                                }

                                AuthTypeRow(selected = authType, onSelect = { authType = it })

                                if (authType != AuthType.NONE) {
                                    if (authType == AuthType.API_KEY_HEADER) {
                                        GlassTextField(
                                            value = authHeaderName,
                                            onValueChange = { authHeaderName = it },
                                            label = "Header Name",
                                            monospace = true
                                        )
                                    }
                                    GlassTextField(
                                        value = apiKey,
                                        onValueChange = { apiKey = it },
                                        label = when (authType) {
                                            AuthType.BEARER -> "Bearer Token"
                                            AuthType.API_KEY_HEADER -> "API Key Value"
                                            AuthType.BASIC -> "Basic Credentials (user:pass)"
                                            AuthType.NONE -> ""
                                        },
                                        isSecret = true,
                                        monospace = true,
                                        tag = "host_api_key_input"
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { showAdvanced = !showAdvanced }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Advanced (WebSocket, S3 Media, Webhooks, Headers)",
                                        style = MaterialTheme.typography.labelMedium.copy(color = SlateSecondary)
                                    )
                                    Icon(
                                        imageVector = if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = "Toggle advanced options",
                                        tint = SlateSecondary
                                    )
                                }

                                AnimatedVisibility(visible = showAdvanced) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        GlassTextField(
                                            value = webSocketUrl,
                                            onValueChange = { webSocketUrl = it },
                                            label = "WebSocket Relay URL (Optional)",
                                            placeholder = "wss://veil.homelab.local/ws",
                                            monospace = true
                                        )
                                        GlassTextField(
                                            value = mediaUploadUrl,
                                            onValueChange = { mediaUploadUrl = it },
                                            label = "S3 / Media Upload Base URL (Optional)",
                                            monospace = true
                                        )
                                        GlassTextField(
                                            value = webhookUrl,
                                            onValueChange = { webhookUrl = it },
                                            label = "Outbound Webhook URL (Optional)",
                                            monospace = true
                                        )
                                        GlassTextField(
                                            value = customHeadersRaw,
                                            onValueChange = { customHeadersRaw = it },
                                            label = "Custom Headers (Key: Value per line)",
                                            singleLine = false,
                                            monospace = true
                                        )
                                    }
                                }

                                GlassSecondaryButton(
                                    text = "Test Connection",
                                    icon = Icons.Rounded.NetworkCheck,
                                    enabled = !isBusy && baseUrl.isNotBlank(),
                                    onClick = {
                                        isBusy = true
                                        viewModel.testBackendDraft(buildDraft()) { res ->
                                            isBusy = false
                                            testResult = res
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    tag = "host_test_connection_button"
                                )
                            }
                        }
                    }

                    // Reuse saved backend quick option if user already saved one and left URL blank
                    if (savedProfiles.isNotEmpty() && baseUrl.isBlank()) {
                        val activeSaved = savedProfiles.firstOrNull { it.isActive } ?: savedProfiles.first()
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = GlassSurfaceSubtle
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Use Saved Backend: ${activeSaved.name}",
                                        style = MaterialTheme.typography.titleSmall.copy(color = SnowPrimary)
                                    )
                                    Text(
                                        text = activeSaved.baseUrl,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            color = SlateSecondary
                                        )
                                    )
                                }
                                GlassSecondaryButton(
                                    text = "Host Here",
                                    enabled = !isBusy && roomNameValid,
                                    accentColor = accent.highlight,
                                    onClick = {
                                        isBusy = true
                                        viewModel.hostRoom(roomName.trim(), activeSaved.id) { res ->
                                            isBusy = false
                                            res.onSuccess { hostOutcome = it }
                                                .onFailure { viewModel.postBanner(it.message ?: "Failed to host room.") }
                                        }
                                    },
                                    tag = "host_with_saved_backend_button"
                                )
                            }
                        }
                    }

                    testResult?.let { result ->
                        val tint = if (result.success) accent.highlight else CoralError
                        val bg = if (result.success) accent.container else CoralContainer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(bg)
                                .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (result.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = result.message,
                                style = MaterialTheme.typography.bodySmall.copy(color = SnowPrimary)
                            )
                        }
                    }

                    GlassPrimaryButton(
                        text = if (isBusy) "Creating Veil Room..." else "Connect & Create Veil Room",
                        icon = if (isBusy) null else Icons.Rounded.Shield,
                        enabled = !isBusy && roomNameValid && baseUrl.isNotBlank(),
                        onClick = {
                            isBusy = true
                            viewModel.saveBackendProfile(buildDraft()) { saveRes ->
                                saveRes.onSuccess { savedBackend ->
                                    viewModel.hostRoom(roomName.trim(), savedBackend.id) { hostRes ->
                                        isBusy = false
                                        hostRes.onSuccess { hostOutcome = it }
                                            .onFailure { viewModel.postBanner(it.message ?: "Unable to host room.") }
                                    }
                                }.onFailure { err ->
                                    isBusy = false
                                    viewModel.postBanner(err.message ?: "Invalid backend configuration.")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        tag = "host_create_room_button"
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

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showSqlSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSqlSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ObsidianBg,
            contentColor = SnowPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Veil v${BuildConfig.VERSION_NAME} Zero-Knowledge SQL Schema (8 Tables)",
                    style = MaterialTheme.typography.titleLarge.copy(color = SnowPrimary)
                )
                Text(
                    text = "Run once in Supabase SQL Editor. All messages, PFPs, files, locations, and voice call signals are encrypted on-device before insert.",
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                )
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = GlassSurfaceSubtle,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Text(
                        text = VeilBackendClient.SUPABASE_SQL_SCHEMA,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            color = accent.highlight,
                            lineHeight = 16.sp
                        )
                    )
                }
                GlassPrimaryButton(
                    text = "Copy SQL to Clipboard",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = {
                        copyToClipboard("Veil v${BuildConfig.VERSION_NAME} SQL Schema", VeilBackendClient.SUPABASE_SQL_SCHEMA)
                        viewModel.postBanner("8-table Veil SQL schema copied.")
                        showSqlSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    tag = "copy_supabase_sql_button"
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun HostRoomCreatedContent(
    outcome: HostRoomOutcome,
    formattedCode: String,
    onCopyCode: () -> Unit,
    onEnterRoom: () -> Unit
) {
    val accent = LocalVeilAccent.current
    val codeBlocks = outcome.room.veilCode.chunked(4)

    Column(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        GlassPill(
            text = if (outcome.warningMessage == null) "Connected · Zero-Knowledge E2EE Active" else "Local Vault Ready · Sync Pending",
            icon = Icons.Rounded.Shield,
            accentColor = if (outcome.warningMessage == null) accent.highlight else AmberWarn
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderTopColor = accent.highlight.copy(alpha = 0.35f),
            contentPadding = PaddingValues(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = outcome.room.roomName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = SnowPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "16-CHARACTER VEIL CODE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = SlateSecondary,
                        letterSpacing = 1.6.sp
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generated_veil_code_text"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    codeBlocks.forEach { block ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(GlassSurfaceSubtle)
                                .border(1.dp, accent.highlight.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .padding(vertical = 14.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = block,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    color = accent.highlight,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Text(
                    text = formattedCode,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFamily,
                        color = SnowPrimary,
                        letterSpacing = 0.8.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Share this exact 16-character Veil Code (and your server connection details if needed) so peers can join ${outcome.room.roomName} with zero-knowledge E2EE.",
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary),
                    textAlign = TextAlign.Center
                )

                GlassSecondaryButton(
                    text = "Copy 16-Character Veil Code",
                    icon = Icons.Rounded.ContentCopy,
                    accentColor = accent.highlight,
                    onClick = onCopyCode,
                    modifier = Modifier.fillMaxWidth(),
                    tag = "copy_veil_code_button"
                )
            }
        }

        GlassPrimaryButton(
            text = "Open ${outcome.room.roomName}",
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            onClick = onEnterRoom,
            modifier = Modifier.fillMaxWidth(),
            tag = "enter_hosted_room_button"
        )
    }
}

@Composable
fun BackendTypeSegment(
    selected: BackendType,
    onSelect: (BackendType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GlassSurfaceSubtle)
            .border(1.dp, GlassBorderTop, RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegmentTab(
            label = "Supabase",
            icon = Icons.Rounded.Storage,
            selected = selected == BackendType.SUPABASE,
            onClick = { onSelect(BackendType.SUPABASE) },
            modifier = Modifier
                .weight(1f)
                .testTag("backend_tab_supabase")
        )
        SegmentTab(
            label = "Self-Hosted",
            icon = Icons.Rounded.Dns,
            selected = selected == BackendType.SELF_HOSTED,
            onClick = { onSelect(BackendType.SELF_HOSTED) },
            modifier = Modifier
                .weight(1f)
                .testTag("backend_tab_self_hosted")
        )
    }
}

@Composable
private fun SegmentTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalVeilAccent.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) accent.container else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) accent.highlight.copy(alpha = 0.4f) else Color.Transparent,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accent.highlight else SlateSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                color = if (selected) accent.highlight else SlateSecondary
            )
        )
    }
}

@Composable
fun AuthTypeRow(
    selected: AuthType,
    onSelect: (AuthType) -> Unit
) {
    val accent = LocalVeilAccent.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Authentication",
            style = MaterialTheme.typography.labelMedium.copy(color = SlateSecondary)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                AuthType.BEARER to "Bearer",
                AuthType.API_KEY_HEADER to "API Key",
                AuthType.BASIC to "Basic",
                AuthType.NONE to "None"
            ).forEach { (type, label) ->
                val active = selected == type
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
                        .clickable { onSelect(type) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (active) accent.highlight else SlateMuted
                        )
                    )
                }
            }
        }
    }
}
