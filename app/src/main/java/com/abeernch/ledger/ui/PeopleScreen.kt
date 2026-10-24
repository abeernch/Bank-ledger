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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.R
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.data.Debt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Money lent and borrowed, grouped by person. */
@Composable
fun PeopleScreen(data: AppData, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Debt?>(null) }

    val openDebts = data.debts.filter { it.settledAt == null }
    val settled = data.debts.filter { it.settledAt != null }
    // Net per person: positive = they owe you.
    val byPerson = openDebts.groupBy { it.person.trim().lowercase() }.map { (_, l) ->
        l.first().person to l.sumOf { if (it.direction == "LENT") it.amountMinor else -it.amountMinor }
    }.sortedByDescending { kotlin.math.abs(it.second) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("People & debts", onBack) { CircleIconButton(R.drawable.ic_add, "Add") { adding = true } }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
            item {
                LedgerCard {
                    if (byPerson.isEmpty()) {
                        EmptyState("All settled", "Mark a transfer as a loan from its detail sheet, or tap + to add one.")
                    } else {
                        byPerson.forEachIndexed { i, (person, net) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                TintedIcon(R.drawable.ic_person, MaterialTheme.colorScheme.primary, 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(Format.title(person), fontWeight = FontWeight.SemiBold)
                                    Text(if (net >= 0) "Owes you" else "You owe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    Format.money(kotlin.math.abs(net)), fontWeight = FontWeight.SemiBold,
                                    color = if (net >= 0) LedgerTheme.colors.credit else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (i < byPerson.lastIndex) HorizontalDivider(color = LedgerTheme.colors.cardBorder)
                        }
                    }
                }
            }
            if (openDebts.isNotEmpty()) {
                item { SectionHeader("Open") }
                item { DebtList(openDebts) { open = it } }
            }
            if (settled.isNotEmpty()) {
                item { SectionHeader("Settled") }
                item { DebtList(settled) { open = it } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (adding) DebtDialog { adding = false }
    open?.let { d ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(Format.title(d.person)) },
            text = {
                Column {
                    Text((if (d.direction == "LENT") "You lent " else "You borrowed ") + Format.money(d.amountMinor) + " on " + Format.date(d.date))
                    d.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    d.settledAt?.let { Text("Settled on ${Format.date(it)}", color = LedgerTheme.colors.credit) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val updated = d.copy(settledAt = if (d.settledAt == null) System.currentTimeMillis() else null)
                    scope.launch(Dispatchers.IO) { AppDb.get(ctx).debts().update(updated) }
                    open = null
                }) { Text(if (d.settledAt == null) "Mark settled" else "Reopen") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { AppDb.get(ctx).debts().delete(d) }
                    open = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        )
    }
}

@Composable
private fun DebtList(list: List<Debt>, onClick: (Debt) -> Unit) {
    LedgerCard(padding = 4.dp) {
        list.forEachIndexed { i, d ->
            androidx.compose.material3.Surface(onClick = { onClick(d) }, color = androidx.compose.ui.graphics.Color.Transparent) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Format.title(d.person), fontWeight = FontWeight.Medium)
                        Text(
                            (if (d.direction == "LENT") "Lent" else "Borrowed") + " · " + Format.date(d.date) + (d.note?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(Format.money(d.amountMinor), fontWeight = FontWeight.SemiBold, color = if (d.settledAt != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                }
            }
            if (i < list.lastIndex) HorizontalDivider(color = LedgerTheme.colors.cardBorder, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

@Composable
private fun DebtDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var lent by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    val minor = Format.parseMinor(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New debt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(lent, { lent = true }, { Text("I lent") })
                    FilterChip(!lent, { lent = false }, { Text("I borrowed") })
                }
                OutlinedTextField(person, { person = it }, label = { Text("Person") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Amount (PKR)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = person.isNotBlank() && minor != null && minor > 0, onClick = {
                scope.launch(Dispatchers.IO) {
                    AppDb.get(ctx).debts().insert(
                        Debt(person = person.trim(), direction = if (lent) "LENT" else "BORROWED", amountMinor = minor!!, date = System.currentTimeMillis(), note = note.trim().ifEmpty { null })
                    )
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
