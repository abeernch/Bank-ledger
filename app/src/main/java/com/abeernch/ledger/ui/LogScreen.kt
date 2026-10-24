package com.abeernch.ledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.data.Rule
import com.abeernch.ledger.data.Txn
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    txns: List<Txn>,
    rules: List<Rule>,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    needsSms: Boolean,
    needsListener: Boolean,
    onAskSms: () -> Unit,
    onOpenListener: () -> Unit,
    modifier: Modifier = Modifier
) {
    var direction by remember { mutableStateOf<String?>(null) }   // null = all
    var bank by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<Txn?>(null) }

    val from = Format.monthStart(month)
    val to = Format.monthEnd(month)
    val inMonth = txns.filter { it.timestamp in from until to }
    val banks = txns.map { it.bank }.distinct().sorted()
    val shown = inMonth.filter { t ->
        (direction == null || t.direction == direction) &&
            (bank == null || t.bank == bank) &&
            (query.isBlank() || listOfNotNull(t.counterparty, t.category, t.location, t.bank)
                .any { it.contains(query.trim(), ignoreCase = true) })
    }
    val out = shown.filter { it.direction == "DEBIT" }.sumOf { it.amountMinor }
    val inn = shown.filter { it.direction == "CREDIT" }.sumOf { it.amountMinor }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (needsSms || needsListener) {
            item { SetupCard(needsSms, needsListener, onAskSms, onOpenListener) }
        }
        item { MonthSwitcher(month, onMonth) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Out  " + Format.money(out), style = MaterialTheme.typography.titleSmall)
                Text("In  " + Format.money(inn), style = MaterialTheme.typography.titleSmall, color = creditColor())
            }
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(direction == null, { direction = null }, { Text("All") })
                FilterChip(direction == "DEBIT", { direction = "DEBIT" }, { Text("Out") })
                FilterChip(direction == "CREDIT", { direction = "CREDIT" }, { Text("In") })
                for (b in banks) {
                    FilterChip(bank == b, { bank = if (bank == b) null else b }, { Text(b) })
                }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search merchant, category, place") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (shown.isEmpty()) {
            item {
                Text(
                    "No transactions for this month yet.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Group by day, newest first (the list already comes sorted by time).
        val byDay = shown.groupBy { Format.day(it.timestamp) }
        for ((day, list) in byDay) {
            item(key = "h-$day") {
                Text(
                    day,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            items(list, key = { it.id }) { t -> TxnRow(t) { open = t } }
        }
    }

    open?.let { t -> TxnSheet(t, rules) { open = null } }
}

@Composable
private fun TxnRow(t: Txn, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    t.counterparty ?: Format.channelName(t.channel),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${t.bank} · ${t.category} · ${Format.time(t.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            AmountText(t)
        }
        HorizontalDivider(Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun SetupCard(needsSms: Boolean, needsListener: Boolean, onAskSms: () -> Unit, onOpenListener: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Finish setup", style = MaterialTheme.typography.titleMedium)
            Text(
                "The app needs SMS access for bank texts and notification access for bank apps. " +
                    "If Android blocks either one, open App info, tap the ⋮ menu, choose Allow restricted settings, then try again.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            if (needsSms) Button(onClick = onAskSms) { Text("Allow SMS access") }
            if (needsListener) Button(onClick = onOpenListener) { Text("Allow notification access") }
        }
    }
}
