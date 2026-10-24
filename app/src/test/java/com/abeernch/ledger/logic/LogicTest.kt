package com.abeernch.ledger.logic

import com.abeernch.ledger.data.Account
import com.abeernch.ledger.data.Budget
import com.abeernch.ledger.data.Fund
import com.abeernch.ledger.data.Txn
import com.abeernch.ledger.parse.Outcome
import com.abeernch.ledger.parse.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class LogicTest {

    private val zone = ZoneId.of("Asia/Karachi")

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, mi: Int = 0) =
        LocalDateTime.of(y, m, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private var nextId = 1L

    private fun txn(
        amount: Long, dir: String = "DEBIT", category: String = "Other", time: Long = at(2026, 10, 10),
        account: Long? = 1, fund: Long? = null, transfer: Boolean = false, split: Boolean = false,
        who: String? = null, currency: String = "PKR"
    ) = Txn(
        id = nextId++, timestamp = time, receivedAt = time, bank = "Bank", direction = dir, channel = "CARD",
        amountMinor = amount, counterparty = who, location = null, accountTail = null, balanceMinor = null,
        feeMinor = null, reference = null, category = category, source = "SMS", senderKey = "X",
        isGeneric = false, rawText = "", hash = "h$nextId", accountId = account, fundId = fund,
        isTransfer = transfer, isSplit = split, currency = currency
    )

    @Test
    fun moneyRulesSkipTransfersSplitsAndForeign() {
        val list = listOf(
            txn(1000), txn(500, transfer = true), txn(700, split = true), txn(300, currency = "USD"),
            txn(2000, dir = "CREDIT"), txn(900, dir = "CREDIT", transfer = true)
        )
        assertEquals(1000L, Money.spent(list))
        assertEquals(2000L, Money.earned(list))
    }

    @Test
    fun budgetWithRollover() {
        val budgets = listOf(Budget("Food", 10000, rollover = true), Budget("Fuel", 5000))
        val thisMonth = listOf(txn(3000, category = "Food"), txn(6000, category = "Fuel"), txn(400, category = "Health"))
        val lastMonth = listOf(txn(7000, category = "Food"))
        val s = BudgetMath.summarize(budgets, thisMonth, lastMonth, daysLeft = 5)

        val food = s.lines.first { it.category == "Food" }
        assertEquals(3000L, food.carryMinor)        // 10,000 - 7,000 unused last month
        assertEquals(10000L, food.leftMinor)         // 13,000 available - 3,000 spent
        val fuel = s.lines.first { it.category == "Fuel" }
        assertTrue(fuel.isOver)
        assertEquals(listOf(80, 100), BudgetMath.thresholdsCrossed(fuel))
        assertEquals(400L, s.unbudgetedMinor)
        assertEquals(18000L, s.totalMinor)
        assertEquals(9000L, s.spentMinor)
        assertEquals(1800L, s.perDayMinor)
        assertEquals("Fuel", s.lines.first().category)   // most used first
    }

    @Test
    fun accountAndFundBalances() {
        val acc = Account(id = 1, name = "Meezan", bank = "Meezan Bank", kind = "BANK", color = 0, openingMinor = 100000, openingDate = at(2026, 10, 1))
        val fund = Fund(id = 7, name = "Abeer Shadi", kind = "FUND", color = 0)
        val goal = Fund(id = 8, name = "Plot", kind = "GOAL", color = 0, startMinor = 50000)
        val list = listOf(
            txn(600000, fund = 7), txn(1200000, fund = 7), txn(20000, dir = "CREDIT", fund = 8),
            txn(5000, time = at(2026, 9, 20)),          // before opening date: ignored
            txn(3000, transfer = true)                  // own transfer still leaves this account
        )
        assertEquals(1800000L, Balances.fundSpent(fund, list))
        assertEquals(70000L, Balances.goalSaved(goal, list))
        assertEquals(100000L - 600000 - 1200000 + 20000 - 3000, Balances.account(acc, list))
    }

    @Test
    fun transferPartnerFound() {
        val out = txn(500000, account = 1, time = at(2026, 10, 15, 18, 39))
        val inn = txn(500000, dir = "CREDIT", account = 2, time = at(2026, 10, 15, 18, 41))
        val other = txn(500000, dir = "CREDIT", account = 1, time = at(2026, 10, 15, 18, 40)) // same account
        val late = txn(500000, dir = "CREDIT", account = 3, time = at(2026, 10, 15, 20, 0))  // too late
        assertEquals(inn.id, TransferMatch.partner(out, listOf(other, late, inn))?.id)
    }

    @Test
    fun toSelfNeedsTwoNameWords() {
        assertTrue(TransferMatch.isToSelf("ABEER NASIR CHAUDHRY (MBL)", "Abeer Nasir Chaudhry"))
        assertTrue(!TransferMatch.isToSelf("ABEER KHAN", "Abeer Nasir Chaudhry"))
        assertTrue(!TransferMatch.isToSelf("MUHAMMAD UMER NAEEM", "Abeer Nasir Chaudhry"))
    }

    @Test
    fun recurringBillDetected() {
        val list = listOf(
            txn(450000, who = "IESCO", category = "Bills & Utilities", time = at(2026, 8, 12)),
            txn(470000, who = "IESCO", category = "Bills & Utilities", time = at(2026, 9, 11)),
            txn(460000, who = "IESCO", category = "Bills & Utilities", time = at(2026, 10, 12)),
            txn(23000, who = "JUICE LAND", time = at(2026, 10, 24)),
            txn(100000, who = "DARAZ", time = at(2026, 9, 3)), txn(900000, who = "DARAZ", time = at(2026, 10, 3)) // amounts too far apart
        )
        val r = Recurring.detect(list, LocalDate.of(2026, 10, 25), zone)
        assertEquals(1, r.size)
        assertEquals("IESCO", r[0].name)
        assertEquals(LocalDate.of(2026, 11, 12), r[0].nextDate)
        assertEquals(3, r[0].months)
    }

    @Test
    fun foreignAmountParsed() {
        val out = Parser.parse("Your card ***1234 has been charged USD 12.99 at NETFLIX.COM", at(2026, 10, 1), zone)
        val t = (out as Outcome.Parsed).txn
        assertEquals("USD", t.currency)
        assertEquals(1299L, t.amountMinor)
    }

    @Test
    fun pkrPreferredOverForeign() {
        val out = Parser.parse("Your card ***1234 has been charged USD 10.00 (PKR 2,850.00) at SPOTIFY", at(2026, 10, 1), zone)
        val t = (out as Outcome.Parsed).txn
        assertEquals("PKR", t.currency)
        assertEquals(285000L, t.amountMinor)
    }
}
