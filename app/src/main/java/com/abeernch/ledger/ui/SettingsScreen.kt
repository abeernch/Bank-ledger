package com.abeernch.ledger.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.BuildConfig
import com.abeernch.ledger.Prefs
import com.abeernch.ledger.export.Backup
import com.abeernch.ledger.export.CsvExport
import com.abeernch.ledger.export.PdfStatement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/** Settings that MainActivity owns because they change the whole app. */
data class AppSettings(
    val theme: String,
    val appLock: Boolean,
    val lockAvailable: Boolean,
    val onTheme: (String) -> Unit,
    val onAppLock: (Boolean) -> Unit
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    setup: Setup,
    importing: Boolean,
    onImport: () -> Unit,
    onGo: (Route) -> Unit,
    onBack: () -> Unit,
    unparsedCount: Int
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(ctx) }
    var myName by remember { mutableStateOf(prefs.myName) }
    var budgetAlerts by remember { mutableStateOf(prefs.budgetAlerts) }
    var billReminders by remember { mutableStateOf(prefs.billReminders) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var pdfMonth by remember { mutableStateOf(YearMonth.now()) }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) { CsvExport.write(ctx, uri) }
            toast("Exported $n transactions")
        }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { PdfStatement.write(ctx, uri, pdfMonth) }
            toast("Statement saved")
        }
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { Backup.save(ctx, uri) }
            toast("Backup saved. Keep it somewhere safe, like Google Drive.")
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { Backup.restore(ctx, uri) }
            if (!ok) toast("That file is not a Ledger backup.")
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings", onBack)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
            item { SectionHeader("Access") }
            item {
                LedgerCard(padding = 4.dp) {
                    StatusRow("Bank texts (SMS)", !setup.needsSms, setup.onSms)
                    Divider()
                    StatusRow("Bank app notifications", !setup.needsListener, setup.onListener)
                    Divider()
                    StatusRow("Alerts and reminders", !setup.needsNotifications, setup.onNotifications)
                }
            }
            item {
                Text(
                    "If Android says a setting is restricted: App info for Ledger → ⋮ → Allow restricted settings.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item { SectionHeader("Appearance") }
            item {
                LedgerCard {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        FilterChip(settings.theme == "SYSTEM", { settings.onTheme("SYSTEM") }, { Text("Phone setting") })
                        FilterChip(settings.theme == "LIGHT", { settings.onTheme("LIGHT") }, { Text("Light") })
                        FilterChip(settings.theme == "DARK", { settings.onTheme("DARK") }, { Text("Dark") })
                    }
                }
            }

            item { SectionHeader("Security & alerts") }
            item {
                LedgerCard(padding = 4.dp) {
                    ToggleRow(
                        "App lock",
                        if (settings.lockAvailable) "Fingerprint, face or screen lock to open Ledger" else "Set a screen lock on the phone first",
                        settings.appLock, settings.lockAvailable
                    ) { settings.onAppLock(it) }
                    Divider()
                    ToggleRow("Budget alerts", "Notify at 80% and 100% of a budget", budgetAlerts) {
                        budgetAlerts = it; prefs.budgetAlerts = it
                    }
                    Divider()
                    ToggleRow("Bill reminders", "Notify the day before a recurring payment", billReminders) {
                        billReminders = it; prefs.billReminders = it
                    }
                }
            }

            item { SectionHeader("Own transfers") }
            item {
                LedgerCard(onClick = { dialog = "name" }) {
                    Text("Your name as banks write it", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        myName.ifBlank { "Not set" },
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Payments to this name count as moves between your own accounts, not spending.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { SectionHeader("Categories & messages") }
            item {
                LedgerCard(padding = 4.dp) {
                    LinkRow("Category rules", "Which payee goes to which category") { onGo(Route.Rules) }
                    Divider()
                    LinkRow("Messages to review", if (unparsedCount > 0) "$unparsedCount waiting" else "None waiting") { onGo(Route.Review) }
                }
            }

            item { SectionHeader("Data") }
            item {
                LedgerCard(padding = 4.dp) {
                    LinkRow(if (importing) "Importing…" else "Import SMS history", "Safe to repeat. Already logged messages are skipped.") { if (!importing) onImport() }
                    Divider()
                    LinkRow("Monthly statement (PDF)", "Totals, categories and every transaction") { dialog = "pdf" }
                    Divider()
                    LinkRow("Export all (CSV)", "Opens in Excel or Google Sheets") { csv.launch("ledger-${LocalDate.now()}.csv") }
                    Divider()
                    LinkRow("Back up", "Save all data to a file") { backup.launch("ledger-backup-${LocalDate.now()}.db") }
                    Divider()
                    LinkRow("Restore", "Replace all data with a backup file") { dialog = "restore" }
                }
            }
            item {
                Text(
                    "Ledger has no internet access. Your data never leaves this phone unless you export it.\nVersion ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            }
        }
    }

    when (dialog) {
        "name" -> TextDialog("Your name", myName, "Save", { dialog = null }) {
            myName = it.trim(); prefs.myName = it; dialog = null
        }
        "pdf" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Monthly statement") },
            text = { Row(Modifier.fillMaxWidth()) { MonthSwitcher(pdfMonth) { pdfMonth = it } } },
            confirmButton = {
                TextButton(onClick = { dialog = null; pdf.launch("ledger-statement-$pdfMonth.pdf") }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
        "restore" -> ConfirmDialog(
            "Replace all data?",
            "Everything in Ledger now will be replaced by the backup. The app restarts afterwards.",
            "Choose backup", { dialog = null }
        ) { restore.launch(arrayOf("*/*")) }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = LedgerTheme.colors.cardBorder, modifier = Modifier.padding(horizontal = 12.dp))

@Composable
private fun StatusRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        if (ok) Text("On", color = LedgerTheme.colors.credit, fontWeight = FontWeight.SemiBold)
        else OutlinedButton(onClick = onFix) { Text("Allow") }
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChange, enabled = enabled)
    }
}

@Composable
private fun LinkRow(title: String, body: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
