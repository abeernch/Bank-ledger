package com.abeernch.ledger.logic

import com.abeernch.ledger.data.Txn

/**
 * The single place that decides what counts as spending and income.
 * Every screen, the widget, alerts and exports use these rules.
 */
object Money {

    const val HOME_CURRENCY = "PKR"

    /** Real money out: not a move between own accounts, not a split parent, in PKR. */
    fun isSpend(t: Txn): Boolean =
        t.direction == "DEBIT" && !t.isTransfer && !t.isSplit && t.currency == HOME_CURRENCY

    /** Real money in, same rules. */
    fun isIncome(t: Txn): Boolean =
        t.direction == "CREDIT" && !t.isTransfer && !t.isSplit && t.currency == HOME_CURRENCY

    /** +amount for money in, -amount for money out. Split parents count as zero. */
    fun signed(t: Txn): Long = when {
        t.isSplit -> 0L
        t.direction == "CREDIT" -> t.amountMinor
        else -> -t.amountMinor
    }

    fun spent(txns: List<Txn>): Long = txns.filter { isSpend(it) }.sumOf { it.amountMinor }

    fun earned(txns: List<Txn>): Long = txns.filter { isIncome(it) }.sumOf { it.amountMinor }

    fun inRange(txns: List<Txn>, from: Long, to: Long): List<Txn> = txns.filter { it.timestamp in from until to }

    /**
     * Rows that appear in lists. The parts of a split are hidden here and shown
     * inside the parent's detail sheet. Totals use the parts, never the parent.
     */
    fun visible(txns: List<Txn>): List<Txn> = txns.filter { it.splitParentId == null }
}
