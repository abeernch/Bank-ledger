package com.abeernch.ledger.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.abeernch.ledger.R
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.logic.BudgetMath
import com.abeernch.ledger.logic.Money
import com.abeernch.ledger.ui.Format
import com.abeernch.ledger.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * Home-screen widget.
 * With budgets set: shows what is left to spend this month.
 * Without: shows what was spent.
 */
class SpendWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                draw(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {

        /** Call after transactions change. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SpendWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { draw(context, manager, ids) }
        }

        private suspend fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val db = AppDb.get(context)
            val month = YearMonth.now()
            val txns = db.txns().between(Format.monthStart(month), Format.monthEnd(month))
            val prev = month.minusMonths(1)
            val prevTxns = db.txns().between(Format.monthStart(prev), Format.monthEnd(prev))
            val budgets = db.budgets().allNow()
            val spent = Money.spent(txns)
            val earned = Money.earned(txns)

            val label: String
            val main: String
            if (budgets.isNotEmpty()) {
                val daysLeft = month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1
                val s = BudgetMath.summarize(budgets, txns, prevTxns, daysLeft)
                label = if (s.leftMinor >= 0) "Left to spend" else "Over budget"
                main = Format.money(kotlin.math.abs(s.leftMinor))
            } else {
                label = "Spent"
                main = Format.money(spent)
            }

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_spend)
                views.setTextViewText(R.id.widget_label, "${Format.monthShort(month)} · $label")
                views.setTextViewText(R.id.widget_main, main)
                views.setTextViewText(R.id.widget_sub, "Out ${Format.moneyShort(spent)}   ·   In ${Format.moneyShort(earned)}")
                views.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
