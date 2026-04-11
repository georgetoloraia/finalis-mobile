package com.finalis.mobile.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

@Composable
fun BrandHeroCard(
    mode: String,
    networkLabel: String,
    defaultFeeUnits: Long,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(10.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.finalis_symbol),
                        contentDescription = "Finalis symbol",
                        modifier = Modifier.size(30.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "Finalis Wallet",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "Calm wallet for finalized balance and sending.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrandChip(text = mode)
                BrandChip(text = networkLabel)
            }
            Text(
                text = "Fee ${formatFinalisAmountLabel(defaultFeeUnits)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun WalletPanel(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tonal: Boolean = false,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (tonal) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun LabelValue(
    label: String,
    value: String,
    monospace: Boolean = false,
    emphasize: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = value,
                style = if (emphasize) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                maxLines = if (monospace) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CodeBlock(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
    ) {
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun CopyableCodeBlock(
    text: String,
    onCopy: () -> Unit,
    onShare: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CodeBlock(text = text)
        ActionButtonsRow(
            primaryLabel = "Copy",
            onPrimary = onCopy,
            secondaryLabel = if (onShare != null) "Share" else null,
            onSecondary = onShare,
        )
    }
}

@Composable
fun BrandChip(text: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = null,
        shape = RoundedCornerShape(999.dp),
    )
}

@Composable
fun StatusChip(
    text: String,
    tone: StatusTone = StatusTone.Neutral,
) {
    val (container, content) = when (tone) {
        StatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        StatusTone.Positive -> Color(0xFFE6EFE5) to Color(0xFF405241)
        StatusTone.Warning -> Color(0xFFF4ECE0) to Color(0xFF75624A)
        StatusTone.Danger -> Color(0xFFF7E8E6) to Color(0xFF8A5A57)
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = container,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun SectionDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun EmptyHint(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun EmptyStateBlock(
    title: String,
    detail: String,
    tone: StatusTone = StatusTone.Neutral,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusChip(text = title, tone = tone)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ActionButtonsRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    if (secondaryLabel != null && onSecondary != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = onPrimary,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(primaryLabel)
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onSecondary,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(secondaryLabel)
            }
        }
    } else {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onPrimary,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(primaryLabel)
        }
    }
}

@Composable
fun ValueRow(
    title: String,
    detail: String,
    tone: StatusTone = StatusTone.Neutral,
) {
    val accent = when (tone) {
        StatusTone.Neutral -> MaterialTheme.colorScheme.outline
        StatusTone.Positive -> Color(0xFF8EA48B)
        StatusTone.Warning -> Color(0xFFC2A47B)
        StatusTone.Danger -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 36.dp)
                .background(accent, RoundedCornerShape(99.dp)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun BrandedMarkHeader(
    title: String,
    subtitle: String,
    darkMark: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(
                id = if (darkMark) R.drawable.finalis_symbol_dark else R.drawable.finalis_symbol,
            ),
            contentDescription = "Finalis symbol",
            modifier = Modifier.size(28.dp),
            contentScale = ContentScale.Fit,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

enum class StatusTone {
    Neutral,
    Positive,
    Warning,
    Danger,
}

@Composable
fun ReceiveQrCard(address: String) {
    val bitmap = remember(address) { generateQrBitmap(address) }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Receive QR",
                style = MaterialTheme.typography.titleMedium,
            )
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Wallet address QR code",
                modifier = Modifier.size(188.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
    )
    val matrix = MultiFormatWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        hints,
    )
    return matrix.toBitmap()
}

private fun BitMatrix.toBitmap(): Bitmap {
    val width = width
    val height = height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(
                x,
                y,
                if (get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
            )
        }
    }
    return bitmap
}
