package com.abeernch.ledger.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.abeernch.ledger.LedgerApp
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.ingest.HistoryImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

class MainActivity : ComponentActivity() {

    // Re-checked every time the app comes to the front, since access is granted in system Settings.
    private var smsGranted by mutableStateOf(false)
    private var listenerEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LedgerTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        smsGranted = hasSms(this)
        listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    private fun hasSms(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    @Composable
    private fun Root() {
        val db = remember { AppDb.get(this) }
        val txns by db.txns().all().collectAsState(initial = emptyList())
        val unparsed by db.unparsed().all().collectAsState(initial = emptyList())
        val rules by db.rules().all().collectAsState(initial = emptyList())

        var tab by rememberSaveable { mutableStateOf(0) }
        var month by remember { mutableStateOf(YearMonth.now()) }
        var importing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun runImport() {
            if (importing) return
            importing = true
            scope.launch {
                val s = withContext(Dispatchers.IO) { HistoryImport.run(this@MainActivity) }
                getSharedPreferences(LedgerApp.PREFS, MODE_PRIVATE).edit()
                    .putBoolean(LedgerApp.KEY_IMPORTED, true).apply()
                importing = false
                Toast.makeText(
                    this@MainActivity,
                    "Scanned ${s.scanned} messages. Logged ${s.logged}, ${s.review} to review.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            smsGranted = hasSms(this)
            val done = getSharedPreferences(LedgerApp.PREFS, MODE_PRIVATE).getBoolean(LedgerApp.KEY_IMPORTED, false)
            if (smsGranted && !done) runImport()
        }
        val askSms = { smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) }
        val openListenerSettings = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("Log") })
                    NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.DateRange, null) }, label = { Text("Summary") })
                    NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.AccountBox, null) }, label = { Text("Banks") })
                    NavigationBarItem(
                        tab == 3, { tab = 3 },
                        {
                            BadgedBox(badge = { if (unparsed.isNotEmpty()) Badge { Text("${unparsed.size}") } }) {
                                Icon(Icons.Filled.Warning, null)
                            }
                        },
                        label = { Text("Review") }
                    )
                    NavigationBarItem(tab == 4, { tab = 4 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") })
                }
            }
        ) { padding ->
            val m = Modifier.padding(padding)
            when (tab) {
                0 -> LogScreen(
                    txns = txns, rules = rules, month = month, onMonth = { month = it },
                    needsSms = !smsGranted, needsListener = !listenerEnabled,
                    onAskSms = askSms, onOpenListener = openListenerSettings, modifier = m
                )
                1 -> SummaryScreen(txns = txns, month = month, onMonth = { month = it }, modifier = m)
                2 -> BanksScreen(txns = txns, modifier = m)
                3 -> ReviewScreen(items = unparsed, modifier = m)
                else -> SettingsScreen(
                    rules = rules, smsGranted = smsGranted, listenerEnabled = listenerEnabled,
                    importing = importing, onAskSms = askSms, onOpenListener = openListenerSettings,
                    onImport = { if (smsGranted) runImport() else askSms() }, modifier = m
                )
            }
        }
    }
}
