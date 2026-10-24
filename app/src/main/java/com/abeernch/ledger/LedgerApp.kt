package com.abeernch.ledger

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.abeernch.ledger.alerts.Notifier
import com.abeernch.ledger.alerts.DailyWorker
import com.abeernch.ledger.category.Categorizer
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.data.Rule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class LedgerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        CoroutineScope(Dispatchers.IO).launch { ensureSeeded(this@LedgerApp) }

        // Once a day: bill reminders. KEEP means reinstalling does not reset the schedule.
        val daily = PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.KEEP, daily)
    }

    companion object {
        private val seedLock = Mutex()

        /** Puts the default category rules in the database once. */
        suspend fun ensureSeeded(context: Context) = seedLock.withLock {
            val prefs = Prefs(context)
            if (prefs.rulesSeeded) return@withLock
            val rules = Categorizer.defaultRules.map { (k, c) -> Rule(keyword = k, category = c, isUser = false) }
            AppDb.get(context).rules().insertAll(rules)
            prefs.rulesSeeded = true
        }
    }
}
