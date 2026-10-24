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
import com.abeernch.ledger.ui.Format
import com.abeernch.ledger.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Home-screen widget: money out and money in for the current month. */
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

        /** Call after a new transaction is logged. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SpendWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { draw(context, manager, ids) }
        }

        private suspend fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val month = YearMonth.now()
            val txns = AppDb.get(context).txns().between(Format.monthStart(month), Format.monthEnd(month))
            val out = txns.filter { it.direction == "DEBIT" }.sumOf { it.amountMinor }
            val inn = txns.filter { it.direction == "CREDIT" }.sumOf { it.amountMinor }

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_spend)
                views.setTextViewText(R.id.widget_month, Format.month(month))
                views.setTextViewText(R.id.widget_out, Format.money(out))
                views.setTextViewText(R.id.widget_in, Format.money(inn))
                views.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
