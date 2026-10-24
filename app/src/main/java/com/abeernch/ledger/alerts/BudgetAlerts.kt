package com.abeernch.ledger.alerts

import android.content.Context
import com.abeernch.ledger.Prefs
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.logic.BudgetMath
import com.abeernch.ledger.ui.Format
import java.time.YearMonth

/** Warns once at 80% and once at 100% of a category's monthly budget. */
object BudgetAlerts {

    suspend fun check(context: Context, category: String) {
        val prefs = Prefs(context)
        if (!prefs.budgetAlerts) return
        val db = AppDb.get(context)
        val budget = db.budgets().allNow().firstOrNull { it.category == category } ?: return

        val month = YearMonth.now()
        val thisMonth = db.txns().between(Format.monthStart(month), Format.monthEnd(month))
        val prev = month.minusMonths(1)
        val lastMonth = db.txns().between(Format.monthStart(prev), Format.monthEnd(prev))
        val line = BudgetMath.summarize(listOf(budget), thisMonth, lastMonth, 0).lines.first()

        // Show only the highest threshold crossed, and each one only once a month.
        val level = BudgetMath.thresholdsCrossed(line).maxOrNull() ?: return
        val key = "$category|$month|$level"
        if (prefs.alertShown(key)) return
        prefs.markAlertShown(key)
        if (level == 80) prefs.markAlertShown("$category|$month|80")

        val title = if (level == 100) "$category budget used up" else "$category budget at 80%"
        val text = "Spent ${Format.money(line.spentMinor)} of ${Format.money(line.availableMinor)} this month. " +
            if (line.leftMinor >= 0) "${Format.money(line.leftMinor)} left." else "${Format.money(-line.leftMinor)} over."
        Notifier.budget(context, ("budget" + category).hashCode(), title, text)
    }
}
