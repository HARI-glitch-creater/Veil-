package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.theme.GlassBorderBottom
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.IceCyan
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary

@Composable
fun WelcomeRoleScreen(
    username: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSelectHost: () -> Unit,
    onSelectJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (canGoBack) {
        BackHandler(onBack = onBack)
    }
    val accent = LocalVeilAccent.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        if (canGoBack) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .testTag("welcome_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = SnowPrimary
                )
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accent.primary.copy(alpha = 0.24f),
                                GlassSurfaceElevated
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(accent.highlight.copy(alpha = 0.45f), GlassBorderBottom)),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.primary.copy(alpha = 0.22f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            radius = size.minDimension * 0.55f
                        )
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = "Veil emblem",
                    tint = accent.highlight,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = if (username.isNotBlank()) "Welcome, $username" else stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = SnowPrimary,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Host your first encrypted Veil Room or join an existing room using a 12-digit Veil Code.",
                style = MaterialTheme.typography.bodyMedium.copy(color = SlateSecondary),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            GlassPill(
                text = "Zero-Knowledge E2EE · Your Own Server",
                icon = Icons.Outlined.Lock,
                accentColor = accent.highlight
            )

            Spacer(modifier = Modifier.height(34.dp))

            RoleChoiceGlassCard(
                title = stringResource(R.string.role_host_title),
                subtitle = stringResource(R.string.role_host_desc),
                tagLabel = "Supabase or Self-Hosted",
                icon = Icons.Rounded.Dns,
                accentColor = accent.highlight,
                testTag = "role_host_card",
                onClick = onSelectHost
            )

            Spacer(modifier = Modifier.height(16.dp))

            RoleChoiceGlassCard(
                title = stringResource(R.string.role_join_title),
                subtitle = stringResource(R.string.role_join_desc),
                tagLabel = "12-Digit Veil Code or QR",
                icon = Icons.Rounded.Key,
                accentColor = IceCyan,
                testTag = "role_join_card",
                onClick = onSelectJoin
            )

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "Once you enter your first room, Veil will launch straight into your room home screen automatically.",
                style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun RoleChoiceGlassCard(
    title: String,
    subtitle: String,
    tagLabel: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        cornerRadius = 24.dp,
        borderTopColor = accentColor.copy(alpha = 0.28f),
        borderBottomColor = GlassBorderBottom,
        contentPadding = PaddingValues(20.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accentColor.copy(alpha = 0.14f))
                    .border(1.dp, accentColor.copy(alpha = 0.32f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = SnowPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accentColor.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = tagLabel,
                        style = MaterialTheme.typography.labelSmall.copy(color = accentColor)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(GlassSurfaceElevated)
                    .border(1.dp, GlassBorderTop, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = SnowPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
