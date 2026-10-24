package com.abeernch.ledger.ui

import android.content.Context
import android.net.Uri
import com.abeernch.ledger.category.Categorizer
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.data.Debt
import com.abeernch.ledger.data.Rule
import com.abeernch.ledger.data.Txn
import com.abeernch.ledger.ingest.Ingest
import com.abeernch.ledger.widget.SpendWidget
import java.io.File
import java.util.UUID

/** Every change the screens make to the data. Call from Dispatchers.IO. */
object Actions {

    /** Changing a category also saves a rule, so future payments to the same place get it too. */
    suspend fun recategorize(ctx: Context, t: Txn, category: String) {
        val db = AppDb.get(ctx)
        val name = t.counterparty
        if (name.isNullOrBlank() || t.splitParentId != null) {
            db.txns().update(t.copy(category = category))
        } else {
            val keyword = name.uppercase()
            db.rules().deleteUserRule(keyword)
            db.rules().insert(Rule(keyword = keyword, category = category, isUser = true))
            db.txns().setCategoryFor(name, category)
        }
        SpendWidget.refresh(ctx)
    }

    suspend fun assignFund(ctx: Context, t: Txn, fundId: Long?) {
        AppDb.get(ctx).txns().update(t.copy(fundId = fundId))
    }

    /** Marks or unmarks a transaction as a move between your own accounts. */
    suspend fun setTransfer(ctx: Context, t: Txn, on: Boolean) {
        val category = if (on) Categorizer.TRANSFERS else if (t.direction == "CREDIT") Categorizer.INCOME else Categorizer.OTHER
        AppDb.get(ctx).txns().update(t.copy(isTransfer = on, category = category))
        SpendWidget.refresh(ctx)
    }

    suspend fun setNote(ctx: Context, t: Txn, note: String) {
        AppDb.get(ctx).txns().update(t.copy(note = note.trim().ifEmpty { null }))
    }

    /** Deletes a transaction, its split parts, and its receipt photo. */
    suspend fun delete(ctx: Context, t: Txn) {
        val db = AppDb.get(ctx)
        db.txns().deleteChildren(t.id)
        t.receiptPath?.let { File(it).delete() }
        db.txns().delete(t)
        SpendWidget.refresh(ctx)
    }

    /** One part of a split: amount and category. */
    data class Part(val amountMinor: Long, val category: String, val note: String?)

    /**
     * Splits [t] into parts. The parts carry the totals; the original is kept for the record.
     * An empty [parts] list undoes the split.
     */
    suspend fun split(ctx: Context, t: Txn, parts: List<Part>) {
        val db = AppDb.get(ctx)
        db.txns().deleteChildren(t.id)
        if (parts.isEmpty()) {
            db.txns().update(t.copy(isSplit = false))
        } else {
            parts.forEachIndexed { i, p ->
                db.txns().insert(
                    t.copy(
                        id = 0, amountMinor = p.amountMinor, category = p.category, note = p.note,
                        splitParentId = t.id, isSplit = false, hash = "split-${t.id}-$i-${UUID.randomUUID()}",
                        receiptPath = null, balanceMinor = null, feeMinor = null
                    )
                )
            }
            db.txns().update(t.copy(isSplit = true))
        }
        SpendWidget.refresh(ctx)
    }

    /** Fields of the add / edit form. */
    data class Entry(
        val amountMinor: Long,
        val credit: Boolean,
        val accountId: Long,
        val category: String,
        val counterparty: String?,
        val timestamp: Long,
        val note: String?,
        val fundId: Long?
    )

    /** Adds a transaction typed by hand (cash spending, a payment the bank did not text about). */
    suspend fun addManual(ctx: Context, e: Entry) {
        val db = AppDb.get(ctx)
        val acc = db.accounts().byId(e.accountId)
        val t = Txn(
            timestamp = e.timestamp, receivedAt = System.currentTimeMillis(),
            bank = acc?.name ?: "Cash", direction = if (e.credit) "CREDIT" else "DEBIT",
            channel = "OTHER", amountMinor = e.amountMinor, counterparty = e.counterparty?.trim()?.ifEmpty { null },
            location = null, accountTail = null, balanceMinor = null, feeMinor = null, reference = null,
            category = e.category, source = Ingest.SOURCE_MANUAL, senderKey = "manual", isGeneric = false,
            rawText = "", hash = "manual-" + UUID.randomUUID(), accountId = e.accountId,
            note = e.note?.trim()?.ifEmpty { null }, fundId = e.fundId, currency = acc?.currency ?: "PKR"
        )
        val id = db.txns().insert(t)
        Ingest.afterInsert(ctx, t.copy(id = id), live = true)
    }

    /** Edits any transaction. The original message text is kept unchanged. */
    suspend fun update(ctx: Context, t: Txn, e: Entry) {
        AppDb.get(ctx).txns().update(
            t.copy(
                amountMinor = e.amountMinor, direction = if (e.credit) "CREDIT" else "DEBIT",
                accountId = e.accountId, category = e.category,
                counterparty = e.counterparty?.trim()?.ifEmpty { null }, timestamp = e.timestamp,
                note = e.note?.trim()?.ifEmpty { null }, fundId = e.fundId
            )
        )
        SpendWidget.refresh(ctx)
    }

    /** Copies a picked photo into app storage and links it to the transaction. */
    suspend fun attachReceipt(ctx: Context, t: Txn, uri: Uri) {
        val dir = File(ctx.filesDir, "receipts").apply { mkdirs() }
        val file = File(dir, "${t.id}-${System.currentTimeMillis()}.jpg")
        ctx.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
        t.receiptPath?.let { File(it).delete() }
        AppDb.get(ctx).txns().update(t.copy(receiptPath = file.absolutePath))
    }

    suspend fun removeReceipt(ctx: Context, t: Txn) {
        t.receiptPath?.let { File(it).delete() }
        AppDb.get(ctx).txns().update(t.copy(receiptPath = null))
    }

    /** Records a transfer as money lent to (or borrowed from) its counterparty. */
    suspend fun markAsLoan(ctx: Context, t: Txn, person: String) {
        AppDb.get(ctx).debts().insert(
            Debt(
                person = person.trim(),
                direction = if (t.direction == "DEBIT") "LENT" else "BORROWED",
                amountMinor = t.amountMinor, date = t.timestamp, txnId = t.id
            )
        )
    }
}
