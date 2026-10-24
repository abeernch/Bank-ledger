package com.abeernch.ledger.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.abeernch.ledger.R
import com.abeernch.ledger.ui.MainActivity

/** Posts the app's own notifications: budget warnings and bill reminders. */
object Notifier {

    private const val CH_BUDGET = "budget"
    private const val CH_BILLS = "bills"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_BUDGET, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_BILLS, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun budget(context: Context, id: Int, title: String, text: String) = post(context, CH_BUDGET, id, title, text)

    fun bill(context: Context, id: Int, title: String, text: String) = post(context, CH_BILLS, id, title, text)

    private fun post(context: Context, channel: String, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_ledger)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the call.
        }
    }
}
