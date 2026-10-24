package com.abeernch.ledger.ingest

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads notifications from bank and wallet apps.
 * SMS apps are skipped because [SmsReceiver] already sees those messages.
 * Chat and email apps are skipped so a friend's "I sent you Rs 500" is not logged.
 */
class BankNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val skipPackages = setOf(
        // SMS apps
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.android.messaging", "com.oneplus.mms", "com.miui.mms", "com.xiaomi.mms", "com.coloros.mms",
        "com.vivo.message", "com.huawei.message", "com.transsion.message", "com.infinix.message",
        "com.textra", "com.truecaller",
        // chat, social, email, browsers
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
        "com.facebook.orca", "com.facebook.katana", "com.instagram.android", "com.snapchat.android",
        "com.discord", "com.Slack", "com.linkedin.android", "com.twitter.android", "com.imo.android.imoim",
        "com.google.android.gm", "com.microsoft.office.outlook", "com.samsung.android.email.provider",
        "com.android.chrome", "com.sec.android.app.sbrowser", "com.anthropic.claude"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName || pkg in skipPackages || sbn.isOngoing) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val body = big ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        val text = if (title.isNotBlank() && !body.contains(title)) "$title. $body" else body

        val label = appLabel(pkg)
        val time = sbn.postTime
        scope.launch {
            Ingest.process(applicationContext, text, pkg, label, Ingest.SOURCE_NOTIFICATION, time)
        }
    }

    @Suppress("DEPRECATION")
    private fun appLabel(pkg: String): String = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        pkg
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
