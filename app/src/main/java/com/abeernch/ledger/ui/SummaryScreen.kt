package com.abeernch.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.data.Txn
import java.time.YearMonth

@Composable
fun SummaryScreen(txns: List<Txn>, month: YearMonth, onMonth: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    val from = Format.monthStart(month)
    val to = Format.monthEnd(month)
    val inMonth = txns.filter { it.timestamp in from until to }
    val debits = inMonth.filter { it.direction == "DEBIT" }
    val credits = inMonth.filter { it.direction == "CREDIT" }
    val out = debits.sumOf { it.amountMinor }
    val inn = credits.sumOf { it.amountMinor }

    val byCategory = debits.groupBy { it.category }
        .map { (c, l) -> c to l.sumOf { it.amountMinor } }
        .sortedByDescending { it.second }
    val topPlaces = debits.filter { it.counterparty != null }
        .groupBy { it.counterparty!! }
        .map { (c, l) -> c to l.sumOf { it.amountMinor } }
        .sortedByDescending { it.second }
        .take(5)
    val byBank = inMonth.groupBy { it.bank }.map { (b, l) ->
        Triple(b, l.filter { it.direction == "DEBIT" }.sumOf { it.amountMinor }, l.filter { it.direction == "CREDIT" }.sumOf { it.amountMinor })
    }.sortedByDescending { it.second }

    LazyColumn(modifier.fillMaxSize()) {
        item { MonthSwitcher(month, onMonth) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Total("Out", out, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                Total("In", inn, creditColor(), Modifier.weight(1f))
                Total("Net", inn - out, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
        }
        item { Section("Spending by category") }
        if (byCategory.isEmpty()) item { Empty() }
        byCategory.forEach { (c, v) -> item { Bar(c, v, out) } }

        item { Section("Top places") }
        if (topPlaces.isEmpty()) item { Empty() }
        topPlaces.forEach { (c, v) -> item { Bar(c, v, topPlaces.first().second) } }

        item { Section("By bank") }
        if (byBank.isEmpty()) item { Empty() }
        byBank.forEach { (b, o, i) ->
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(b, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("− " + Format.money(o))
                    Text("   + " + Format.money(i), color = creditColor())
                }
            }
        }
        item { Box(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Total(label: String, minor: Long, color: Color, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(Format.money(minor), color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun Empty() {
    Text("Nothing yet.", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A label, its amount, and a bar showing its share of [max]. */
@Composable
private fun Bar(label: String, minor: Long, max: Long) {
    val fraction = if (max > 0) (minor.toFloat() / max).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Format.money(minor), fontWeight = FontWeight.Medium)
        }
        Box(
            Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(MaterialTheme.colorScheme.primary))
        }
    }
}
