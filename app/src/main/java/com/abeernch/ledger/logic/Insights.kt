package com.abeernch.ledger.logic

import com.abeernch.ledger.data.Txn
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class MonthTotals(val month: YearMonth, val spentMinor: Long, val earnedMinor: Long)

data class CategoryChange(val category: String, val thisMinor: Long, val lastMinor: Long) {
    val deltaMinor: Long get() = thisMinor - lastMinor
}

data class Ranked(val name: String, val minor: Long, val count: Int)

/** Numbers for the Insights tab. All in PKR, using the Money rules. */
object Insights {

    fun monthly(txns: List<Txn>, endMonth: YearMonth, count: Int, zone: ZoneId): List<MonthTotals> =
        (count - 1 downTo 0).map { back ->
            val m = endMonth.minusMonths(back.toLong())
            val from = m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val inMonth = Money.inRange(txns, from, to)
            MonthTotals(m, Money.spent(inMonth), Money.earned(inMonth))
        }

    fun categoryChanges(thisMonth: List<Txn>, lastMonth: List<Txn>): List<CategoryChange> {
        val a = BudgetMath.spendByCategory(thisMonth)
        val b = BudgetMath.spendByCategory(lastMonth)
        return (a.keys + b.keys).map { CategoryChange(it, a[it] ?: 0L, b[it] ?: 0L) }
            .sortedByDescending { it.thisMinor }
    }

    fun topPayees(txns: List<Txn>, limit: Int): List<Ranked> =
        txns.filter { Money.isSpend(it) && !it.counterparty.isNullOrBlank() }
            .groupBy { it.counterparty!!.uppercase() }
            .map { (_, l) -> Ranked(l.first().counterparty!!, l.sumOf { it.amountMinor }, l.size) }
            .sortedByDescending { it.minor }
            .take(limit)

    /** Total spending on each weekday, Monday first. */
    fun byWeekday(txns: List<Txn>, zone: ZoneId): List<Pair<DayOfWeek, Long>> {
        val sums = txns.filter { Money.isSpend(it) }
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).dayOfWeek }
            .mapValues { (_, l) -> l.sumOf { it.amountMinor } }
        return DayOfWeek.values().map { it to (sums[it] ?: 0L) }
    }

    /** Average spending per day so far this month. */
    fun dailyAverage(spentMinor: Long, daysElapsed: Int): Long = if (daysElapsed > 0) spentMinor / daysElapsed else 0L
}
