package com.abeernch.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.R
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.data.Budget
import com.abeernch.ledger.data.Fund
import com.abeernch.ledger.logic.Balances
import com.abeernch.ledger.logic.BudgetLine
import com.abeernch.ledger.logic.BudgetMath
import com.abeernch.ledger.logic.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.YearMonth

@Composable
fun BudgetsScreen(data: AppData, onGo: (Route) -> Unit, modifier: Modifier = Modifier) {
    var section by rememberSaveable { mutableStateOf(0) }   // 0 monthly, 1 funds, 2 goals
    var month by remember { mutableStateOf(YearMonth.now()) }
    var editBudget by remember { mutableStateOf<Budget?>(null) }
    var newBudget by remember { mutableStateOf(false) }
    var editFund by remember { mutableStateOf<Fund?>(null) }
    var newFundKind by remember { mutableStateOf<String?>(null) }

    val from = Format.monthStart(month)
    val to = Format.monthEnd(month)
    val prev = month.minusMonths(1)
    val monthTxns = remember(data.txns, month) { Money.inRange(data.txns, from, to) }
    val prevTxns = remember(data.txns, month) { Money.inRange(data.txns, Format.monthStart(prev), Format.monthEnd(prev)) }
    val summary = remember(data.txns, data.budgets, month) {
        BudgetMath.summarize(data.budgets, monthTxns, prevTxns, Format.daysLeft(month))
    }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = Gutter)) {
        item {
            TabTitle("Budgets") {
                CircleIconButton(R.drawable.ic_add, "Add") {
                    when (section) {
                        0 -> newBudget = true
                        1 -> newFundKind = "FUND"
                        else -> newFundKind = "GOAL"
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                FilterChip(section == 0, { section = 0 }, { Text("Monthly") })
                FilterChip(section == 1, { section = 1 }, { Text("Funds") })
                FilterChip(section == 2, { section = 2 }, { Text("Goals") })
            }
        }

        when (section) {
            0 -> {
                item { Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) { MonthSwitcher(month) { month = it } } }
                if (data.budgets.isEmpty()) {
                    item {
                        LedgerCard {
                            EmptyState("No budgets yet", "Tap + to set a monthly limit for a category, like Food or Fuel.")
                        }
                    }
                } else {
                    item { BudgetHero(summary) }
                    item { SectionHeader("Categories") }
                    item {
                        LedgerCard(padding = 4.dp) {
                            summary.lines.forEach { line ->
                                BudgetRow(line) { editBudget = data.budgets.firstOrNull { it.category == line.category } }
                            }
                        }
                    }
                    if (summary.unbudgetedMinor > 0) {
                        item {
                            Text(
                                "Spending in categories without a budget: ${Format.money(summary.unbudgetedMinor)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
            1, 2 -> {
                val kind = if (section == 1) "FUND" else "GOAL"
                val list = data.funds.filter { it.kind == kind }
                item {
                    Text(
                        if (kind == "FUND") "A fund tracks spending for one purpose, like a wedding or a trip. Assign payments to it from any transaction."
                        else "A goal tracks money you are saving towards a target. Assign deposits to it, or set what is already saved.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                if (list.isEmpty()) {
                    item { LedgerCard { EmptyState(if (kind == "FUND") "No funds yet" else "No goals yet", "Tap + to create one.") } }
                }
                list.forEach { f ->
                    item(key = "f-${f.id}") {
                        FundCard(f, data) { onGo(Route.FundDetail(f.id)) }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (newBudget || editBudget != null) {
        BudgetDialog(
            existing = editBudget,
            categories = data.categories.filter { c -> editBudget != null || data.budgets.none { it.category == c } },
            onDismiss = { newBudget = false; editBudget = null }
        )
    }
    if (newFundKind != null || editFund != null) {
        FundDialog(existing = editFund, kind = editFund?.kind ?: newFundKind!!, onDismiss = { newFundKind = null; editFund = null })
    }
}

@Composable
private fun BudgetHero(s: com.abeernch.ledger.logic.BudgetSummary) {
    val over = s.leftMinor < 0
    LedgerCard(padding = 20.dp) {
        Text(if (over) "Over budget" else "Left to spend", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        BigMoney(kotlin.math.abs(s.leftMinor), if (over) LedgerTheme.colors.warning else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Bar(s.fraction)
        Spacer(Modifier.height(8.dp))
        Row {
            Text("${Format.number(s.spentMinor)} of ${Format.number(s.totalMinor)} used", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (s.perDayMinor > 0) Text("≈ ${Format.number(s.perDayMinor)} a day · ${s.daysLeft} days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BudgetRow(line: BudgetLine, onClick: () -> Unit) {
    androidx.compose.material3.Surface(onClick = onClick, color = Color.Transparent) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(line.category, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(line.category, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        if (line.isOver) "${Format.number(-line.leftMinor)} over" else "${Format.number(line.leftMinor)} left" +
                            if (line.carryMinor > 0) " · ${Format.number(line.carryMinor)} carried over" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (line.isOver) LedgerTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(Format.number(line.spentMinor), fontWeight = FontWeight.SemiBold)
                Text(" / ${Format.number(line.availableMinor)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(10.dp))
            Bar(line.fraction, color = Palette.category(line.category).color)
        }
    }
}

@Composable
private fun FundCard(f: Fund, data: AppData, onClick: () -> Unit) {
    val isGoal = f.kind == "GOAL"
    val value = if (isGoal) com.abeernch.ledger.logic.Locks.goalSaved(f, data.txns, data.locks) else Balances.fundSpent(f, data.txns)
    val count = data.txns.count { it.fundId == f.id }
    LedgerCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TintedIcon(if (isGoal) R.drawable.ic_goal else R.drawable.ic_fund, Color(f.color), 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(f.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (isGoal) "Saved" else "Spent") + " · $count transactions",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(Format.money(value), fontWeight = FontWeight.SemiBold)
        }
        f.targetMinor?.let { target ->
            Spacer(Modifier.height(12.dp))
            Bar(if (target > 0) value.toFloat() / target else 0f, color = Color(f.color))
            Spacer(Modifier.height(6.dp))
            Text(
                if (isGoal) "${percent(value, target)}% of ${Format.money(target)}" else "${Format.money(target - value)} left of ${Format.money(target)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun percent(v: Long, of: Long): Int = if (of > 0) (v * 100 / of).toInt() else 0

@Composable
private fun BudgetDialog(existing: Budget?, categories: List<String>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf(existing?.category ?: categories.firstOrNull() ?: "") }
    var amount by remember { mutableStateOf(existing?.let { Format.toInput(it.limitMinor) } ?: "") }
    var rollover by remember { mutableStateOf(existing?.rollover ?: false) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val minor = Format.parseMinor(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New budget" else "Edit budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (existing == null) {
                    androidx.compose.material3.OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(category.ifBlank { "Pick category" })
                    }
                } else {
                    Text(category, style = MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Monthly limit (PKR)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Roll over unused", style = MaterialTheme.typography.bodyMedium)
                        Text("Add last month's leftover to this month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(rollover, { rollover = it })
                }
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Remove budget", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = minor != null && minor > 0 && category.isNotBlank(),
                onClick = {
                    scope.launch(Dispatchers.IO) { AppDb.get(ctx).budgets().put(Budget(category, minor!!, rollover)) }
                    onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (picking) {
        PickerDialog("Category", categories, category, { picking = false }, allowNew = "New category") { c ->
            if (c != null) category = c
            picking = false
        }
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog("Remove the ${existing.category} budget?", "Transactions are not affected.", "Remove", { confirmDelete = false }) {
            scope.launch(Dispatchers.IO) { AppDb.get(ctx).budgets().delete(existing) }
            onDismiss()
        }
    }
}

/** Create or edit a fund (spending pot) or goal (savings target). */
@Composable
fun FundDialog(existing: Fund?, kind: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isGoal = kind == "GOAL"
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var target by remember { mutableStateOf(existing?.targetMinor?.let { Format.toInput(it) } ?: "") }
    var start by remember { mutableStateOf(existing?.startMinor?.takeIf { it != 0L }?.let { Format.toInput(it) } ?: "") }
    var color by remember { mutableStateOf(existing?.color ?: Palette.swatches[if (isGoal) 2 else 5]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((if (existing == null) "New " else "Edit ") + if (isGoal) "goal" else "fund") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    target, { target = it }, singleLine = true,
                    label = { Text(if (isGoal) "Target amount (optional)" else "Budget for this fund (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                if (isGoal) {
                    OutlinedTextField(
                        start, { start = it }, singleLine = true, label = { Text("Already saved") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                    )
                }
                SwatchRow(color) { color = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val f = Fund(
                        id = existing?.id ?: 0, name = name.trim(), kind = kind, color = color,
                        targetMinor = Format.parseMinor(target)?.takeIf { it > 0 },
                        startMinor = Format.parseMinor(start) ?: 0L,
                        note = existing?.note, archived = existing?.archived ?: false
                    )
                    scope.launch(Dispatchers.IO) {
                        val dao = AppDb.get(ctx).funds()
                        if (existing == null) dao.insert(f) else dao.update(f)
                    }
                    onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
