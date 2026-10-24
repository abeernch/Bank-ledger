package com.abeernch.ledger.logic

import com.abeernch.ledger.data.Account
import com.abeernch.ledger.data.Fund
import com.abeernch.ledger.data.Txn

object Balances {

    /**
     * Account balance = opening balance + money in - money out,
     * counting transactions in the account's currency from the opening date.
     * Own-account transfers DO count here: money really left or arrived in this account.
     */
    fun account(a: Account, txns: List<Txn>): Long =
        a.openingMinor + txns
            .filter { it.accountId == a.id && it.currency == a.currency && it.timestamp >= a.openingDate }
            .sumOf { Money.signed(it) }

    /** The newest balance a bank message reported for this account, if any. */
    fun reported(a: Account, txns: List<Txn>): Txn? =
        txns.filter { it.accountId == a.id && it.balanceMinor != null }.maxByOrNull { it.timestamp }

    /** Spending assigned to a fund, minus refunds assigned to it. */
    fun fundSpent(f: Fund, txns: List<Txn>): Long =
        -txns.filter { it.fundId == f.id }.sumOf { Money.signed(it) }

    /** What a goal holds: starting amount + money in assigned - money out assigned. */
    fun goalSaved(f: Fund, txns: List<Txn>): Long =
        f.startMinor + txns.filter { it.fundId == f.id }.sumOf { Money.signed(it) }
}
