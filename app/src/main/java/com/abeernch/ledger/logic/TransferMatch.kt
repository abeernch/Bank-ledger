package com.abeernch.ledger.logic

import com.abeernch.ledger.data.Txn

/**
 * Finds money moving between your own accounts, so it is not counted as spending or income.
 *
 * Two ways to spot it:
 *  1. Money leaves one account and the same amount arrives in another within 30 minutes.
 *  2. The payee or sender is you (your name is set in Settings).
 */
object TransferMatch {

    const val WINDOW_MS = 30 * 60 * 1000L

    /** The other half of a transfer for [t], if one exists among [candidates]. */
    fun partner(t: Txn, candidates: List<Txn>): Txn? =
        candidates
            .filter {
                it.id != t.id &&
                    !it.isTransfer && !it.isSplit &&
                    it.amountMinor == t.amountMinor &&
                    it.currency == t.currency &&
                    it.direction != t.direction &&
                    it.accountId != null && it.accountId != t.accountId &&
                    kotlin.math.abs(it.timestamp - t.timestamp) <= WINDOW_MS
            }
            .minByOrNull { kotlin.math.abs(it.timestamp - t.timestamp) }

    /**
     * True when the counterparty is the user. Needs at least two words of the name
     * to match, so a payee who shares only a first name is not caught.
     */
    fun isToSelf(counterparty: String?, myName: String?): Boolean {
        if (counterparty.isNullOrBlank() || myName.isNullOrBlank()) return false
        val mine = words(myName)
        if (mine.size < 2) return false
        val theirs = words(counterparty)
        return mine.count { it in theirs } >= 2
    }

    private fun words(s: String): Set<String> =
        s.uppercase().split(Regex("[^A-Z]+")).filter { it.length >= 3 }.toSet()
}
