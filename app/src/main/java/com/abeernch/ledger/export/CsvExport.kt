package com.abeernch.ledger.export

import android.content.Context
import android.net.Uri
import com.abeernch.ledger.data.AppDb
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Writes every transaction to a CSV file the user picked. Opens in Excel or Google Sheets. */
object CsvExport {

    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    suspend fun write(context: Context, uri: Uri): Int {
        val txns = AppDb.get(context).txns().allNow()
        val zone = ZoneId.systemDefault()
        val header = listOf(
            "Date", "Time", "Bank", "Type", "Channel", "Amount (PKR)", "Counterparty", "Location",
            "Category", "Account", "Balance (PKR)", "Fee (PKR)", "Reference", "Source", "Message"
        )
        val sb = StringBuilder()
        sb.append(header.joinToString(",")).append("\r\n")
        for (t in txns) {
            val at = Instant.ofEpochMilli(t.timestamp).atZone(zone)
            val row = listOf(
                dateFmt.format(at), timeFmt.format(at), t.bank,
                if (t.direction == "DEBIT") "Debit" else "Credit",
                t.channel, rupees(t.amountMinor), t.counterparty, t.location, t.category,
                t.accountTail, t.balanceMinor?.let { rupees(it) }, t.feeMinor?.let { rupees(it) },
                t.reference, t.source, t.rawText
            )
            sb.append(row.joinToString(",") { cell(it) }).append("\r\n")
        }
        context.contentResolver.openOutputStream(uri)?.use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        return txns.size
    }

    private fun rupees(minor: Long): String = "%d.%02d".format(minor / 100, kotlin.math.abs(minor % 100))

    /** Quote a cell when it holds a comma, quote or line break. */
    private fun cell(v: String?): String {
        val s = v ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
