package com.abeernch.ledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.data.Alias
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.data.Txn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.YearMonth

private data class BankInfo(
    val name: String,
    val balance: Txn?,      // latest transaction that reported a balance
    val monthOut: Long,
    val monthIn: Long,
    val count: Int
)

@Composable
fun BanksScreen(txns: List<Txn>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDb.get(context) }
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<String?>(null) }

    val month = YearMonth.now()
    val from = Format.monthStart(month)
    val to = Format.monthEnd(month)
    val banks = txns.groupBy { it.bank }.map { (name, list) ->
        val thisMonth = list.filter { it.timestamp in from until to }
        BankInfo(
            name = name,
            balance = list.filter { it.balanceMinor != null }.maxByOrNull { it.timestamp },
            monthOut = thisMonth.filter { it.direction == "DEBIT" }.sumOf { it.amountMinor },
            monthIn = thisMonth.filter { it.direction == "CREDIT" }.sumOf { it.amountMinor },
            count = list.size
        )
    }.sortedBy { it.name }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            Text(
                "Tap a bank to rename it. Balance shows only for banks whose alerts include it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        if (banks.isEmpty()) {
            item { Text("No banks yet.", Modifier.padding(16.dp)) }
        }
        items(banks, key = { it.name }) { b ->
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { renaming = b.name }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(b.name, style = MaterialTheme.typography.titleMedium)
                    if (b.balance != null) {
                        Text(
                            "Balance " + Format.money(b.balance.balanceMinor!!),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "as of " + Format.full(b.balance.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(Modifier.padding(top = 8.dp)) {
                        Text("This month  − " + Format.money(b.monthOut))
                        Text("   + " + Format.money(b.monthIn), color = creditColor())
                    }
                    Text(
                        "Net flow " + Format.money(b.monthIn - b.monthOut) + " · ${b.count} transactions in total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    renaming?.let { old ->
        var name by remember(old) { mutableStateOf(old) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename bank") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && name.trim() != old,
                    onClick = {
                        val newName = name.trim()
                        renaming = null
                        scope.launch(Dispatchers.IO) {
                            // Remember the new name for every sender that fed this bank.
                            for (key in db.txns().senderKeysFor(old)) db.aliases().put(Alias(key, newName))
                            db.txns().renameBank(old, newName)
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }
}
