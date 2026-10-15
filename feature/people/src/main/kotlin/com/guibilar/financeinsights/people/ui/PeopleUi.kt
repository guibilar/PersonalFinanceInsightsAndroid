package com.guibilar.financeinsights.people.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guibilar.financeinsights.core.people.initialsOf
import com.guibilar.financeinsights.core.people.monthShort
import java.time.LocalDate

/** The colour pairs of the avatars (the web app's): a person keeps the same pair, from the name. */
private val avatarTones = listOf(
    Color(0xFFF6E6EC) to Color(0xFF8A2E52),
    Color(0xFFE8EEF6) to Color(0xFF2D5287),
    Color(0xFFE3F0EE) to Color(0xFF0E6B63),
    Color(0xFFECE7F5) to Color(0xFF4F3A80),
    Color(0xFFFBEBDC) to Color(0xFF9A4F10),
    Color(0xFFEEEAE3) to Color(0xFF4A463F),
)

/** The tone of a name: the same hash as the web app. */
internal fun avatarTone(name: String): Int {
    var hash = 0
    for (ch in name) hash = hash * 31 + ch.code
    return Math.floorMod(hash, avatarTones.size)
}

/** A round badge with the initials of a person. Decoration only: the name is next to it. */
@Composable
fun PersonAvatar(name: String, size: Dp = 40.dp) {
    val (background, foreground) = avatarTones[avatarTone(name)]
    Box(
        Modifier.size(size).background(background, CircleShape).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(initialsOf(name), color = foreground, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
    }
}

/** The month and the day of a birthday, as a small square ("NOV" over "2"). Decoration only. */
@Composable
fun DateBadge(day: LocalDate) {
    Column(
        Modifier.size(42.dp).background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(12.dp)).clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(monthShort(day), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
        Text(day.dayOfMonth.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

/** A small capital title over a list. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/** A card of rows with lines between them. */
@Composable
fun RowsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) { Column { content() } }
}

/** One row of a [RowsCard]: tap target of 48 dp or more; a line under it unless [last]. */
@Composable
fun CardRow(onClick: (() -> Unit)?, last: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
    if (!last) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** A name over a smaller line. */
@Composable
fun RowScope.TwoLines(title: String, subtitle: String?, subtitleColor: Color = Color.Unspecified) {
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (subtitleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else subtitleColor,
            )
        }
    }
}
