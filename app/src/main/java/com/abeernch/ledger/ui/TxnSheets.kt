package com.abeernch.ledger.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.R
import com.abeernch.ledger.category.Categorizer
import com.abeernch.ledger.data.Txn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

/** Full details of one transaction, with every action. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TxnDetailSheet(
    t: Txn,
    data: AppData,
    onDismiss: () -> Unit,
    onEdit: (Txn) -> Unit,
    onSplit: (Txn) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }   // which dialog is open
    val children = data.children(t)
    val parent = t.splitParentId?.let { id -> data.txns.firstOrNull { it.id == id } }

    fun io(block: suspend () -> Unit) = scope.launch(Dispatchers.IO) { block() }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) io { Actions.attachReceipt(ctx, t, uri) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(if (t.isSplit) "Other" else t.category, 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(Format.title(t.counterparty ?: Format.channelName(t.channel)), style = MaterialTheme.typography.titleLarge)
                    Text(Format.full(t.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Amount(t, big = true)
            if (t.isTransfer) Text("Move between your own accounts. Not counted as spending or income.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (t.isSplit) Text("Split into ${children.size} parts. The parts are counted, not this total.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (parent != null) Text("Part of a split from ${Format.title(parent.counterparty ?: "a payment")}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Actions
            FlowRow(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!t.isSplit) ActionChip(R.drawable.ic_shopping, "Category") { dialog = "category" }
                ActionChip(R.drawable.ic_fund, if (t.fundId != null) "Change fund" else "Assign to fund") { dialog = "fund" }
                if (parent == null) ActionChip(R.drawable.ic_split, if (t.isSplit) "Edit split" else "Split") { onSplit(t) }
                ActionChip(R.drawable.ic_note, if (t.note != null) "Edit note" else "Note") { dialog = "note" }
                ActionChip(R.drawable.ic_receipt, if (t.receiptPath != null) "Replace receipt" else "Receipt") {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (parent == null) ActionChip(R.drawable.ic_transfer, if (t.isTransfer) "Not own transfer" else "Own transfer") {
                    io { Actions.setTransfer(ctx, t, !t.isTransfer) }
                }
                if (parent == null && data.debts.none { it.txnId == t.id }) {
                    ActionChip(R.drawable.ic_person, if (t.direction == "DEBIT") "Lent to someone" else "Borrowed") { dialog = "loan" }
                }
                ActionChip(R.drawable.ic_edit, "Edit") { onEdit(t) }
            }

            HorizontalDivider(color = LedgerTheme.colors.cardBorder)
            InfoRow("Account", data.accountName(t) + (t.accountTail?.let { "  ••$it" } ?: ""))
            if (!t.isSplit) InfoRow("Category", t.category, onClick = { dialog = "category" })
            InfoRow("Fund", data.fund(t.fundId)?.name ?: "None", onClick = { dialog = "fund" })
            InfoRow("Type", Format.channelName(t.channel) + if (t.direction == "CREDIT") " · money in" else " · money out")
            t.location?.let { InfoRow("Location", Format.title(it)) }
            t.balanceMinor?.let { InfoRow("Balance after", Format.money(it)) }
            t.feeMinor?.let { InfoRow("Fee", Format.money(it)) }
            t.reference?.let { InfoRow("Reference", it) }
            data.debts.firstOrNull { it.txnId == t.id }?.let { d ->
                InfoRow(if (d.direction == "LENT") "Lent to" else "Borrowed from", d.person + if (d.settledAt != null) " · settled" else "")
            }
            InfoRow("Source", when (t.source) {
                "MANUAL" -> "Added by hand"
                "IMPORT" -> "SMS history"
                "NOTIFICATION" -> "Bank app"
                else -> "SMS"
            } + if (t.isGeneric) " · general rules" else "")

            if (t.note != null) {
                Text("Note", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                Text(t.note, style = MaterialTheme.typography.bodyMedium)
            }

            if (children.isNotEmpty()) {
                Text("Parts", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                children.forEachIndexed { i, c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryIcon(c.category, 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(c.category + (c.note?.let { " · $it" } ?: ""), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(Format.number(c.amountMinor), fontWeight = FontWeight.SemiBold)
                    }
                    if (i < children.lastIndex) HorizontalDivider(color = LedgerTheme.colors.cardBorder)
                }
            }

            t.receiptPath?.let { path ->
                Text("Receipt", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                ReceiptImage(path)
                TextButton(onClick = { io { Actions.removeReceipt(ctx, t) } }) { Text("Remove receipt") }
            }

            if (t.rawText.isNotBlank()) {
                Text("Original message", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                SelectionContainer {
                    Text(t.rawText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { dialog = "delete" }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (dialog) {
        "category" -> PickerDialog("Category", data.categories, t.category, { dialog = null }, allowNew = "New category") { c ->
            dialog = null
            if (c != null) io { Actions.recategorize(ctx, t, c) }
        }
        "fund" -> PickerDialog("Assign to fund or goal", data.funds.filter { !it.archived }.map { it.name }, data.fund(t.fundId)?.name, { dialog = null }, noneLabel = "No fund") { name ->
            dialog = null
            io { Actions.assignFund(ctx, t, data.funds.firstOrNull { it.name == name }?.id) }
        }
        "note" -> TextDialog("Note", t.note ?: "", "Save", { dialog = null }) { text ->
            dialog = null
            io { Actions.setNote(ctx, t, text) }
        }
        "loan" -> TextDialog(
            if (t.direction == "DEBIT") "Who did you lend to?" else "Who did you borrow from?",
            Format.title(t.counterparty ?: ""), "Save", { dialog = null }
        ) { person ->
            dialog = null
            if (person.isNotBlank()) io { Actions.markAsLoan(ctx, t, person) }
        }
        "delete" -> ConfirmDialog(
            "Delete this transaction?",
            "It will only come back if the same message is imported again.",
            "Delete", { dialog = null }
        ) {
            io { Actions.delete(ctx, t) }
            onDismiss()
        }
    }
}

@Composable
private fun ActionChip(icon: Int, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = false, onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(painterResource(icon), null, Modifier.size(16.dp)) }
    )
}

@Composable
private fun ReceiptImage(path: String) {
    var bmp by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bmp = withContext(Dispatchers.IO) {
            // Decode at a reduced size so large photos do not use much memory.
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        }
    }
    bmp?.let {
        Image(
            it, "Receipt photo", contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(16.dp))
        )
    }
}

@Composable
fun TextDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Add a transaction by hand, or edit an existing one ([editing] not null).
 * [presetAccountId] picks the account when adding (e.g. from an account card).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(data: AppData, editing: Txn?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val firstAccount = data.accounts.firstOrNull { it.kind == "CASH" && !it.archived } ?: data.accounts.firstOrNull { !it.archived }

    var amount by remember { mutableStateOf(editing?.let { Format.toInput(it.amountMinor) } ?: "") }
    var credit by remember { mutableStateOf(editing?.direction == "CREDIT") }
    var accountId by remember { mutableStateOf(editing?.accountId ?: firstAccount?.id) }
    var category by remember { mutableStateOf(editing?.category ?: Categorizer.OTHER) }
    var payee by remember { mutableStateOf(editing?.counterparty ?: "") }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var fundId by remember { mutableStateOf(editing?.fundId) }
    var timestamp by remember { mutableStateOf(editing?.timestamp ?: System.currentTimeMillis()) }
    var dialog by remember { mutableStateOf<String?>(null) }

    val parsed = Format.parseMinor(amount)
    val valid = parsed != null && parsed > 0 && accountId != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (editing == null) "Add transaction" else "Edit transaction", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!credit, { credit = false }, { Text("Money out") })
                FilterChip(credit, { credit = true }, { Text("Money in") })
            }
            OutlinedTextField(
                amount, { amount = it }, label = { Text("Amount (PKR)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(payee, { payee = it }, label = { Text(if (credit) "From" else "Paid to") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (data.accounts.isEmpty()) {
                Text("Add an account first on the Accounts tab.", color = LedgerTheme.colors.warning, style = MaterialTheme.typography.bodySmall)
            }
            FieldButton("Account", data.account(accountId)?.name ?: "Choose") { dialog = "account" }
            FieldButton("Category", category) { dialog = "category" }
            FieldButton("Fund", data.fund(fundId)?.name ?: "None") { dialog = "fund" }
            FieldButton("Date", Format.date(timestamp)) { dialog = "date" }
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = valid,
                onClick = {
                    val e = Actions.Entry(parsed!!, credit, accountId!!, category, payee, timestamp, note, fundId)
                    scope.launch(Dispatchers.IO) {
                        if (editing == null) Actions.addManual(ctx, e) else Actions.update(ctx, editing, e)
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (editing == null) "Add" else "Save") }
            Spacer(Modifier.height(16.dp))
        }
    }

    when (dialog) {
        "account" -> PickerDialog("Account", data.accounts.filter { !it.archived }.map { it.name }, data.account(accountId)?.name, { dialog = null }) { name ->
            accountId = data.accounts.firstOrNull { it.name == name }?.id ?: accountId; dialog = null
        }
        "category" -> PickerDialog("Category", data.categories, category, { dialog = null }, allowNew = "New category") { c ->
            if (c != null) category = c; dialog = null
        }
        "fund" -> PickerDialog("Fund", data.funds.filter { !it.archived }.map { it.name }, data.fund(fundId)?.name, { dialog = null }, noneLabel = "No fund") { name ->
            fundId = data.funds.firstOrNull { it.name == name }?.id; dialog = null
        }
        "date" -> {
            // The date picker works in UTC days; keep the original time of day.
            val state = rememberDatePickerState(
                initialSelectedDateMillis = Format.localDate(timestamp).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { dialog = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { utc ->
                            val day = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                            val time = Instant.ofEpochMilli(timestamp).atZone(Format.zone).toLocalTime() ?: LocalTime.NOON
                            timestamp = day.atTime(time).atZone(Format.zone).toInstant().toEpochMilli()
                        }
                        dialog = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
            ) { DatePicker(state) }
        }
    }
}

@Composable
private fun FieldButton(label: String, value: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Split one payment into parts with their own categories. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitSheet(t: Txn, data: AppData, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    class PartState(amount: String, category: String) {
        var amount by mutableStateOf(amount)
        var category by mutableStateOf(category)
    }

    val existing = data.children(t)
    val parts = remember {
        mutableStateListOf<PartState>().apply {
            if (existing.isNotEmpty()) existing.forEach { add(PartState(Format.toInput(it.amountMinor), it.category)) }
            else {
                add(PartState(Format.toInput(t.amountMinor), t.category))
                add(PartState("", Categorizer.OTHER))
            }
        }
    }
    var picking by remember { mutableStateOf<Int?>(null) }

    val sum = parts.sumOf { Format.parseMinor(it.amount) ?: 0L }
    val remaining = t.amountMinor - sum
    val valid = remaining == 0L && parts.all { (Format.parseMinor(it.amount) ?: 0L) > 0 } && parts.size >= 2

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Split ${Format.money(t.amountMinor)}", style = MaterialTheme.typography.titleLarge)
            Text("Give each part an amount and a category. Parts must add up to the total.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            parts.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        p.amount, { p.amount = it }, singleLine = true, label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { picking = i }, modifier = Modifier.weight(1.3f).height(56.dp), shape = RoundedCornerShape(12.dp)) {
                        Text(p.category, maxLines = 1)
                    }
                    if (parts.size > 2) {
                        IconButton(onClick = { parts.removeAt(i) }) { Icon(painterResource(R.drawable.ic_close), "Remove part") }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { parts.add(PartState(if (remaining > 0) Format.toInput(remaining) else "", Categorizer.OTHER)) }) { Text("+ Add part") }
                Spacer(Modifier.weight(1f))
                Text(
                    if (remaining == 0L) "Adds up" else if (remaining > 0) "${Format.number(remaining)} left" else "${Format.number(-remaining)} too much",
                    color = if (remaining == 0L) LedgerTheme.colors.credit else LedgerTheme.colors.warning,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Button(
                enabled = valid,
                onClick = {
                    val list = parts.map { Actions.Part(Format.parseMinor(it.amount)!!, it.category, null) }
                    scope.launch(Dispatchers.IO) { Actions.split(ctx, t, list) }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Save split") }
            if (existing.isNotEmpty()) {
                OutlinedButton(onClick = { scope.launch(Dispatchers.IO) { Actions.split(ctx, t, emptyList()) }; onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Undo split")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    picking?.let { i ->
        PickerDialog("Category", data.categories, parts[i].category, { picking = null }, allowNew = "New category") { c ->
            if (c != null) parts[i].category = c
            picking = null
        }
    }
}
