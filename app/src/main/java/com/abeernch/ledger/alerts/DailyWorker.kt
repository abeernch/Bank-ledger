package com.abeernch.ledger.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.abeernch.ledger.Prefs
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.logic.Recurring
import com.abeernch.ledger.ui.Format
import java.time.LocalDate
import java.time.ZoneId

/** Runs once a day. Reminds about recurring payments due tomorrow. */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.billReminders) return Result.success()

        val txns = AppDb.get(applicationContext).txns().allNow()
        val tomorrow = LocalDate.now().plusDays(1)
        val due = Recurring.detect(txns, LocalDate.now(), ZoneId.systemDefault()).filter { it.nextDate == tomorrow }

        for (item in due) {
            val key = "bill|${item.name}|${item.nextDate}"
            if (prefs.alertShown(key)) continue
            prefs.markAlertShown(key)
            Notifier.bill(
                applicationContext,
                key.hashCode(),
                "${item.name} due tomorrow",
                "Usually about ${Format.money(item.typicalMinor)} around the ${item.dayOfMonth}${Format.ordinal(item.dayOfMonth)}."
            )
        }
        return Result.success()
    }
}
