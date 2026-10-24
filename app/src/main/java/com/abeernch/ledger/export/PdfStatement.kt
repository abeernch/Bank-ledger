package com.abeernch.ledger.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.res.ResourcesCompat
import com.abeernch.ledger.R
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.logic.BudgetMath
import com.abeernch.ledger.logic.Money
import com.abeernch.ledger.ui.Format
import java.time.YearMonth

/** A printable A4 monthly statement: totals, spending by category, every transaction. */
object PdfStatement {

    private const val W = 595   // A4 in points
    private const val H = 842
    private const val M = 40    // margin

    suspend fun write(context: Context, uri: Uri, month: YearMonth) {
        val db = AppDb.get(context)
        val all = db.txns().allNow()
        val accounts = db.accounts().allNow().associateBy { it.id }
        val txns = Money.inRange(all, Format.monthStart(month), Format.monthEnd(month)).sortedBy { it.timestamp }
        val prev = month.minusMonths(1)
        val prevTxns = Money.inRange(all, Format.monthStart(prev), Format.monthEnd(prev))
        val budgets = BudgetMath.summarize(db.budgets().allNow(), txns, prevTxns, 0)

        val regular = ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT
        val bold = ResourcesCompat.getFont(context, R.font.inter_bold) ?: Typeface.DEFAULT_BOLD
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular; textSize = 9f; color = 0xFF111827.toInt() }
        val muted = Paint(text).apply { color = 0xFF4B5563.toInt() }
        val head = Paint(text).apply { typeface = bold; textSize = 20f }
        val sub = Paint(text).apply { typeface = bold; textSize = 12f }
        val accent = Paint(text).apply { color = 0xFF0F766E.toInt(); typeface = bold }
        val line = Paint().apply { color = 0xFFE5E7EB.toInt(); strokeWidth = 0.7f }
        val right = Paint(text).apply { textAlign = Paint.Align.RIGHT }
        val rightBold = Paint(right).apply { typeface = bold }

        val doc = PdfDocument()
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        fun newPage() {
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas
            y = M.toFloat()
            c.drawText("Ledger · ${Format.month(month)}", M.toFloat(), H - 20f, muted)
            c.drawText("Page $pageNo", (W - M).toFloat(), H - 20f, Paint(muted).apply { textAlign = Paint.Align.RIGHT })
        }

        fun ensure(space: Float) {
            if (y + space > H - 50) {
                doc.finishPage(page)
                newPage()
            }
        }

        newPage()
        c.drawText("Monthly statement", M.toFloat(), y + 18, head); y += 30
        c.drawText(Format.month(month), M.toFloat(), y + 10, accent); y += 28

        // Totals
        val spent = Money.spent(txns)
        val earned = Money.earned(txns)
        val cols = listOf("Money out" to spent, "Money in" to earned, "Net" to (earned - spent))
        cols.forEachIndexed { i, (label, v) ->
            val x = M + i * 170f
            c.drawText(label, x, y + 10, muted)
            c.drawText(Format.money(v), x, y + 28, sub)
        }
        y += 48
        c.drawLine(M.toFloat(), y, (W - M).toFloat(), y, line); y += 18

        // Categories
        c.drawText("Spending by category", M.toFloat(), y + 10, sub); y += 24
        val byCat = BudgetMath.spendByCategory(txns).entries.sortedByDescending { it.value }
        val limits = budgets.lines.associateBy { it.category }
        for ((cat, v) in byCat) {
            ensure(16f)
            c.drawText(cat, M.toFloat(), y + 10, text)
            limits[cat]?.let { c.drawText("budget ${Format.number(it.availableMinor)}", 330f, y + 10, muted) }
            c.drawText(Format.number(v), (W - M).toFloat(), y + 10, rightBold)
            y += 16
        }
        if (byCat.isEmpty()) { c.drawText("No spending this month.", M.toFloat(), y + 10, muted); y += 16 }
        y += 12
        c.drawLine(M.toFloat(), y, (W - M).toFloat(), y, line); y += 18

        // Transactions
        c.drawText("Transactions", M.toFloat(), y + 10, sub); y += 22
        fun columns() {
            c.drawText("Date", M.toFloat(), y + 10, muted)
            c.drawText("Payee", M + 60f, y + 10, muted)
            c.drawText("Category", 300f, y + 10, muted)
            c.drawText("Account", 400f, y + 10, muted)
            c.drawText("Amount", (W - M).toFloat(), y + 10, Paint(muted).apply { textAlign = Paint.Align.RIGHT })
            y += 16
        }
        columns()
        for (t in txns.filter { !it.isSplit }) {
            if (y + 15 > H - 50) { doc.finishPage(page); newPage(); columns() }
            c.drawText(Format.date(t.timestamp).substringBeforeLast(" "), M.toFloat(), y + 10, text)
            c.drawText(clip(Format.title(t.counterparty ?: Format.channelName(t.channel)), 38), M + 60f, y + 10, text)
            c.drawText(clip(if (t.isTransfer) "Own transfer" else t.category, 18), 300f, y + 10, muted)
            c.drawText(clip(accounts[t.accountId]?.name ?: t.bank, 22), 400f, y + 10, muted)
            val amount = (if (t.direction == "CREDIT") "+" else "−") + Format.number(t.amountMinor) + if (t.currency != "PKR") " ${t.currency}" else ""
            c.drawText(amount, (W - M).toFloat(), y + 10, right)
            y += 15
        }
        if (txns.isEmpty()) c.drawText("No transactions this month.", M.toFloat(), y + 10, muted)

        doc.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
        doc.close()
    }

    private fun clip(s: String, n: Int) = if (s.length <= n) s else s.take(n - 1) + "…"
}
