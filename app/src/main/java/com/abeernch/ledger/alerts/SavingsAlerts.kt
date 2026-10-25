package com.abeernch.ledger.alerts

import android.content.Context
import com.abeernch.ledger.Prefs
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.data.LockEvent
import com.abeernch.ledger.data.Txn
import com.abeernch.ledger.logic.Locks
import com.abeernch.ledger.ui.Format

/** Auto-locking part of incoming money, and warning when spending dips into locked savings. */
object SavingsAlerts {

    /** Runs the account's auto-lock rules on a new credit. */
    suspend fun autoLock(context: Context, t: Txn) {
        if (t.direction != "CREDIT" || t.accountId == null) return
        val db = AppDb.get(context)
        val plan = Locks.autoPlan(db.locks().forAccount(t.accountId), t)
        for ((lock, amount) in plan) {
            db.locks().update(lock.copy(amountMinor = lock.amountMinor + amount))
            db.lockEvents().insert(
                LockEvent(lockId = lock.id, lockName = lock.name, deltaMinor = amount, at = System.currentTimeMillis(), reason = "AUTO", txnId = t.id)
            )
            Notifier.savings(
                context, ("auto" + t.id + lock.id).hashCode(),
                "Locked ${Format.money(amount)} into ${lock.name}",
                "From the ${Format.money(t.amountMinor)} that arrived" + (t.counterparty?.let { " from ${Format.title(it)}" } ?: "") + "."
            )
        }
    }

    /**
     * Warns once when an account's available money goes below zero, meaning locked savings
     * were spent. The warning re-arms when the account is back above zero.
     */
    suspend fun checkDip(context: Context, accountId: Long?) {
        if (accountId == null) return
        val db = AppDb.get(context)
        val locks = db.locks().forAccount(accountId)
        if (locks.isEmpty()) return
        val account = db.accounts().byId(accountId) ?: return
        val available = Locks.available(account, db.txns().allNow(), locks)
        val prefs = Prefs(context)
        val key = "dip|$accountId"
        if (available >= 0) {
            prefs.clearAlert(key)
            return
        }
        if (prefs.alertShown(key)) return
        prefs.markAlertShown(key)
        Notifier.savings(
            context, key.hashCode(),
            "You dipped into locked savings",
            "${account.name} is ${Format.money(-available)} below its locked amount. Top it up or release part of a lock."
        )
    }
}
