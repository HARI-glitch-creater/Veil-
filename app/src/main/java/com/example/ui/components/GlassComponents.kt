package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmbientGlowMint
import com.example.ui.theme.AmbientGlowSlate
import com.example.ui.theme.GlacierMint
import com.example.ui.theme.GlassBorderBottom
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun VeilObsidianBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Subtle ambient atmospheric refraction layers behind translucent glass cards
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AmbientGlowSlate.copy(alpha = 0.65f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.15f, size.height * 0.08f),
                    radius = size.maxDimension * 0.65f
                ),
                radius = size.maxDimension * 0.65f,
                center = Offset(size.width * 0.15f, size.height * 0.08f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AmbientGlowMint.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.78f),
                    radius = size.maxDimension * 0.55f
                ),
                radius = size.maxDimension * 0.55f,
                center = Offset(size.width * 0.88f, size.height * 0.78f)
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    containerColor: Color = GlassSurface,
    borderTopColor: Color = GlassBorderTop,
    borderBottomColor: Color = GlassBorderBottom,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val shape: Shape = RoundedCornerShape(cornerRadius)
    val borderBrush = remember(borderTopColor, borderBottomColor) {
        Brush.linearGradient(listOf(borderTopColor, borderBottomColor))
    }
    val surfaceBrush = remember(containerColor) {
        Brush.verticalGradient(
            listOf(
                containerColor.copy(alpha = (containerColor.alpha + 0.08f).coerceAtMost(0.92f)),
                containerColor
            )
        )
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(surfaceBrush, shape)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun GlassPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = GlacierMint,
    containerColor: Color = GlassSurfaceSubtle,
    monospace: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(containerColor, shape)
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.28f),
                shape = shape
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = text,
            style = if (monospace) {
                MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    color = accentColor,
                    fontWeight = FontWeight.Medium
                )
            } else {
                MaterialTheme.typography.labelMedium.copy(color = accentColor)
            },
            maxLines = 1
        )
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    isSecret: Boolean = false,
    monospace: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    supportingText: String? = null,
    tag: String? = null
) {
    var revealed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (tag != null) Modifier.testTag(tag) else Modifier),
            label = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium
                )
            },
            placeholder = if (placeholder.isNotEmpty()) {
                {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = SlateMuted,
                            fontFamily = if (monospace) JetBrainsMonoFamily else MaterialTheme.typography.bodyMedium.fontFamily
                        )
                    )
                }
            } else null,
            textStyle = if (monospace) {
                TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 14.sp,
                    color = SnowPrimary
                )
            } else {
                MaterialTheme.typography.bodyLarge.copy(color = SnowPrimary)
            },
            singleLine = singleLine,
            visualTransformation = if (isSecret && !revealed) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            trailingIcon = if (isSecret) {
                {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            imageVector = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (revealed) "Hide secret" else "Show secret",
                            tint = SlateSecondary
                        )
                    }
                }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = shape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = GlassSurfaceElevated.copy(alpha = 0.72f),
                unfocusedContainerColor = GlassSurfaceSubtle,
                focusedBorderColor = GlacierMint.copy(alpha = 0.75f),
                unfocusedBorderColor = GlassBorderTop,
                focusedLabelColor = GlacierMint,
                unfocusedLabelColor = SlateSecondary,
                cursorColor = GlacierMint
            )
        )
        if (!supportingText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
fun GlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tag: String? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GlacierMint,
            contentColor = ObsidianDeep,
            disabledContainerColor = GlacierMint.copy(alpha = 0.25f),
            disabledContentColor = ObsidianDeep.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
fun GlassSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accentColor: Color = SnowPrimary,
    tag: String? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(accentColor.copy(alpha = 0.30f), GlassBorderBottom)
            )
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = GlassSurfaceSubtle,
            contentColor = accentColor
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun MonogramAvatar(
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    accent: Color = GlacierMint
) {
    val initials = remember(label) {
        label.trim()
            .split(" ", "-", "_")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "V" }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.24f),
                        GlassSurfaceElevated
                    )
                )
            )
            .border(1.dp, accent.copy(alpha = 0.38f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleSmall.copy(
                color = SnowPrimary,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
fun QrCodeView(
    payload: String,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 200.dp
) {
    val bitmap = remember(payload) { generateQrBitmap(payload, 512) }
    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(20.dp))
            .background(SnowPrimary)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR code",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun generateQrBitmap(content: String, sizePx: Int): Bitmap? {
    return runCatching {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        val fg = android.graphics.Color.parseColor("#090B0E")
        val bg = android.graphics.Color.parseColor("#F1F5F9")
        for (y in 0 until sizePx) {
            val offset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[offset + x] = if (matrix.get(x, y)) fg else bg
            }
        }
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        }
    }.getOrNull()
}
