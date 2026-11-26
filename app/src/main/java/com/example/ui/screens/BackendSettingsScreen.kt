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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Storage
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BackendProfileDraft
import com.example.data.local.AuthType
import com.example.data.local.BackendProfileEntity
import com.example.data.local.BackendType
import com.example.data.network.VeilBackendClient
import com.example.ui.VeilViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassTextField
import com.example.ui.theme.CoralError
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.IceCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendSettingsScreen(
    viewModel: VeilViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val accent = LocalVeilAccent.current
    val profiles by viewModel.backendProfiles.collectAsStateWithLifecycle()

    var showAddSheet by remember { mutableStateOf(false) }
    var showSqlSheet by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("backend_settings_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = SnowPrimary
                    )
                }
                Column {
                    Text(
                        text = "Backend Nodes",
                        style = MaterialTheme.typography.titleLarge.copy(color = SnowPrimary)
                    )
                    Text(
                        text = "Supabase or self-hosted servers",
                        style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { showSqlSheet = true },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(GlassSurfaceSubtle)
                        .border(1.dp, GlassBorderTop, CircleShape)
                        .testTag("backend_sql_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = "Supabase SQL schema",
                        tint = SlateSecondary
                    )
                }
                IconButton(
                    onClick = { showAddSheet = true },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.container)
                        .border(1.dp, accent.highlight.copy(alpha = 0.42f), CircleShape)
                        .testTag("backend_add_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add backend profile",
                        tint = accent.highlight
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storage,
                            contentDescription = null,
                            tint = accent.highlight,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "No Backend Connected",
                            style = MaterialTheme.typography.titleLarge.copy(color = SnowPrimary)
                        )
                        Text(
                            text = "Connect a Supabase project with your API key or point Veil to your self-hosted server.",
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        GlassPrimaryButton(
                            text = "Add Supabase or Server",
                            icon = Icons.Rounded.Add,
                            onClick = { showAddSheet = true },
                            modifier = Modifier.fillMaxWidth(),
                            tag = "empty_add_backend_button"
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    BackendProfileCard(
                        profile = profile,
                        isBusy = isTesting,
                        onSetActive = { viewModel.setActiveBackendProfile(profile.id) },
                        onTest = {
                            isTesting = true
                            viewModel.testSavedBackendProfile(profile) { res ->
                                isTesting = false
                                viewModel.postBanner(res.message)
                            }
                        },
                        onDelete = { viewModel.deleteBackendProfile(profile.id) }
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddBackendProfileSheet(
            viewModel = viewModel,
            onDismiss = { showAddSheet = false }
        )
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
                    text = "Veil v1.0.1 Encrypted Tables SQL",
                    style = MaterialTheme.typography.titleLarge.copy(color = SnowPrimary)
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
                    text = "Copy SQL Schema",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Veil SQL Schema", VeilBackendClient.SUPABASE_SQL_SCHEMA))
                        viewModel.postBanner("8-table Veil SQL schema copied.")
                        showSqlSheet = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun BackendProfileCard(
    profile: BackendProfileEntity,
    isBusy: Boolean,
    onSetActive: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    val accentTokens = LocalVeilAccent.current
    val isSupabase = profile.backendType == BackendType.SUPABASE
    val tint = if (isSupabase) accentTokens.highlight else IceCyan

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderTopColor = if (profile.isActive) tint.copy(alpha = 0.45f) else GlassBorderTop,
        contentPadding = PaddingValues(16.dp),
        onClick = onSetActive
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(tint.copy(alpha = 0.14f))
                            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSupabase) Icons.Rounded.Storage else Icons.Rounded.Dns,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium.copy(color = SnowPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = profile.baseUrl,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                color = SlateSecondary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (profile.isActive) {
                    GlassPill(
                        text = "Active",
                        icon = Icons.Rounded.CheckCircle,
                        accentColor = accentTokens.highlight
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSupabase) {
                        "Supabase · Zero-Knowledge Ciphertext Tables"
                    } else {
                        buildString {
                            append("Self-Hosted REST")
                            if (profile.webSocketUrl.isNotBlank()) append(" · WS")
                            if (profile.mediaUploadUrl.isNotBlank()) append(" · S3")
                            if (profile.webhookUrl.isNotBlank()) append(" · Hook")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                IconButton(
                    onClick = onTest,
                    enabled = !isBusy,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NetworkCheck,
                        contentDescription = "Test server connection",
                        tint = accentTokens.highlight,
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete backend profile",
                        tint = CoralError.copy(alpha = 0.85f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBackendProfileSheet(
    viewModel: VeilViewModel,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(BackendType.SUPABASE) }
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var authType by remember { mutableStateOf(AuthType.BEARER) }
    var authHeaderName by remember { mutableStateOf("X-API-Key") }
    var showAdvanced by remember { mutableStateOf(false) }
    var webSocketUrl by remember { mutableStateOf("") }
    var mediaUploadUrl by remember { mutableStateOf("") }
    var webhookUrl by remember { mutableStateOf("") }
    var customHeadersRaw by remember { mutableStateOf("") }

    fun parseHeadersMap(raw: String): Map<String, String> =
        raw.lines().mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) null
            else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }.filter { it.first.isNotBlank() && it.second.isNotBlank() }.toMap()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                text = "Add Backend Node",
                style = MaterialTheme.typography.titleLarge.copy(color = SnowPrimary)
            )

            BackendTypeSegment(
                selected = selectedType,
                onSelect = { selectedType = it }
            )

            GlassTextField(
                value = name,
                onValueChange = { name = it },
                label = "Profile Name",
                placeholder = if (selectedType == BackendType.SUPABASE) "Personal Supabase" else "Home Rack"
            )
            GlassTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = if (selectedType == BackendType.SUPABASE) "Supabase Project URL" else "REST Base URL",
                placeholder = if (selectedType == BackendType.SUPABASE) {
                    "https://your-project.supabase.co"
                } else {
                    "https://veil.homelab.local"
                },
                monospace = true,
                keyboardType = KeyboardType.Uri
            )

            if (selectedType == BackendType.SELF_HOSTED) {
                AuthTypeRow(selected = authType, onSelect = { authType = it })
                if (authType == AuthType.API_KEY_HEADER) {
                    GlassTextField(
                        value = authHeaderName,
                        onValueChange = { authHeaderName = it },
                        label = "Header Name",
                        monospace = true
                    )
                }
            }

            if (selectedType == BackendType.SUPABASE || authType != AuthType.NONE) {
                GlassTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = if (selectedType == BackendType.SUPABASE) "Supabase API Key" else "Auth Token / Key",
                    isSecret = true,
                    monospace = true
                )
            }

            if (selectedType == BackendType.SELF_HOSTED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Advanced (WebSocket, S3, Webhooks, Headers)",
                        style = MaterialTheme.typography.labelMedium.copy(color = SlateSecondary)
                    )
                    Icon(
                        imageVector = if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = SlateSecondary
                    )
                }

                AnimatedVisibility(visible = showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassTextField(
                            value = webSocketUrl,
                            onValueChange = { webSocketUrl = it },
                            label = "WebSocket URL (Optional)",
                            monospace = true
                        )
                        GlassTextField(
                            value = mediaUploadUrl,
                            onValueChange = { mediaUploadUrl = it },
                            label = "S3 / Media Endpoint (Optional)",
                            monospace = true
                        )
                        GlassTextField(
                            value = webhookUrl,
                            onValueChange = { webhookUrl = it },
                            label = "Webhook URL (Optional)",
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
            }

            GlassPrimaryButton(
                text = "Save Backend Profile",
                enabled = baseUrl.isNotBlank(),
                onClick = {
                    viewModel.saveBackendProfile(
                        BackendProfileDraft(
                            name = name,
                            backendType = selectedType,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            authType = if (selectedType == BackendType.SUPABASE) AuthType.BEARER else authType,
                            authHeaderName = authHeaderName,
                            customHeaders = parseHeadersMap(customHeadersRaw),
                            webSocketUrl = webSocketUrl,
                            mediaUploadUrl = mediaUploadUrl,
                            webhookUrl = webhookUrl
                        )
                    ) { res ->
                        res.onSuccess {
                            viewModel.postBanner("Backend profile saved.")
                            onDismiss()
                        }.onFailure { err ->
                            viewModel.postBanner(err.message ?: "Unable to save backend.")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                tag = "save_backend_profile_button"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
