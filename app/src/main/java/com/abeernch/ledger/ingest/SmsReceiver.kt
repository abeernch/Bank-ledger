package com.abeernch.ledger.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives every incoming SMS and hands it to [Ingest]. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        // A long SMS arrives in several parts. Join them per sender.
        val bySender = parts.groupBy { it.originatingAddress ?: "unknown" }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for ((sender, msgs) in bySender) {
                    val body = msgs.joinToString("") { it.messageBody ?: "" }
                    val time = msgs.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
                    Ingest.process(context, body, sender, sender, Ingest.SOURCE_SMS, time)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
