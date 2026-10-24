package com.abeernch.ledger

import android.app.Application
import android.content.Context
import com.abeernch.ledger.category.Categorizer
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.data.Rule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LedgerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch { ensureSeeded(this@LedgerApp) }
    }

    companion object {
        const val PREFS = "ledger"
        const val KEY_SEEDED = "rules_seeded"
        const val KEY_IMPORTED = "history_imported"

        private val seedLock = Mutex()

        /** Puts the default category rules in the database once. */
        suspend fun ensureSeeded(context: Context) = seedLock.withLock {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_SEEDED, false)) return@withLock
            val rules = Categorizer.defaultRules.map { (k, c) -> Rule(keyword = k, category = c, isUser = false) }
            AppDb.get(context).rules().insertAll(rules)
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
        }
    }
}
