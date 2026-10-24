package com.abeernch.ledger.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.data.Rule
import com.abeernch.ledger.export.CsvExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen(
    rules: List<Rule>,
    smsGranted: Boolean,
    listenerEnabled: Boolean,
    importing: Boolean,
    onAskSms: () -> Unit,
    onOpenListener: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { AppDb.get(context) }
    val scope = rememberCoroutineScope()
    var keyword by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) { CsvExport.write(context, uri) }
            Toast.makeText(context, "Exported $n transactions", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Heading("Access") }
        item { StatusRow("SMS (bank texts)", smsGranted, onAskSms) }
        item { StatusRow("Notifications (bank apps)", listenerEnabled, onOpenListener) }
        item {
            Text(
                "If Android says a setting is restricted: open App info for Ledger, tap ⋮, choose Allow restricted settings, then come back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item { Heading("Data") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImport, enabled = !importing) {
                    Text(if (importing) "Importing…" else "Import SMS history")
                }
                OutlinedButton(onClick = { exporter.launch("ledger-${LocalDate.now()}.csv") }) { Text("Export CSV") }
            }
        }
        item {
            Text(
                "Importing again is safe. Messages already logged are skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item { Heading("Category rules") }
        item {
            Text(
                "If a merchant or name contains the keyword, it gets that category. Your own rules come first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Column(Modifier.padding(vertical = 8.dp)) {
                OutlinedTextField(
                    keyword, { keyword = it }, singleLine = true,
                    label = { Text("Keyword, e.g. CHAYE KHANA") }, modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { picking = true }) { Text(category.ifBlank { "Pick category" }) }
                    Button(
                        enabled = keyword.isNotBlank() && category.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = {
                            val k = keyword.trim().uppercase()
                            val c = category
                            keyword = ""
                            scope.launch(Dispatchers.IO) {
                                db.rules().deleteUserRule(k)
                                db.rules().insert(Rule(keyword = k, category = c, isUser = true))
                            }
                        }
                    ) { Text("Add rule") }
                }
            }
        }
        items(rules, key = { it.id }) { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.keyword, fontWeight = if (r.isUser) FontWeight.SemiBold else FontWeight.Normal)
                    Text(
                        r.category + if (r.isUser) " · yours" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { scope.launch(Dispatchers.IO) { db.rules().delete(r) } }) {
                    Icon(Icons.Filled.Delete, "Delete rule")
                }
            }
            HorizontalDivider()
        }
        item {
            Text(
                "New rules apply to new transactions. To fix an old one, change its category from the Log.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }

    if (picking) {
        CategoryPicker(category, allCategories(rules), onDismiss = { picking = false }) {
            category = it
            picking = false
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun StatusRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        if (ok) Text("On", color = creditColor(), fontWeight = FontWeight.SemiBold)
        else OutlinedButton(onClick = onFix) { Text("Allow") }
    }
}
