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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BackendProfileDraft
import com.example.data.HostRoomOutcome
import com.example.data.local.AuthType
import com.example.data.local.BackendType
import com.example.data.network.VeilBackendClient
import com.example.ui.VeilViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.QrCodeView
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.GlacierMint
import com.example.ui.theme.GlacierMintContainer
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostRoomScreen(
    viewModel: VeilViewModel,
    onBack: () -> Unit,
    onEnterRoom: (String) -> Unit
) {
    val hostOutcome by viewModel.hostOutcome.collectAsStateWithLifecycle()
    val connectionTest by viewModel.connectionTest.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var selectedType by remember { mutableStateOf(BackendType.SUPABASE) }
    var roomName by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var supabaseBucket by remember { mutableStateOf("veil-media") }

    // Self-hosted optional advanced fields
    var showAdvanced by remember { mutableStateOf(false) }
    var authType by remember { mutableStateOf(AuthType.BEARER) }
    var authHeaderName by remember { mutableStateOf("X-API-Key") }
    var customHeadersRaw by remember { mutableStateOf("") }
    var webSocketUrl by remember { mutableStateOf("") }
    var mediaUploadUrl by remember { mutableStateOf("") }
    var webhookUrl by remember { mutableStateOf("") }
    var roomsPath by remember { mutableStateOf("/api/v1/rooms") }
    var membersPath by remember { mutableStateOf("/api/v1/members") }
    var messagesPath by remember { mutableStateOf("/api/v1/messages") }
    var healthPath by remember { mutableStateOf("/api/v1/health") }

    var showSqlSheet by remember { mutableStateOf(false) }

    fun buildDraft(): BackendProfileDraft = BackendProfileDraft(
        name = profileName.ifBlank {
            if (selectedType == BackendType.SUPABASE) "My Supabase" else "Homelab Server"
        },
        backendType = selectedType,
        baseUrl = baseUrl,
        apiKey = apiKey,
        supabaseBucket = supabaseBucket,
        authType = if (selectedType == BackendType.SUPABASE) AuthType.BEARER else authType,
        authHeaderName = authHeaderName,
        customHeadersRaw = customHeadersRaw,
        roomsPath = roomsPath,
        membersPath = membersPath,
        messagesPath = messagesPath,
        healthPath = healthPath,
        webSocketUrl = webSocketUrl,
        mediaUploadUrl = mediaUploadUrl,
        webhookUrl = webhookUrl
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Minimal top header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    viewModel.clearHostOutcome()
                    viewModel.clearConnectionTest()
                    onBack()
                },
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
                text = if (hostOutcome == null) "Host a Veil Room" else "Your Veil Room is Ready",
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
                    formattedCode = viewModel.formatVeilCode(outcome.room.veilCode),
                    onCopyCode = {
                        clipboard.setText(AnnotatedString(outcome.room.veilCode))
                        viewModel.showToast("12-digit Veil Code copied")
                    },
                    onCopyInvite = {
                        clipboard.setText(AnnotatedString(outcome.inviteUri))
                        viewModel.showToast("Full Room Invite copied")
                    },
                    onEnterRoom = {
                        val id = outcome.room.id
                        viewModel.clearHostOutcome()
                        viewModel.clearConnectionTest()
                        onEnterRoom(id)
                    }
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
                    // Clean Mode Switcher: Supabase (simple default) vs Self-Hosted
                    BackendTypeSegment(
                        selected = selectedType,
                        onSelect = {
                            selectedType = it
                            viewModel.clearConnectionTest()
                        }
                    )

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            GlassTextField(
                                value = roomName,
                                onValueChange = { roomName = it },
                                label = "Room Name (optional)",
                                placeholder = "Family Vault, Private Chat...",
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
                                    supportingText = "All messages & files are encrypted on-device before insert. Even the Supabase owner only sees ciphertext.",
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
                                        onClick = { viewModel.testConnectionDraft(buildDraft()) },
                                        tag = "host_test_connection_button"
                                    )
                                }
                            } else {
                                GlassTextField(
                                    value = profileName,
                                    onValueChange = { profileName = it },
                                    label = "Server Label (optional)",
                                    placeholder = "Home Rack, VPS Relay..."
                                )
                                GlassTextField(
                                    value = baseUrl,
                                    onValueChange = { baseUrl = it },
                                    label = "Server REST Base URL",
                                    placeholder = "https://veil.homelab.local or http://192.168.1.50:8080",
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
                                            text = "Cleartext HTTP — fine on WireGuard/Tailscale/LAN; E2EE still protects payloads.",
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
                                            placeholder = "X-API-Key",
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
                                        placeholder = "Secret token or key",
                                        isSecret = true,
                                        monospace = true,
                                        tag = "host_api_key_input"
                                    )
                                }

                                // Collapsible Advanced Homelab Options (keeps UI uncluttered)
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
                                        text = "Advanced (WebSocket, S3 Media, Webhooks, Paths)",
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
                                            label = "WebSocket Relay URL (optional)",
                                            placeholder = "wss://veil.homelab.local/ws",
                                            monospace = true
                                        )
                                        GlassTextField(
                                            value = mediaUploadUrl,
                                            onValueChange = { mediaUploadUrl = it },
                                            label = "S3 / Media Upload Base URL (optional)",
                                            placeholder = "https://s3.homelab.local/veil-blobs",
                                            monospace = true
                                        )
                                        GlassTextField(
                                            value = webhookUrl,
                                            onValueChange = { webhookUrl = it },
                                            label = "Outbound Webhook URL (optional)",
                                            placeholder = "https://hooks.homelab.local/veil",
                                            monospace = true
                                        )
                                        GlassTextField(
                                            value = customHeadersRaw,
                                            onValueChange = { customHeadersRaw = it },
                                            label = "Custom Headers (Key: Value per line)",
                                            placeholder = "CF-Access-Client-Id: xxx",
                                            singleLine = false,
                                            monospace = true
                                        )
                                        GlassTextField(
                                            value = messagesPath,
                                            onValueChange = { messagesPath = it },
                                            label = "Messages Endpoint Path",
                                            monospace = true
                                        )
                                        GlassTextField(
                                            value = roomsPath,
                                            onValueChange = { roomsPath = it },
                                            label = "Rooms Endpoint Path",
                                            monospace = true
                                        )
                                        GlassTextField(
                                            value = membersPath,
                                            onValueChange = { membersPath = it },
                                            label = "Members Endpoint Path",
                                            monospace = true
                                        )
                                        GlassTextField(
                                            value = healthPath,
                                            onValueChange = { healthPath = it },
                                            label = "Health Endpoint Path",
                                            monospace = true
                                        )
                                    }
                                }

                                GlassSecondaryButton(
                                    text = "Test Connection",
                                    icon = Icons.Rounded.NetworkCheck,
                                    enabled = !isBusy && baseUrl.isNotBlank(),
                                    onClick = { viewModel.testConnectionDraft(buildDraft()) },
                                    modifier = Modifier.fillMaxWidth(),
                                    tag = "host_test_connection_button"
                                )
                            }
                        }
                    }

                    // Connection test result pill
                    connectionTest?.let { result ->
                        val tint = if (result.success) GlacierMint else CoralError
                        val bg = if (result.success) GlacierMintContainer else CoralContainer
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
                        enabled = !isBusy && baseUrl.isNotBlank(),
                        onClick = { viewModel.hostVeilRoom(buildDraft(), roomName) },
                        modifier = Modifier.fillMaxWidth(),
                        tag = "host_create_room_button"
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

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showSqlSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSqlSheet = false },
            sheetState = sheetState,
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
                    text = "Supabase Zero-Knowledge Tables",
                    style = MaterialTheme.typography.titleLarge.copy(color = SnowPrimary)
                )
                Text(
                    text = "Paste this once into your Supabase SQL Editor. Veil encrypts all text, files, and locations on-device before insert — your tables only ever store ciphertext.",
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
                            color = GlacierMint,
                            lineHeight = 16.sp
                        )
                    )
                }
                GlassPrimaryButton(
                    text = "Copy SQL to Clipboard",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = {
                        clipboard.setText(AnnotatedString(VeilBackendClient.SUPABASE_SQL_SCHEMA))
                        viewModel.showToast("Supabase SQL copied")
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
    onCopyInvite: () -> Unit,
    onEnterRoom: () -> Unit
) {
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
            text = if (outcome.remoteSynced) "Connected · Zero-Knowledge E2EE Active" else "Local Vault Ready · Sync Queued",
            icon = Icons.Rounded.Shield,
            accentColor = if (outcome.remoteSynced) GlacierMint else AmberWarn
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderTopColor = GlacierMint.copy(alpha = 0.35f),
            contentPadding = PaddingValues(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "12-DIGIT VEIL CODE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = SlateSecondary,
                        letterSpacing = 1.6.sp
                    )
                )
                Text(
                    text = formattedCode,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        color = GlacierMint,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("generated_veil_code_text")
                )

                QrCodeView(payload = outcome.inviteUri, sizeDp = 196.dp)

                Text(
                    text = "Share the 12-digit Veil Code or let your peer scan this Room QR to connect directly to your database with end-to-end encryption.",
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary),
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassSecondaryButton(
                        text = "Copy Code",
                        icon = Icons.Rounded.ContentCopy,
                        onClick = onCopyCode,
                        modifier = Modifier.weight(1f),
                        tag = "copy_veil_code_button"
                    )
                    GlassSecondaryButton(
                        text = "Copy Invite",
                        icon = Icons.Rounded.Link,
                        onClick = onCopyInvite,
                        modifier = Modifier.weight(1f),
                        tag = "copy_invite_uri_button"
                    )
                }
            }
        }

        GlassPrimaryButton(
            text = "Open Veil Room",
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
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) GlacierMintContainer else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) GlacierMint.copy(alpha = 0.4f) else androidx.compose.ui.graphics.Color.Transparent,
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
            tint = if (selected) GlacierMint else SlateSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                color = if (selected) GlacierMint else SlateSecondary
            )
        )
    }
}

@Composable
fun AuthTypeRow(
    selected: AuthType,
    onSelect: (AuthType) -> Unit
) {
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
                        .background(if (active) GlacierMintContainer else GlassSurfaceSubtle)
                        .border(
                            1.dp,
                            if (active) GlacierMint.copy(alpha = 0.45f) else GlassBorderTop,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelect(type) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (active) GlacierMint else SlateMuted
                        )
                    )
                }
            }
        }
    }
}
