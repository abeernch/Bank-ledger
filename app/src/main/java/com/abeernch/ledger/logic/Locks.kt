package com.abeernch.ledger.logic

import com.abeernch.ledger.data.Account
import com.abeernch.ledger.data.Fund
import com.abeernch.ledger.data.SavingsLock
import com.abeernch.ledger.data.Txn

/** Savings locks: money kept in an account but treated as not usable. */
object Locks {

    fun locked(accountId: Long, locks: List<SavingsLock>): Long =
        locks.filter { it.accountId == accountId }.sumOf { it.amountMinor }

    /** What can be spent from this account: balance minus every lock on it. Can go negative. */
    fun available(a: Account, txns: List<Txn>, locks: List<SavingsLock>): Long =
        Balances.account(a, txns) - locked(a.id, locks)

    /** Money locked towards a goal. */
    fun forGoal(fundId: Long, locks: List<SavingsLock>): Long =
        locks.filter { it.fundId == fundId }.sumOf { it.amountMinor }

    /** A goal's saved amount including locks linked to it. */
    fun goalSaved(f: Fund, txns: List<Txn>, locks: List<SavingsLock>): Long =
        Balances.goalSaved(f, txns) + forGoal(f.id, locks)

    /**
     * How much of credit [t] the auto-lock rule of [lock] takes. 0 when the rule does not apply.
     * Applies only to money arriving in the lock's own account, in that account's currency.
     */
    fun autoAmount(lock: SavingsLock, t: Txn): Long {
        if (lock.autoMode == "NONE" || lock.autoValue <= 0) return 0
        if (t.direction != "CREDIT" || t.accountId != lock.accountId || t.isSplit || t.splitParentId != null) return 0
        if (t.isTransfer && !lock.autoIncludeTransfers) return 0
        if (t.amountMinor < lock.autoMinCreditMinor) return 0
        return when (lock.autoMode) {
            "PERCENT" -> t.amountMinor * lock.autoValue.coerceAtMost(100) / 100
            "FIXED" -> minOf(lock.autoValue, t.amountMinor)
            else -> 0
        }
    }

    /**
     * Applies every auto-lock rule on the account to one credit.
     * Rules run in creation order and together never take more than the credit itself.
     * Returns (lock, amount) pairs with amount > 0.
     */
    fun autoPlan(locks: List<SavingsLock>, t: Txn): List<Pair<SavingsLock, Long>> {
        var remaining = t.amountMinor
        val out = mutableListOf<Pair<SavingsLock, Long>>()
        for (l in locks.sortedBy { it.createdAt }) {
            val amount = minOf(autoAmount(l, t), remaining)
            if (amount <= 0) continue
            remaining -= amount
            out.add(l to amount)
        }
        return out
    }

    /** One-line description of a lock's rule, for the screens. */
    fun ruleText(l: SavingsLock, money: (Long) -> String): String? = when (l.autoMode) {
        "PERCENT" -> "Auto: ${l.autoValue}% of" + credits(l, money)
        "FIXED" -> "Auto: ${money(l.autoValue)} from" + credits(l, money)
        else -> null
    }

    private fun credits(l: SavingsLock, money: (Long) -> String): String =
        (if (l.autoMinCreditMinor > 0) " each credit of ${money(l.autoMinCreditMinor)} or more" else " every credit") +
            if (l.autoIncludeTransfers) ", including own transfers" else ""
}
