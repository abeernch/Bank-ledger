package com.abeernch.ledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.category.Categorizer
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.data.Rule
import com.abeernch.ledger.data.Txn
import com.abeernch.ledger.widget.SpendWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.YearMonth

@Composable
fun MonthSwitcher(month: YearMonth, onMonth: (YearMonth) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
        }
        Text(Format.month(month), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < YearMonth.now()) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
        }
    }
}

/** Amount with sign and colour: money in is green with a plus. */
@Composable
fun AmountText(t: Txn, big: Boolean = false) {
    val credit = t.direction == "CREDIT"
    Text(
        text = (if (credit) "+ " else "− ") + Format.money(t.amountMinor),
        color = if (credit) creditColor() else MaterialTheme.colorScheme.onSurface,
        style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold
    )
}

/** All categories the user can pick: built-in ones plus any used in rules. */
fun allCategories(rules: List<Rule>): List<String> =
    (Categorizer.builtIn + rules.map { it.category }).distinct()

@Composable
fun CategoryPicker(
    current: String,
    categories: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Category") },
        text = {
            Column {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(categories) { c ->
                        Text(
                            text = if (c == current) "• $c" else c,
                            fontWeight = if (c == current) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 10.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = custom, onValueChange = { custom = it },
                    label = { Text("New category") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (custom.isNotBlank()) onPick(custom.trim()) }, enabled = custom.isNotBlank()) {
                Text("Use new")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Changing a category also saves a rule, so future payments to the same place get it too. */
suspend fun recategorize(db: AppDb, t: Txn, category: String) {
    val name = t.counterparty
    if (name.isNullOrBlank()) {
        db.txns().update(t.copy(category = category))
        return
    }
    val keyword = name.uppercase()
    db.rules().deleteUserRule(keyword)
    db.rules().insert(Rule(keyword = keyword, category = category, isUser = true))
    db.txns().setCategoryFor(name, category)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxnSheet(t: Txn, rules: List<Rule>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDb.get(context) }
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            AmountText(t, big = true)
            Text(t.counterparty ?: Format.channelName(t.channel), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Field("When", Format.full(t.timestamp))
            Field("Bank", t.bank + (t.accountTail?.let { "  ••$it" } ?: ""))
            Field("Type", Format.channelName(t.channel) + if (t.direction == "CREDIT") " · money in" else " · money out")
            t.location?.let { Field("Location", it) }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { picking = true }.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Category", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(t.category + "  ✎", fontWeight = FontWeight.Medium)
            }
            t.balanceMinor?.let { Field("Balance after", Format.money(it)) }
            t.feeMinor?.let { Field("Fee", Format.money(it)) }
            t.reference?.let { Field("Reference", it) }
            Field("Source", t.source.lowercase().replaceFirstChar { it.uppercase() } + if (t.isGeneric) " · read by general rules" else "")
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Original message", style = MaterialTheme.typography.labelLarge)
            SelectionContainer { Text(t.rawText, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete") }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (picking) {
        CategoryPicker(t.category, allCategories(rules), onDismiss = { picking = false }) { c ->
            picking = false
            scope.launch(Dispatchers.IO) { recategorize(db, t, c) }
            onDismiss()
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this transaction?") },
            text = { Text("It will not come back unless the same message is imported again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch(Dispatchers.IO) {
                        db.txns().delete(t)
                        SpendWidget.refresh(context)
                    }
                    onDismiss()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(horizontal = 8.dp))
        Text(value, fontWeight = FontWeight.Medium)
    }
}
