package com.guibilar.financeinsights.documents.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.documents.DocumentKind
import com.guibilar.financeinsights.core.documents.OcrStatus
import com.guibilar.financeinsights.core.documents.expiryLabel
import com.guibilar.financeinsights.ui.common.budgetWarningText
import com.guibilar.financeinsights.ui.theme.LocalChartColors

// What the Documents screens share: the kind icons, and the small labels for the OCR status and the expiry.

/** The icon of a kind of document. */
val DocumentKind.icon: ImageVector
    get() = when (this) {
        DocumentKind.Receipt -> Icons.AutoMirrored.Outlined.ReceiptLong
        DocumentKind.Warranty -> Icons.Outlined.VerifiedUser
        DocumentKind.Lease -> Icons.Outlined.Home
        DocumentKind.Tax -> Icons.Outlined.RequestQuote
        DocumentKind.Insurance -> Icons.Outlined.HealthAndSafety
        DocumentKind.Manual -> Icons.AutoMirrored.Outlined.MenuBook
        DocumentKind.Other -> Icons.AutoMirrored.Outlined.Article
    }

/** A kind's icon in a round tile, as the Money home's rows have. */
@Composable
fun KindIcon(kind: DocumentKind?, modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(kind?.icon ?: Icons.Outlined.Description, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** A small rounded label. */
@Composable
fun Badge(text: String, background: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = content,
        maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(background).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** The days to the expiry: amber within 30 days, red when it expired, quiet after that. */
@Composable
fun ExpiryBadge(days: Long, modifier: Modifier = Modifier) {
    val colors = LocalChartColors.current
    when {
        days < 0 -> Badge(expiryLabel(days), colors.negative.copy(alpha = 0.14f), colors.negative, modifier)
        days <= 30 -> Badge(expiryLabel(days), MaterialTheme.colorScheme.tertiaryContainer, budgetWarningText, modifier)
        else -> Badge(expiryLabel(days), MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, modifier)
    }
}

/** The OCR status, when it says something: "Reading text" while it runs, "Text not read" when it failed. */
@Composable
fun OcrBadge(status: OcrStatus, modifier: Modifier = Modifier) {
    when (status) {
        OcrStatus.Pending -> Badge("Reading text", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, modifier)
        OcrStatus.Failed -> Badge("Text not read", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, modifier)
        else -> {}
    }
}
