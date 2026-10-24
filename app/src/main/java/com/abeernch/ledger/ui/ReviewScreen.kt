package com.abeernch.ledger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.data.AppDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Messages that mention money but no template could read. Copy them so a template can be added. */
@Composable
fun ReviewScreen(data: AppData, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDb.get(context) }
    val scope = rememberCoroutineScope()
    val items = data.unparsed

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Messages to review", onBack)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
            item {
                Column(Modifier.padding(bottom = 12.dp)) {
                    Text(
                        "These mention money but could not be read. Copy them and send them over so a new bank format can be added.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (items.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = { copy(context, items.joinToString("\n\n---\n\n") { "[${it.sender}] ${it.text}" }) }) { Text("Copy all") }
                            OutlinedButton(onClick = { scope.launch(Dispatchers.IO) { db.unparsed().deleteAll() } }) { Text("Dismiss all") }
                        }
                    }
                }
            }
            if (items.isEmpty()) item { LedgerCard { EmptyState("Nothing to review", "Every money message so far was read.") } }
            items(items, key = { it.id }) { u ->
                LedgerCard(Modifier.padding(bottom = 10.dp)) {
                    Text("${u.sender} · ${Format.full(u.receivedAt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer { Text(u.text, Modifier.padding(vertical = 8.dp)) }
                    Row {
                        TextButton(onClick = { copy(context, "[${u.sender}] ${u.text}") }) { Text("Copy") }
                        TextButton(onClick = { scope.launch(Dispatchers.IO) { db.unparsed().delete(u) } }) { Text("Dismiss") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Bank messages", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
