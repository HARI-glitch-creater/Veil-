package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.VeilViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.QrCodeView
import com.example.ui.theme.GlacierMint
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary

@Composable
fun IdentityScreen(
    viewModel: VeilViewModel,
    modifier: Modifier = Modifier
) {
    val identity by viewModel.identity.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var handleInput by remember(identity.handle) { mutableStateOf(identity.handle) }
    val identityUri = remember(identity) { viewModel.repository.buildIdentityUri() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            Column {
                Text(
                    text = "Identity",
                    style = MaterialTheme.typography.headlineMedium.copy(color = SnowPrimary)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Hardware-protected EC P-256 identity keypair",
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                )
            }

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderTopColor = GlacierMint.copy(alpha = 0.32f),
                contentPadding = PaddingValues(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    GlassPill(
                        text = "Android Keystore Protected",
                        icon = Icons.Rounded.Key,
                        accentColor = GlacierMint
                    )

                    QrCodeView(payload = identityUri, sizeDp = 192.dp)

                    Text(
                        text = identity.handle,
                        style = MaterialTheme.typography.titleLarge.copy(color = SnowPrimary)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassSurfaceSubtle)
                            .border(1.dp, GlassBorderTop, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "PUBLIC KEY FINGERPRINT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SlateMuted,
                                letterSpacing = 1.2.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = identity.shortFingerprint,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                color = GlacierMint
                            ),
                            textAlign = TextAlign.Center
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassSecondaryButton(
                            text = "Copy Public Key",
                            icon = Icons.Rounded.Fingerprint,
                            onClick = {
                                clipboard.setText(AnnotatedString(identity.publicKeyBase64))
                                viewModel.showToast("Public key copied")
                            },
                            modifier = Modifier.weight(1f),
                            tag = "copy_public_key_button"
                        )
                        GlassSecondaryButton(
                            text = "Copy ID URI",
                            icon = Icons.Rounded.ContentCopy,
                            onClick = {
                                clipboard.setText(AnnotatedString(identityUri))
                                viewModel.showToast("Identity URI copied")
                            },
                            modifier = Modifier.weight(1f),
                            tag = "copy_identity_uri_button"
                        )
                    }
                }
            }

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassTextField(
                        value = handleInput,
                        onValueChange = { handleInput = it },
                        label = "Display Handle",
                        placeholder = "node-alpha",
                        supportingText = "Shown inside encrypted messages only — never stored unencrypted in message rows.",
                        tag = "identity_handle_input"
                    )
                    GlassPrimaryButton(
                        text = "Update Handle",
                        icon = Icons.Rounded.Check,
                        enabled = handleInput.trim().isNotBlank() && handleInput.trim() != identity.handle,
                        onClick = { viewModel.updateHandle(handleInput) },
                        modifier = Modifier.fillMaxWidth(),
                        tag = "update_handle_button"
                    )
                }
            }

            Spacer(modifier = Modifier.height(104.dp))
        }
    }
}
