package com.abeernch.ledger.logic

import com.abeernch.ledger.data.Account
import com.abeernch.ledger.data.Fund
import com.abeernch.ledger.data.SavingsLock
import com.abeernch.ledger.data.Txn
import org.junit.Assert.assertEquals
import org.junit.Test

class LocksTest {

    private var nextId = 1L

    private fun txn(amount: Long, dir: String = "CREDIT", account: Long = 1, transfer: Boolean = false, fund: Long? = null) = Txn(
        id = nextId++, timestamp = 1000, receivedAt = 1000, bank = "Meezan Bank", direction = dir, channel = "TRANSFER",
        amountMinor = amount, counterparty = "EMPLOYER", location = null, accountTail = null, balanceMinor = null,
        feeMinor = null, reference = null, category = "Income", source = "SMS", senderKey = "X", isGeneric = false,
        rawText = "", hash = "h$nextId", accountId = account, isTransfer = transfer, fundId = fund
    )

    private fun lock(id: Long, amount: Long, account: Long = 1, mode: String = "NONE", value: Long = 0, min: Long = 0, transfers: Boolean = false, fund: Long? = null) =
        SavingsLock(id = id, accountId = account, name = "L$id", amountMinor = amount, createdAt = id, autoMode = mode,
            autoValue = value, autoMinCreditMinor = min, autoIncludeTransfers = transfers, fundId = fund)

    @Test
    fun availableIsBalanceMinusLocks() {
        val acc = Account(id = 1, name = "Meezan", bank = "Meezan Bank", kind = "BANK", color = 0, openingMinor = 50_000_000)
        val locks = listOf(lock(1, 40_000_000), lock(2, 5_000_000), lock(3, 1_000_000, account = 2))
        assertEquals(45_000_000L, Locks.locked(1, locks))
        assertEquals(5_000_000L, Locks.available(acc, emptyList(), locks))
        // Spending more than the free part makes available negative: that triggers the warning.
        assertEquals(-1_000_000L, Locks.available(acc, listOf(txn(6_000_000, dir = "DEBIT")), locks))
    }

    @Test
    fun autoPercentAndFixedWithSalaryThreshold() {
        val pct = lock(1, 0, mode = "PERCENT", value = 20, min = 10_000_000)  // 20% of credits >= 100,000
        assertEquals(4_000_000L, Locks.autoAmount(pct, txn(20_000_000)))
        assertEquals(0L, Locks.autoAmount(pct, txn(5_000_000)))             // below the threshold
        assertEquals(0L, Locks.autoAmount(pct, txn(20_000_000, account = 2)))  // other account
        assertEquals(0L, Locks.autoAmount(pct, txn(20_000_000, dir = "DEBIT")))
        assertEquals(0L, Locks.autoAmount(pct, txn(20_000_000, transfer = true)))
        assertEquals(4_000_000L, Locks.autoAmount(pct.copy(autoIncludeTransfers = true), txn(20_000_000, transfer = true)))

        val fixed = lock(2, 0, mode = "FIXED", value = 5_000_000)
        assertEquals(5_000_000L, Locks.autoAmount(fixed, txn(20_000_000)))
        assertEquals(3_000_000L, Locks.autoAmount(fixed, txn(3_000_000)))   // never more than the credit
    }

    @Test
    fun severalRulesNeverTakeMoreThanTheCredit() {
        val a = lock(1, 0, mode = "PERCENT", value = 70)
        val b = lock(2, 0, mode = "FIXED", value = 5_000_000)
        val plan = Locks.autoPlan(listOf(b, a), txn(10_000_000))
        assertEquals(listOf(7_000_000L, 3_000_000L), plan.map { it.second })  // a first (older), b gets the rest
        assertEquals(listOf(1L, 2L), plan.map { it.first.id })
    }

    @Test
    fun goalIncludesLinkedLocks() {
        val goal = Fund(id = 9, name = "Plot and hostel", kind = "GOAL", color = 0, startMinor = 1_000_000)
        val locks = listOf(lock(1, 40_000_000, fund = 9), lock(2, 2_000_000))
        val txns = listOf(txn(500_000, fund = 9))
        assertEquals(1_000_000L + 500_000 + 40_000_000, Locks.goalSaved(goal, txns, locks))
    }
}
