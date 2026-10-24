package com.abeernch.ledger.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.R
import com.abeernch.ledger.data.Txn
import com.abeernch.ledger.logic.Money
import java.time.YearMonth

@Composable
fun ActivityScreen(
    data: AppData,
    onOpenTxn: (Txn) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var direction by rememberSaveable { mutableStateOf<String?>(null) }   // null = all
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var fundId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var pickCategory by remember { mutableStateOf(false) }
    var pickFund by remember { mutableStateOf(false) }

    val from = Format.monthStart(month)
    val to = Format.monthEnd(month)
    val searching = query.isNotBlank()
    // A search looks across all months; otherwise the selected month is shown.
    val base = if (searching) Money.visible(data.txns) else Money.visible(Money.inRange(data.txns, from, to))
    val shown = base.filter { t ->
        (direction == null || t.direction == direction) &&
            (accountId == null || t.accountId == accountId) &&
            (category == null || t.category == category || data.children(t).any { it.category == category }) &&
            (fundId == null || t.fundId == fundId) &&
            (!searching || listOfNotNull(t.counterparty, t.category, t.location, t.note, data.accountName(t), Format.number(t.amountMinor))
                .any { it.contains(query.trim(), ignoreCase = true) })
    }
    val out = shown.filter { Money.isSpend(it) }.sumOf { it.amountMinor } +
        shown.filter { it.isSplit }.flatMap { data.children(it) }.filter { Money.isSpend(it) }.sumOf { it.amountMinor }
    val inn = shown.filter { Money.isIncome(it) }.sumOf { it.amountMinor }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
            item { TabTitle("Activity") { MonthSwitcher(month) { month = it } } }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search payee, note, amount") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(direction == null, { direction = null }, { Text("All") })
                    FilterChip(direction == "DEBIT", { direction = if (direction == "DEBIT") null else "DEBIT" }, { Text("Out") })
                    FilterChip(direction == "CREDIT", { direction = if (direction == "CREDIT") null else "CREDIT" }, { Text("In") })
                    FilterChip(category != null, { if (category != null) category = null else pickCategory = true }, { Text(category ?: "Category") })
                    if (data.funds.isNotEmpty()) {
                        FilterChip(fundId != null, { if (fundId != null) fundId = null else pickFund = true }, { Text(data.fund(fundId)?.name ?: "Fund") })
                    }
                    for (a in data.accounts.filter { !it.archived }) {
                        FilterChip(accountId == a.id, { accountId = if (accountId == a.id) null else a.id }, { Text(a.name) })
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text(
                        if (searching) "${shown.size} results in all months" else "${shown.size} transactions",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Out ${Format.number(out)}", style = MaterialTheme.typography.bodySmall)
                    Text("   In ${Format.number(inn)}", style = MaterialTheme.typography.bodySmall, color = LedgerTheme.colors.credit)
                }
            }

            if (shown.isEmpty()) {
                item { EmptyState("No transactions", if (searching) "Nothing matches your search." else "Nothing logged for this month with these filters.") }
            }

            val byDay = shown.groupBy { Format.localDate(it.timestamp) }
            for ((day, list) in byDay) {
                item(key = "h-$day") {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Format.dayHeader(list.first().timestamp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        val dayOut = list.filter { Money.isSpend(it) }.sumOf { it.amountMinor }
                        if (dayOut > 0) Text("−${Format.number(dayOut)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item(key = "d-$day") {
                    LedgerCard(padding = 4.dp) {
                        Column(Modifier.padding(horizontal = 12.dp)) {
                            list.forEachIndexed { i, t -> TxnRow(t, data, { onOpenTxn(t) }, showDivider = i < list.lastIndex) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(painterResource(R.drawable.ic_add), null) },
            text = { Text("Add") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Gutter)
        )
    }

    if (pickCategory) {
        PickerDialog("Filter by category", data.categories, category, { pickCategory = false }) {
            category = it; pickCategory = false
        }
    }
    if (pickFund) {
        PickerDialog("Filter by fund", data.funds.map { it.name }, data.fund(fundId)?.name, { pickFund = false }) { name ->
            fundId = data.funds.firstOrNull { it.name == name }?.id; pickFund = false
        }
    }
}
