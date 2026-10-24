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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.R
import com.abeernch.ledger.data.Account
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.logic.Balances
import com.abeernch.ledger.logic.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.YearMonth

@Composable
fun AccountsScreen(data: AppData, onGo: (Route) -> Unit, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf<Account?>(null) }
    var adding by remember { mutableStateOf(false) }

    val month = YearMonth.now()
    val monthTxns = Money.inRange(data.txns, Format.monthStart(month), Format.monthEnd(month))
    val active = data.accounts.filter { !it.archived }
    val archived = data.accounts.filter { it.archived }
    val byCurrency = active.groupBy { it.currency }.mapValues { (_, l) -> l.sumOf { Balances.account(it, data.txns) } }
    val openDebts = data.debts.filter { it.settledAt == null }
    val owedToMe = openDebts.filter { it.direction == "LENT" }.sumOf { it.amountMinor }
    val iOwe = openDebts.filter { it.direction == "BORROWED" }.sumOf { it.amountMinor }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = Gutter)) {
        item { TabTitle("Accounts") { CircleIconButton(R.drawable.ic_add, "Add account") { adding = true } } }

        item {
            LedgerCard(padding = 20.dp) {
                Text("Total balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BigMoney(byCurrency["PKR"] ?: 0L, MaterialTheme.colorScheme.onSurface)
                byCurrency.filterKeys { it != "PKR" }.forEach { (cur, v) ->
                    Text(Format.money(v, cur), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "Opening balance plus everything logged. Set each account's current balance once to make this exact.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        item { SectionHeader("Your accounts") }
        if (active.isEmpty()) item { LedgerCard { EmptyState("No accounts yet", "They appear when the first bank message arrives, or tap + to add cash.") } }
        active.forEach { a ->
            item(key = "a-${a.id}") {
                AccountCard(a, data, monthTxns) { editing = a }
                Spacer(Modifier.height(10.dp))
            }
        }

        item { SectionHeader("People & debts", "Open") { onGo(Route.People) } }
        item {
            LedgerCard(onClick = { onGo(Route.People) }) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Owed to you", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Format.money(owedToMe), style = MaterialTheme.typography.titleMedium, color = LedgerTheme.colors.credit)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("You owe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Format.money(iOwe), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text("${openDebts.size} open · mark any transfer as a loan from its detail sheet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }

        if (archived.isNotEmpty()) {
            item { SectionHeader("Archived") }
            archived.forEach { a ->
                item(key = "x-${a.id}") {
                    AccountCard(a, data, monthTxns) { editing = a }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (adding || editing != null) {
        AccountDialog(existing = editing, data = data, onDismiss = { adding = false; editing = null })
    }
}

@Composable
private fun AccountCard(a: Account, data: AppData, monthTxns: List<com.abeernch.ledger.data.Txn>, onClick: () -> Unit) {
    val balance = Balances.account(a, data.txns)
    val reported = Balances.reported(a, data.txns)
    val mine = monthTxns.filter { it.accountId == a.id }
    val out = Money.spent(mine)
    val inn = Money.earned(mine)
    LedgerCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TintedIcon(
                when (a.kind) { "CASH" -> R.drawable.ic_cash; "SAVINGS" -> R.drawable.ic_goal; else -> R.drawable.ic_wallet },
                Color(a.color), 42.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(kindName(a.kind) + if (a.currency != "PKR") " · ${a.currency}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(Format.money(balance, a.currency), style = MaterialTheme.typography.titleMedium)
        }
        if (reported != null) {
            Text(
                "Bank said ${Format.money(reported.balanceMinor!!, a.currency)} on ${Format.date(reported.timestamp)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Row(Modifier.padding(top = 8.dp)) {
            Text("This month  −${Format.number(out)}", style = MaterialTheme.typography.bodySmall)
            Text("   +${Format.number(inn)}", style = MaterialTheme.typography.bodySmall, color = LedgerTheme.colors.credit)
        }
    }
}

private fun kindName(k: String) = when (k) {
    "WALLET" -> "Mobile wallet"
    "CASH" -> "Cash"
    "SAVINGS" -> "Savings"
    else -> "Bank account"
}

@Composable
private fun AccountDialog(existing: Account?, data: AppData, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: "CASH") }
    var currency by remember { mutableStateOf(existing?.currency ?: "PKR") }
    var color by remember { mutableStateOf(existing?.color ?: Palette.swatches[9]) }
    val currentBalance = existing?.let { Balances.account(it, data.txns) }
    var balance by remember { mutableStateOf(currentBalance?.let { Format.toInput(it) } ?: "") }
    var merging by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New account" else "Edit account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (k in listOf("BANK", "WALLET", "CASH", "SAVINGS")) {
                        FilterChip(kind == k, { kind = k }, { Text(kindName(k).substringBefore(" ")) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in listOf("PKR", "USD", "GBP", "EUR")) FilterChip(currency == c, { currency = c }, { Text(c) })
                }
                OutlinedTextField(
                    balance, { balance = it }, singleLine = true, label = { Text("Current balance") },
                    supportingText = { Text("Type what the bank shows now. Future transactions update it.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                SwatchRow(color) { color = it }
                if (existing != null) {
                    if (existing.bank.isNotBlank() && !existing.bank.startsWith("manual:")) {
                        Text("Linked to messages from: ${existing.bank}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { merging = true }) { Text("Merge into…") }
                        OutlinedButton(onClick = {
                            scope.launch(Dispatchers.IO) { AppDb.get(ctx).accounts().update(existing.copy(archived = !existing.archived)) }
                            onDismiss()
                        }) { Text(if (existing.archived) "Unarchive" else "Archive") }
                    }
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                scope.launch(Dispatchers.IO) {
                    val dao = AppDb.get(ctx).accounts()
                    val target = Format.parseMinor(balance.replace("−", "-"))
                    if (existing == null) {
                        dao.insert(
                            Account(
                                name = name.trim(), bank = "manual:" + name.trim(), kind = kind, color = color,
                                currency = currency, openingMinor = target ?: 0L, openingDate = 0
                            )
                        )
                    } else {
                        // Opening balance is whatever makes the computed balance equal what was typed.
                        val withoutOpening = Balances.account(existing.copy(openingMinor = 0, openingDate = 0, currency = currency), data.txns)
                        dao.update(
                            existing.copy(
                                name = name.trim(), kind = kind, color = color, currency = currency,
                                openingMinor = if (target != null) target - withoutOpening else existing.openingMinor,
                                openingDate = if (target != null) 0 else existing.openingDate
                            )
                        )
                    }
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (merging && existing != null) {
        val others = data.accounts.filter { it.id != existing.id }
        PickerDialog("Move all of ${existing.name}'s transactions to", others.map { it.name }, null, { merging = false }) { pick ->
            val to = others.firstOrNull { it.name == pick }
            merging = false
            if (to != null) {
                scope.launch(Dispatchers.IO) {
                    val db = AppDb.get(ctx)
                    // Future messages from the same senders go straight to the target account.
                    for (key in db.txns().senderKeysFor(existing.id)) {
                        if (key != "manual") db.aliases().put(com.abeernch.ledger.data.Alias(key, to.bank))
                    }
                    db.txns().moveAccount(existing.id, to.id)
                    db.accounts().update(existing.copy(archived = true))
                }
                onDismiss()
            }
        }
    }
    if (confirmDelete && existing != null) {
        val count = data.txns.count { it.accountId == existing.id }
        ConfirmDialog(
            "Delete ${existing.name}?",
            if (count > 0) "It has $count transactions. Merge it into another account instead, or archive it." else "This cannot be undone.",
            if (count > 0) "Archive instead" else "Delete", { confirmDelete = false }
        ) {
            scope.launch(Dispatchers.IO) {
                val dao = AppDb.get(ctx).accounts()
                if (count > 0) dao.update(existing.copy(archived = true)) else dao.delete(existing)
            }
            onDismiss()
        }
    }
}
