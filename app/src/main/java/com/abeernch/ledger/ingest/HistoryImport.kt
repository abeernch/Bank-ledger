package com.abeernch.ledger.ingest

import android.content.Context
import android.provider.Telephony
import com.abeernch.ledger.widget.SpendWidget

/** Reads the SMS inbox once and logs every past bank message. Safe to run again: duplicates are skipped. */
object HistoryImport {

    data class Summary(val scanned: Int, val logged: Int, val review: Int)

    suspend fun run(context: Context): Summary {
        var scanned = 0
        var logged = 0
        var review = 0
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null, null,
            Telephony.Sms.DATE + " ASC"
        ) ?: return Summary(0, 0, 0)

        cursor.use { c ->
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val sender = c.getString(iAddr) ?: continue
                val body = c.getString(iBody) ?: continue
                scanned++
                when (Ingest.process(context, body, sender, sender, Ingest.SOURCE_IMPORT, c.getLong(iDate), refreshWidget = false)) {
                    Ingest.Result.LOGGED -> logged++
                    Ingest.Result.REVIEW -> review++
                    else -> {}
                }
            }
        }
        SpendWidget.refresh(context)
        return Summary(scanned, logged, review)
    }
}
