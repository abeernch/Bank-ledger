package com.abeernch.ledger.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.abeernch.ledger.data.LockEvent
import com.abeernch.ledger.data.SavingsLock
import com.abeernch.ledger.logic.Locks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Every change to a lock also writes a history entry. Call from Dispatchers.IO. */
object LockActions {

    private suspend fun log(ctx: Context, l: SavingsLock, delta: Long, reason: String) =
        AppDb.get(ctx).lockEvents().insert(LockEvent(lockId = l.id, lockName = l.name, deltaMinor = delta, at = System.currentTimeMillis(), reason = reason))

    suspend fun create(ctx: Context, l: SavingsLock) {
        val id = AppDb.get(ctx).locks().insert(l)
        log(ctx, l.copy(id = id), l.amountMinor, "CREATE")
    }

    suspend fun add(ctx: Context, l: SavingsLock, amount: Long) {
        AppDb.get(ctx).locks().update(l.copy(amountMinor = l.amountMinor + amount))
        log(ctx, l, amount, "ADD")
    }

    suspend fun release(ctx: Context, l: SavingsLock, amount: Long) {
        val take = amount.coerceAtMost(l.amountMinor)
        AppDb.get(ctx).locks().update(l.copy(amountMinor = l.amountMinor - take))
        log(ctx, l, -take, "RELEASE")
    }

    suspend fun delete(ctx: Context, l: SavingsLock) {
        if (l.amountMinor != 0L) log(ctx, l, -l.amountMinor, "RELEASE")
        AppDb.get(ctx).locks().delete(l)
    }

    /** Name, goal, note and auto-lock rule. Never the amount or the account. */
    suspend fun saveSettings(ctx: Context, l: SavingsLock) = AppDb.get(ctx).locks().update(l)
}

/** Create a lock on [accountId], or edit an existing lock's settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockEditSheet(data: AppData, accountId: Long, existing: SavingsLock?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val guard = LocalGuard.current
    val account = data.account(accountId)
    val available = account?.let { Locks.available(it, data.txns, data.locks) } ?: 0L

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember { mutableStateOf("") }
    var fundId by remember { mutableStateOf(existing?.fundId) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var mode by remember { mutableStateOf(existing?.autoMode ?: "NONE") }
    var value by remember {
        mutableStateOf(
            when (existing?.autoMode) {
                "PERCENT" -> existing!!.autoValue.toString()
                "FIXED" -> Format.toInput(existing!!.autoValue)
                else -> ""
            }
        )
    }
    var minCredit by remember { mutableStateOf(existing?.autoMinCreditMinor?.takeIf { it > 0 }?.let { Format.toInput(it) } ?: "") }
    var transfers by remember { mutableStateOf(existing?.autoIncludeTransfers ?: false) }
    var pickGoal by remember { mutableStateOf(false) }

    val amountMinor = Format.parseMinor(amount)
    val ruleValue: Long? = when (mode) {
        "PERCENT" -> value.toLongOrNull()?.takeIf { it in 1..100 }
        "FIXED" -> Format.parseMinor(value)?.takeIf { it > 0 }
        else -> 0L
    }
    val valid = name.isNotBlank() && ruleValue != null &&
        (existing != null || (amountMinor != null && amountMinor >= 0))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (existing == null) "Lock money in ${account?.name ?: ""}" else "Lock settings", style = MaterialTheme.typography.titleLarge)
            if (existing == null) {
                Text("Available to lock now: ${Format.money(available)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(name, { name = it }, label = { Text("Name, e.g. Plot savings") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (existing == null) {
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Amount to lock now (PKR)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.headlineSmall,
                    supportingText = {
                        if (amountMinor != null && amountMinor > available) Text("More than what is free in this account. Available will show as negative.")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedButton(onClick = { pickGoal = true }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Counts towards goal", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(data.fund(fundId)?.name ?: "None", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }

            HorizontalDivider(color = LedgerTheme.colors.cardBorder)
            Text("Auto-lock incoming money", style = MaterialTheme.typography.titleMedium)
            Text("When money arrives in ${account?.name ?: "this account"}, lock part of it here automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(mode == "NONE", { mode = "NONE" }, { Text("Off") })
                FilterChip(mode == "PERCENT", { mode = "PERCENT"; value = "" }, { Text("Percent") })
                FilterChip(mode == "FIXED", { mode = "FIXED"; value = "" }, { Text("Fixed amount") })
            }
            if (mode != "NONE") {
                OutlinedTextField(
                    value, { value = it }, singleLine = true,
                    label = { Text(if (mode == "PERCENT") "Percent of each credit (1 to 100)" else "Amount from each credit (PKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    minCredit, { minCredit = it }, singleLine = true,
                    label = { Text("Only credits of at least (PKR, optional)") },
                    supportingText = { Text("Use your salary amount so small credits are not touched.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Include transfers from my own accounts", style = MaterialTheme.typography.bodyMedium)
                        Text("Turn on if your salary lands elsewhere and you move it here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(transfers, { transfers = it })
                }
            }
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())

            Button(
                enabled = valid,
                onClick = {
                    val base = existing ?: SavingsLock(accountId = accountId, name = "", amountMinor = amountMinor ?: 0L, createdAt = System.currentTimeMillis())
                    val l = base.copy(
                        name = name.trim(), fundId = fundId, note = note.trim().ifEmpty { null },
                        autoMode = mode, autoValue = ruleValue ?: 0L,
                        autoMinCreditMinor = if (mode == "NONE") 0L else Format.parseMinor(minCredit) ?: 0L,
                        autoIncludeTransfers = mode != "NONE" && transfers
                    )
                    // The first lock asks you to create a Ledger PIN, so it can be protected from the start.
                    guard.ensurePin {
                        scope.launch(Dispatchers.IO) {
                            if (existing == null) LockActions.create(ctx, l) else LockActions.saveSettings(ctx, l)
                        }
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (existing == null) "Lock" else "Save") }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickGoal) {
        val goals = data.funds.filter { it.kind == "GOAL" && !it.archived }
        PickerDialog("Counts towards goal", goals.map { it.name }, data.fund(fundId)?.name, { pickGoal = false }, noneLabel = "No goal") { n ->
            fundId = goals.firstOrNull { it.name == n }?.id
            pickGoal = false
        }
    }
}

/** One lock: amount, rule, history, and the add / release actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockDetailSheet(data: AppData, lockId: Long, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val guard = LocalGuard.current
    val l = data.locks.firstOrNull { it.id == lockId }
    var dialog by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }

    if (l == null) {
        LaunchedEffect(lockId) { onDismiss() }   // it was removed
        return
    }
    val account = data.account(l.accountId)
    val history = data.lockEvents.filter { it.lockId == l.id }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TintedIcon(R.drawable.ic_lock, MaterialTheme.colorScheme.primary, 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.name, style = MaterialTheme.typography.titleLarge)
                    Text("Locked in ${account?.name ?: "an account"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            BigMoney(l.amountMinor, MaterialTheme.colorScheme.onSurface)
            data.fund(l.fundId)?.let { Text("Counts towards ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Locks.ruleText(l) { Format.money(it) }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
            l.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }

            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { dialog = "add" }, modifier = Modifier.weight(1f)) { Text("Add") }
                OutlinedButton(onClick = { dialog = "release" }, modifier = Modifier.weight(1f), enabled = l.amountMinor > 0) { Text("Release") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editing = true }) { Text("Settings") }
                TextButton(onClick = {
                    guard.require("Release all of ${l.name} and remove the lock") {
                        scope.launch(Dispatchers.IO) { LockActions.delete(ctx, l) }
                        onDismiss()
                    }
                }) { Text("Release all and remove", color = MaterialTheme.colorScheme.error) }
            }

            HorizontalDivider(color = LedgerTheme.colors.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
            Text("History", style = MaterialTheme.typography.labelLarge)
            if (history.isEmpty()) Text("No changes yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            history.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (e.reason) {
                                "CREATE" -> "Lock created"
                                "AUTO" -> "Auto-locked from incoming money"
                                "ADD" -> "Added"
                                else -> "Released"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(Format.full(e.at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        (if (e.deltaMinor >= 0) "+" else "") + Format.number(e.deltaMinor), fontWeight = FontWeight.SemiBold,
                        color = if (e.deltaMinor >= 0) MaterialTheme.colorScheme.onSurface else LedgerTheme.colors.warning
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (dialog) {
        "add" -> AmountDialog("Add to ${l.name}", "Amount to lock", null, { dialog = null }) { amount ->
            dialog = null
            scope.launch(Dispatchers.IO) { LockActions.add(ctx, l, amount) }
        }
        "release" -> AmountDialog("Release from ${l.name}", "Amount to release", l.amountMinor, { dialog = null }) { amount ->
            dialog = null
            guard.require("Release ${Format.money(amount)} from ${l.name}") {
                scope.launch(Dispatchers.IO) { LockActions.release(ctx, l, amount) }
            }
        }
    }
    if (editing && account != null) LockEditSheet(data, account.id, l) { editing = false }
}

/** Asks for an amount. [max] caps it (shows a "Max" button). */
@Composable
private fun AmountDialog(title: String, label: String, max: Long?, onDismiss: () -> Unit, onOk: (Long) -> Unit) {
    var text by remember { mutableStateOf("") }
    val v = Format.parseMinor(text)
    val ok = v != null && v > 0 && (max == null || v <= max)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    text, { text = it }, label = { Text("$label (PKR)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { if (max != null) Text("Up to ${Format.money(max)}") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (max != null) TextButton(onClick = { text = Format.toInput(max) }) { Text("Max") }
            }
        },
        confirmButton = { TextButton(onClick = { onOk(v!!) }, enabled = ok) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
