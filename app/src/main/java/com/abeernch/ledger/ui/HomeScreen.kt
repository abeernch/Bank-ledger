package com.abeernch.ledger.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.R
import com.abeernch.ledger.data.Txn
import com.abeernch.ledger.logic.Balances
import com.abeernch.ledger.logic.BudgetMath
import com.abeernch.ledger.logic.Money
import com.abeernch.ledger.logic.Recurring
import java.time.LocalDate
import java.time.YearMonth

/** What the Home tab needs to know about missing permissions. */
data class Setup(
    val needsSms: Boolean,
    val needsListener: Boolean,
    val needsNotifications: Boolean,
    val onSms: () -> Unit,
    val onListener: () -> Unit,
    val onNotifications: () -> Unit
) {
    val any: Boolean get() = needsSms || needsListener || needsNotifications
}

@Composable
fun HomeScreen(
    data: AppData,
    myName: String,
    setup: Setup,
    onOpenTxn: (Txn) -> Unit,
    onGo: (Route) -> Unit,
    onTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val month = YearMonth.now()
    val from = Format.monthStart(month)
    val to = Format.monthEnd(month)
    val prev = month.minusMonths(1)

    val monthTxns = remember(data.txns) { Money.inRange(data.txns, from, to) }
    val prevTxns = remember(data.txns) { Money.inRange(data.txns, Format.monthStart(prev), Format.monthEnd(prev)) }
    val spent = Money.spent(monthTxns)
    val earned = Money.earned(monthTxns)
    val summary = remember(data.txns, data.budgets) {
        BudgetMath.summarize(data.budgets, monthTxns, prevTxns, Format.daysLeft(month))
    }
    val upcoming = remember(data.txns) {
        Recurring.detect(data.txns, LocalDate.now(), Format.zone).filter { !it.nextDate.isAfter(LocalDate.now().plusDays(7)) && !it.nextDate.isBefore(LocalDate.now()) }
    }
    val over = summary.lines.filter { it.isOver }
    val owedToMe = data.debts.filter { it.settledAt == null && it.direction == "LENT" }.sumOf { it.amountMinor }
    val recent = Money.visible(data.txns).take(6)
    val firstName = myName.split(" ").firstOrNull { it.isNotBlank() }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = Gutter)) {
        item {
            TabTitle(
                title = if (firstName != null) "${Format.greeting()}, ${Format.title(firstName)}" else Format.greeting(),
                subtitle = Format.todayLong()
            ) { CircleIconButton(R.drawable.ic_settings, "Settings") { onGo(Route.Settings) } }
        }

        if (setup.any) item { SetupCard(setup) }

        item { Spacer(Modifier.height(8.dp)) }
        item {
            HeroCard(
                hasBudgets = data.budgets.isNotEmpty(),
                spent = spent, earned = earned,
                left = summary.leftMinor, total = summary.totalMinor, fraction = summary.fraction,
                perDay = summary.perDayMinor, daysLeft = Format.daysLeft(month),
                onSetBudget = { onTab(2) }
            )
        }

        // Savings locks: what is actually free to spend across accounts
        if (data.locks.isNotEmpty()) {
            item {
                val pkr = data.accounts.filter { !it.archived && it.currency == "PKR" }
                val locked = data.locks.filter { l -> pkr.any { it.id == l.accountId } }.sumOf { it.amountMinor }
                val available = pkr.sumOf { Balances.account(it, data.txns) } - locked
                LedgerCard(Modifier.padding(top = 10.dp), onClick = { onTab(3) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TintedIcon(R.drawable.ic_lock, MaterialTheme.colorScheme.primary, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Available to use", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                Format.money(available), style = MaterialTheme.typography.titleMedium,
                                color = if (available < 0) LedgerTheme.colors.warning else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Locked", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Format.money(locked), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }

        // Things that need a look
        val alerts = buildList {
            if (data.unparsed.isNotEmpty()) {
                add(AlertItem(R.drawable.ic_warning, true, "${data.unparsed.size} messages could not be read", "Review them so nothing is missed") { onGo(Route.Review) })
            }
            for (l in over) {
                add(AlertItem(Palette.category(l.category).icon, true, "${l.category} is over budget", "${Format.money(-l.leftMinor)} over this month") { onTab(2) })
            }
            for (u in upcoming) {
                add(AlertItem(R.drawable.ic_calendar, false, "${Format.title(u.name)} due ${Format.dayHeader(u.nextDate)}", "Usually ${Format.money(u.typicalMinor)}") { onTab(4) })
            }
            if (owedToMe > 0) {
                add(AlertItem(R.drawable.ic_person, false, "People owe you ${Format.money(owedToMe)}", "Open People & debts") { onGo(Route.People) })
            }
        }
        if (alerts.isNotEmpty()) {
            item { SectionHeader("Needs a look") }
            item {
                LedgerCard(padding = 4.dp) {
                    for (a in alerts) {
                        AlertRow(a.icon, if (a.warn) LedgerTheme.colors.warning else MaterialTheme.colorScheme.primary, a.title, a.body, a.onClick)
                    }
                }
            }
        }

        // Accounts strip
        if (data.accounts.any { !it.archived }) {
            item { SectionHeader("Accounts", "Manage") { onTab(3) } }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (a in data.accounts.filter { !it.archived }) {
                        val balance = Balances.account(a, data.txns)
                        LedgerCard(Modifier.widthIn(min = 128.dp).width(140.dp), onClick = { onTab(3) }, padding = 14.dp) {
                            ColorDot(Color(a.color))
                            Spacer(Modifier.height(8.dp))
                            Text(a.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Format.money(balance, a.currency), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }

        item { SectionHeader("Recent", "See all") { onTab(1) } }
        item {
            if (recent.isEmpty()) {
                LedgerCard { EmptyState("Nothing logged yet", "Bank messages will appear here as they arrive.") }
            } else {
                LedgerCard(padding = 4.dp) {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        recent.forEachIndexed { i, t -> TxnRow(t, data, { onOpenTxn(t) }, showDivider = i < recent.lastIndex) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private class AlertItem(val icon: Int, val warn: Boolean, val title: String, val body: String, val onClick: () -> Unit)

@Composable
private fun HeroCard(
    hasBudgets: Boolean,
    spent: Long, earned: Long,
    left: Long, total: Long, fraction: Float, perDay: Long, daysLeft: Int,
    onSetBudget: () -> Unit
) {
    val c = LedgerTheme.colors
    LedgerCard(padding = 20.dp) {
        if (hasBudgets) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (left >= 0) "Left to spend" else "Over budget", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Pill("$daysLeft days left", c.accentSoft, MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            BigMoney(kotlin.math.abs(left), if (left < 0) c.warning else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            Bar(fraction)
            Spacer(Modifier.height(8.dp))
            Row {
                Text("${Format.number(total - left)} of ${Format.number(total)} used", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (perDay > 0) Text("≈ ${Format.number(perDay)} a day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text("Spent this month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            BigMoney(spent, MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSetBudget) { Text("Set a monthly budget") }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("Money in", Format.money(earned), c.credit, Modifier.weight(1f))
            MiniStat("Money out", Format.money(spent), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
    }
}

/** "PKR" small, number large. */
@Composable
fun BigMoney(minor: Long, color: Color, currency: String = "PKR") {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(currency, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        Spacer(Modifier.width(6.dp))
        Text(Format.number(minor), style = MaterialTheme.typography.displaySmall, color = color)
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = LedgerTheme.colors.subtle) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun AlertRow(icon: Int, color: Color, title: String, body: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TintedIcon(icon, color, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SetupCard(s: Setup) {
    LedgerCard(Modifier.padding(top = 8.dp)) {
        Text("Finish setup", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ledger reads bank texts and bank app notifications. If Android blocks a setting, open App info for Ledger, tap ⋮, choose Allow restricted settings, then try again.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.needsSms) Button(onClick = s.onSms, modifier = Modifier.fillMaxWidth()) { Text("Allow SMS access") }
            if (s.needsListener) Button(onClick = s.onListener, modifier = Modifier.fillMaxWidth()) { Text("Allow notification access") }
            if (s.needsNotifications) OutlinedButton(onClick = s.onNotifications, modifier = Modifier.fillMaxWidth()) { Text("Allow budget alerts") }
        }
    }
}
