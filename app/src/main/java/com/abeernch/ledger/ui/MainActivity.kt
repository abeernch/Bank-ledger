package com.abeernch.ledger.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.abeernch.ledger.Prefs
import com.abeernch.ledger.R
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.data.Txn
import com.abeernch.ledger.ingest.HistoryImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screens opened on top of the tabs. */
sealed interface Route {
    data object Settings : Route
    data object Review : Route
    data object Rules : Route
    data object People : Route
    data class FundDetail(val id: Long) : Route
}

private data class Tab(val label: String, val icon: Int)

private val tabs = listOf(
    Tab("Home", R.drawable.ic_home),
    Tab("Activity", R.drawable.ic_activity),
    Tab("Budgets", R.drawable.ic_budget),
    Tab("Accounts", R.drawable.ic_wallet),
    Tab("Insights", R.drawable.ic_insights)
)

class MainActivity : FragmentActivity() {

    private lateinit var prefs: Prefs

    // Re-checked whenever the app comes to the front, since access is granted in system Settings.
    private var smsGranted by mutableStateOf(false)
    private var listenerEnabled by mutableStateOf(false)
    private var notificationsAllowed by mutableStateOf(true)

    private var theme by mutableStateOf("SYSTEM")
    private var appLock by mutableStateOf(false)
    private var locked by mutableStateOf(false)
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        theme = prefs.theme
        appLock = prefs.appLock
        locked = appLock && lockAvailable()
        setContent {
            val dark = isDark(theme)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LedgerTheme(dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (locked) LockScreen { authenticate { locked = false } } else Root()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        smsGranted = hasPermission(Manifest.permission.RECEIVE_SMS) && hasPermission(Manifest.permission.READ_SMS)
        listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        notificationsAllowed = Build.VERSION.SDK_INT < 33 || hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Lock again after 30 seconds away.
        if (appLock && backgroundedAt > 0 && System.currentTimeMillis() - backgroundedAt > 30_000 && lockAvailable()) locked = true
    }

    private fun hasPermission(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun lockAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    /** Fingerprint, face, or the phone's PIN/pattern. */
    private fun authenticate(onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Ledger")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    @Composable
    private fun Root() {
        val ctx = this
        val db = remember { AppDb.get(ctx) }
        val txns by db.txns().all().collectAsState(initial = emptyList())
        val accounts by db.accounts().all().collectAsState(initial = emptyList())
        val funds by db.funds().all().collectAsState(initial = emptyList())
        val budgets by db.budgets().all().collectAsState(initial = emptyList())
        val debts by db.debts().all().collectAsState(initial = emptyList())
        val rules by db.rules().all().collectAsState(initial = emptyList())
        val unparsed by db.unparsed().all().collectAsState(initial = emptyList())
        val data = remember(txns, accounts, funds, budgets, debts, rules, unparsed) {
            AppData(txns, accounts, funds, budgets, debts, rules, unparsed)
        }

        var tab by rememberSaveable { mutableStateOf(0) }
        val stack = remember { mutableStateListOf<Route>() }
        var openTxnId by remember { mutableStateOf<Long?>(null) }
        var editing by remember { mutableStateOf<Txn?>(null) }
        var adding by remember { mutableStateOf(false) }
        var splitting by remember { mutableStateOf<Txn?>(null) }
        var importing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
        BackHandler(enabled = stack.isEmpty() && tab != 0) { tab = 0 }

        fun runImport() {
            if (importing) return
            importing = true
            scope.launch {
                val s = withContext(Dispatchers.IO) { HistoryImport.run(ctx) }
                prefs.historyImported = true
                importing = false
                Toast.makeText(ctx, "Scanned ${s.scanned} messages. Logged ${s.logged}, ${s.review} to review.", Toast.LENGTH_LONG).show()
            }
        }

        val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            smsGranted = hasPermission(Manifest.permission.RECEIVE_SMS) && hasPermission(Manifest.permission.READ_SMS)
            if (smsGranted && !prefs.historyImported) runImport()
        }
        val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = it }

        val setup = Setup(
            needsSms = !smsGranted,
            needsListener = !listenerEnabled,
            needsNotifications = !notificationsAllowed,
            onSms = { smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) },
            onListener = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            onNotifications = { if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        )
        val settings = AppSettings(
            theme = theme,
            appLock = appLock,
            lockAvailable = lockAvailable(),
            onTheme = { theme = it; prefs.theme = it },
            onAppLock = { on ->
                // Turning the lock on needs one successful unlock first, so nobody locks themselves out.
                if (on) authenticate { appLock = true; prefs.appLock = true } else { appLock = false; prefs.appLock = false }
            }
        )
        val openTxn: (Txn) -> Unit = { openTxnId = it.id }
        val go: (Route) -> Unit = { stack.add(it) }
        val back: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

        val top = stack.lastOrNull()
        if (top != null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                when (top) {
                    Route.Settings -> SettingsScreen(settings, setup, importing, { if (smsGranted) runImport() else setup.onSms() }, go, back, data.unparsed.size)
                    Route.Review -> ReviewScreen(data, back)
                    Route.Rules -> RulesScreen(data, back)
                    Route.People -> PeopleScreen(data, back)
                    is Route.FundDetail -> FundDetailScreen(top.id, data, back, openTxn)
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(painterResource(t.icon), null) },
                                label = { Text(t.label) }
                            )
                        }
                    }
                }
            ) { padding ->
                val m = Modifier.padding(padding)
                when (tab) {
                    0 -> HomeScreen(data, prefs.myName, setup, openTxn, go, { tab = it }, m)
                    1 -> ActivityScreen(data, openTxn, { adding = true }, m)
                    2 -> BudgetsScreen(data, go, m)
                    3 -> AccountsScreen(data, go, m)
                    else -> InsightsScreen(data, m)
                }
            }
        }

        // Sheets float over whatever screen is showing.
        openTxnId?.let { id ->
            val t = data.txns.firstOrNull { it.id == id }
            if (t == null) LaunchedEffect(id) { openTxnId = null }   // it was deleted
            else TxnDetailSheet(
                t, data,
                onDismiss = { openTxnId = null },
                onEdit = { openTxnId = null; editing = it },
                onSplit = { openTxnId = null; splitting = it }
            )
        }
        if (adding || editing != null) EntrySheet(data, editing) { adding = false; editing = null }
        splitting?.let { SplitSheet(it, data) { splitting = null } }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TintedIcon(R.drawable.ic_lock, MaterialTheme.colorScheme.primary, 72.dp)
        Spacer(Modifier.height(20.dp))
        Text("Ledger is locked", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlock) { Text("Unlock") }
    }
}
