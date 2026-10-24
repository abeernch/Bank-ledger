package com.abeernch.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.logic.Insights
import com.abeernch.ledger.logic.Money
import com.abeernch.ledger.logic.Recurring
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InsightsScreen(data: AppData, modifier: Modifier = Modifier) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val zone = Format.zone
    val prev = month.minusMonths(1)
    val thisTxns = remember(data.txns, month) { Money.inRange(data.txns, Format.monthStart(month), Format.monthEnd(month)) }
    val prevTxns = remember(data.txns, month) { Money.inRange(data.txns, Format.monthStart(prev), Format.monthEnd(prev)) }
    val series = remember(data.txns, month) { Insights.monthly(data.txns, month, 6, zone) }
    val changes = remember(data.txns, month) { Insights.categoryChanges(thisTxns, prevTxns) }
    val payees = remember(data.txns, month) { Insights.topPayees(thisTxns, 5) }
    val weekdays = remember(data.txns, month) { Insights.byWeekday(thisTxns, zone) }
    val recurring = remember(data.txns) { Recurring.detect(data.txns, LocalDate.now(), zone) }

    val spent = Money.spent(thisTxns)
    val lastSpent = Money.spent(prevTxns)
    val avg = Insights.dailyAverage(spent, Format.daysElapsed(month))

    LazyColumn(modifier.fillMaxSize().padding(horizontal = Gutter)) {
        item { TabTitle("Insights") { MonthSwitcher(month) { month = it } } }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Spent", Format.money(spent), Modifier.weight(1f))
                StatCard("Daily average", Format.money(avg), Modifier.weight(1f))
            }
        }
        item {
            val delta = spent - lastSpent
            Text(
                when {
                    lastSpent == 0L -> "No spending logged in ${Format.month(prev)} to compare."
                    delta > 0 -> "${Format.money(delta)} more than ${Format.month(prev)}."
                    delta < 0 -> "${Format.money(-delta)} less than ${Format.month(prev)}."
                    else -> "Same as ${Format.month(prev)}."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }

        item { SectionHeader("Last 6 months") }
        item { MonthChart(series, month) }

        item { SectionHeader("By category") }
        item {
            LedgerCard(padding = 4.dp) {
                if (changes.isEmpty()) EmptyState("No spending", "Nothing to compare yet.")
                changes.forEachIndexed { i, c ->
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryIcon(c.category, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.category, fontWeight = FontWeight.Medium)
                            val d = c.deltaMinor
                            Text(
                                when {
                                    c.lastMinor == 0L -> "New this month"
                                    d > 0 -> "▲ ${Format.number(d)} vs last month"
                                    d < 0 -> "▼ ${Format.number(-d)} vs last month"
                                    else -> "Same as last month"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (d > 0 && c.lastMinor > 0) LedgerTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(Format.number(c.thisMinor), fontWeight = FontWeight.SemiBold)
                    }
                    if (i < changes.lastIndex) HorizontalDivider(color = LedgerTheme.colors.cardBorder, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }

        item { SectionHeader("Top payees") }
        item {
            LedgerCard {
                if (payees.isEmpty()) EmptyState("No payees", "Nothing spent this month.")
                val max = payees.maxOfOrNull { it.minor } ?: 1L
                payees.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(Format.title(p.name), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.count}×  ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Text(Format.number(p.minor), fontWeight = FontWeight.SemiBold)
                    }
                    Bar(p.minor.toFloat() / max, Modifier.padding(top = 6.dp, bottom = 4.dp))
                }
            }
        }

        item { SectionHeader("By day of week") }
        item { WeekdayChart(weekdays) }

        item { SectionHeader("Recurring payments") }
        item {
            LedgerCard(padding = 4.dp) {
                if (recurring.isEmpty()) EmptyState("None found yet", "Payments to the same place in two or more months show up here.")
                recurring.forEachIndexed { i, r ->
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryIcon(r.category, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Format.title(r.name), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Next around ${Format.date(r.nextDate)} · seen ${r.months} months", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("≈ ${Format.number(r.typicalMinor)}", fontWeight = FontWeight.SemiBold)
                    }
                    if (i < recurring.lastIndex) HorizontalDivider(color = LedgerTheme.colors.cardBorder, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    LedgerCard(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

/** Paired bars per month: money out (accent) and money in (green). */
@Composable
private fun MonthChart(series: List<com.abeernch.ledger.logic.MonthTotals>, selected: YearMonth) {
    val max = series.maxOfOrNull { maxOf(it.spentMinor, it.earnedMinor) }?.takeIf { it > 0 } ?: 1L
    val accent = MaterialTheme.colorScheme.primary
    val credit = LedgerTheme.colors.credit
    LedgerCard {
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            for (m in series) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                    Text(Format.moneyShort(m.spentMinor), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                        ChartBar(m.spentMinor.toFloat() / max, if (m.month == selected) accent else accent.copy(alpha = 0.45f))
                        ChartBar(m.earnedMinor.toFloat() / max, if (m.month == selected) credit else credit.copy(alpha = 0.45f))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            for (m in series) {
                Text(
                    Format.monthShort(m.month), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = if (m.month == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (m.month == selected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(accent, 8.dp); Text("  Out", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(16.dp))
            ColorDot(credit, 8.dp); Text("  In", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ChartBar(fraction: Float, color: Color) {
    Box(Modifier.width(12.dp).fillMaxHeight(fraction.coerceIn(0.01f, 1f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color))
}

@Composable
private fun WeekdayChart(days: List<Pair<java.time.DayOfWeek, Long>>) {
    val max = days.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1L
    val accent = MaterialTheme.colorScheme.primary
    LedgerCard {
        Row(Modifier.fillMaxWidth().height(100.dp), verticalAlignment = Alignment.Bottom) {
            for ((d, v) in days) {
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Box(Modifier.width(20.dp).fillMaxHeight((v.toFloat() / max).coerceIn(0.02f, 1f)).clip(RoundedCornerShape(6.dp)).background(if (v == max && v > 0) accent else accent.copy(alpha = 0.35f)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            for ((d, _) in days) {
                Text(
                    d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
