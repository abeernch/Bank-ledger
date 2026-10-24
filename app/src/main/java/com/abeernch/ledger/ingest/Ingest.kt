package com.abeernch.ledger.ingest

import android.content.Context
import com.abeernch.ledger.LedgerApp
import com.abeernch.ledger.category.Categorizer
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.data.Txn
import com.abeernch.ledger.data.Unparsed
import com.abeernch.ledger.parse.Outcome
import com.abeernch.ledger.parse.Parser
import com.abeernch.ledger.parse.TextUtil
import com.abeernch.ledger.widget.SpendWidget
import java.security.MessageDigest

/**
 * The single entry point for every message, whether it came from SMS,
 * a bank app notification, or the inbox history import.
 */
object Ingest {

    enum class Result { LOGGED, DUPLICATE, REVIEW, IGNORED }

    const val SOURCE_SMS = "SMS"
    const val SOURCE_NOTIFICATION = "NOTIFICATION"
    const val SOURCE_IMPORT = "IMPORT"

    // Two alerts for the same amount within this window are treated as one transaction.
    private const val DUPLICATE_WINDOW_MS = 3 * 60 * 1000L

    suspend fun process(
        context: Context,
        text: String,
        senderKey: String,     // SMS sender ID, or app package for notifications
        senderLabel: String,   // what to call the bank if nothing better is known
        source: String,
        receivedAt: Long,
        refreshWidget: Boolean = true
    ): Result {
        if (text.isBlank()) return Result.IGNORED
        if (source != SOURCE_NOTIFICATION && TextUtil.isPersonalSender(senderKey)) return Result.IGNORED

        LedgerApp.ensureSeeded(context)
        val db = AppDb.get(context)
        val hash = sha1(TextUtil.normalize(text).lowercase())
        if (db.txns().countHash(hash) > 0 || db.unparsed().countHash(hash) > 0) return Result.DUPLICATE

        when (val out = Parser.parse(text, receivedAt)) {
            is Outcome.Ignore -> return Result.IGNORED

            is Outcome.NeedsReview -> {
                db.unparsed().insert(
                    Unparsed(receivedAt = receivedAt, sender = senderLabel, source = source, text = text, hash = hash)
                )
                return Result.REVIEW
            }

            is Outcome.Parsed -> {
                val p = out.txn

                // Same transaction already logged through the other channel (SMS vs app)?
                val near = db.txns().nearby(
                    p.amountMinor, p.direction.name,
                    receivedAt - DUPLICATE_WINDOW_MS, receivedAt + DUPLICATE_WINDOW_MS
                )
                val sameTxn = near.any { it.reference == null || p.reference == null || it.reference == p.reference }
                if (sameTxn) return Result.DUPLICATE

                val bank = db.aliases().bankFor(senderKey) ?: p.bank ?: senderLabel
                val rules = db.rules().allNow().map { it.keyword to it.category }
                val category = Categorizer.categorize(p.counterparty, p.channel, p.direction, rules)

                db.txns().insert(
                    Txn(
                        timestamp = p.timestamp,
                        receivedAt = receivedAt,
                        bank = bank,
                        direction = p.direction.name,
                        channel = p.channel.name,
                        amountMinor = p.amountMinor,
                        counterparty = p.counterparty,
                        location = p.location,
                        accountTail = p.accountTail,
                        balanceMinor = p.balanceMinor,
                        feeMinor = p.feeMinor,
                        reference = p.reference,
                        category = category,
                        source = source,
                        senderKey = senderKey,
                        isGeneric = p.isGeneric,
                        rawText = text,
                        hash = hash
                    )
                )
                if (refreshWidget) SpendWidget.refresh(context)
                return Result.LOGGED
            }
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
