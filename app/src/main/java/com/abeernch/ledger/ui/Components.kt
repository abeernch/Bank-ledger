package com.abeernch.ledger.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.R
import com.abeernch.ledger.data.Txn
import java.time.YearMonth

/** Standard screen side padding. Everything sits on an 8 dp grid. */
val Gutter = 20.dp

/** A white (light) / charcoal (dark) card with a hairline border. */
@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LedgerTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier.fillMaxWidth().clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = c.card,
        border = BorderStroke(1.dp, c.cardBorder)
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** Section title with an optional action on the right ("See all"). */
@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Header for pushed screens (Settings, People...). */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

/** Big tab title used at the top of each tab. */
@Composable
fun TabTitle(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        trailing()
    }
}

/** Round outlined icon button, 44 dp. */
@Composable
fun CircleIconButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = c.card,
        border = BorderStroke(1.dp, c.cardBorder),
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), label, modifier = Modifier.size(20.dp))
        }
    }
}

/** Thin rounded progress bar. Turns orange when over. */
@Composable
fun Bar(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, height: Dp = 6.dp) {
    val c = LedgerTheme.colors
    val over = fraction > 1f
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(c.subtle)) {
        if (fraction > 0f) {
            Box(
                Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(height)
                    .clip(RoundedCornerShape(height / 2))
                    .background(if (over) c.warning else color)
            )
        }
    }
}

/** Category icon in a tinted rounded square. */
@Composable
fun CategoryIcon(category: String, size: Dp = 40.dp) {
    val s = Palette.category(category)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(s.color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(s.icon), null, tint = s.color, modifier = Modifier.size(size * 0.5f))
    }
}

/** Any icon in a tinted rounded square. */
@Composable
fun TintedIcon(@DrawableRes icon: Int, color: Color, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun ColorDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Amount with sign and colour: money in is green with a plus; money out is plain. */
@Composable
fun Amount(t: Txn, big: Boolean = false) {
    val credit = t.direction == "CREDIT"
    val muted = t.isTransfer || t.isSplit
    Text(
        text = (if (credit) "+" else "−") + Format.number(t.amountMinor) + if (t.currency != "PKR") " ${t.currency}" else "",
        color = when {
            muted -> MaterialTheme.colorScheme.onSurfaceVariant
            credit -> LedgerTheme.colors.credit
            else -> MaterialTheme.colorScheme.onSurface
        },
        style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.bodyLarge,
        fontWeight = if (big) FontWeight.Bold else FontWeight.SemiBold
    )
}

/** One transaction row, as used in every list. */
@Composable
fun TxnRow(t: Txn, data: AppData, onClick: () -> Unit, showDivider: Boolean = true) {
    Column(Modifier.clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIcon(if (t.isSplit) "Other" else t.category)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    Format.title(t.counterparty ?: Format.channelName(t.channel)),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val tags = buildList {
                    add(if (t.isSplit) "Split" else if (t.isTransfer) "Own transfer" else t.category)
                    add(data.accountName(t))
                    data.fund(t.fundId)?.let { add(it.name) }
                    add(Format.time(t.timestamp))
                }
                Text(
                    tags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Amount(t)
        }
        if (showDivider) HorizontalDivider(color = LedgerTheme.colors.cardBorder, modifier = Modifier.padding(start = 52.dp))
    }
}

@Composable
fun MonthSwitcher(month: YearMonth, onMonth: (YearMonth) -> Unit) {
    val c = LedgerTheme.colors
    Surface(shape = RoundedCornerShape(22.dp), color = c.card, border = BorderStroke(1.dp, c.cardBorder)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
                Icon(painterResource(R.drawable.ic_chevron_left), "Previous month", Modifier.size(18.dp))
            }
            Text(Format.month(month), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < YearMonth.now()) {
                Icon(painterResource(R.drawable.ic_chevron_right), "Next month", Modifier.size(18.dp))
            }
        }
    }
}

/** A label and its value on one row, for detail sheets. */
@Composable
fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        Text(
            value + if (onClick != null) "  ›" else "",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Centered message for empty lists. */
@Composable
fun EmptyState(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A list of choices in a dialog, with an optional "add new" text field. */
@Composable
fun PickerDialog(
    title: String,
    options: List<String>,
    current: String?,
    onDismiss: () -> Unit,
    allowNew: String? = null,          // label for the "new" field, null to hide it
    noneLabel: String? = null,         // e.g. "No fund", null to hide
    onPick: (String?) -> Unit
) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    if (noneLabel != null) {
                        item {
                            PickerRow(noneLabel, current == null) { onPick(null) }
                        }
                    }
                    items(options) { o -> PickerRow(o, o == current) { onPick(o) } }
                }
                if (allowNew != null) {
                    OutlinedTextField(
                        value = custom, onValueChange = { custom = it },
                        label = { Text(allowNew) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (allowNew != null) {
                TextButton(onClick = { onPick(custom.trim()) }, enabled = custom.isNotBlank()) { Text("Add") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .background(if (selected) LedgerTheme.colors.accentSoft else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Row of colour swatches for accounts and funds. */
@Composable
fun SwatchRow(selected: Long, onPick: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        for (s in Palette.swatches.take(8)) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Color(s)).clickable { onPick(s) },
                contentAlignment = Alignment.Center
            ) {
                if (s == selected) Icon(painterResource(R.drawable.ic_check), "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Small rounded label, e.g. "7 days left". */
@Composable
fun Pill(text: String, background: Color, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(background).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
