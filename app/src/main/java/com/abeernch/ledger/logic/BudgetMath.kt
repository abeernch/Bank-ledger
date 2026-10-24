package com.abeernch.ledger.logic

import com.abeernch.ledger.data.Budget
import com.abeernch.ledger.data.Txn

/** One category's budget for one month. */
data class BudgetLine(
    val category: String,
    val limitMinor: Long,
    val carryMinor: Long,       // unused amount brought from last month (rollover only)
    val spentMinor: Long
) {
    val availableMinor: Long get() = limitMinor + carryMinor
    val leftMinor: Long get() = availableMinor - spentMinor
    val fraction: Float get() = if (availableMinor > 0) spentMinor.toFloat() / availableMinor else 0f
    val isOver: Boolean get() = spentMinor > availableMinor
}

data class BudgetSummary(
    val lines: List<BudgetLine>,
    val unbudgetedMinor: Long,  // spending in categories that have no budget
    val daysLeft: Int           // including today; 0 for a past month
) {
    val totalMinor: Long get() = lines.sumOf { it.availableMinor }
    val spentMinor: Long get() = lines.sumOf { it.spentMinor }
    val leftMinor: Long get() = totalMinor - spentMinor
    val fraction: Float get() = if (totalMinor > 0) spentMinor.toFloat() / totalMinor else 0f
    val perDayMinor: Long get() = if (daysLeft > 0 && leftMinor > 0) leftMinor / daysLeft else 0L
}

object BudgetMath {

    /**
     * [monthTxns] and [prevMonthTxns] are already filtered to their months.
     * Rollover carries only last month's unused amount, never older months,
     * and never a negative amount, so one bad month does not haunt the next.
     */
    fun summarize(
        budgets: List<Budget>,
        monthTxns: List<Txn>,
        prevMonthTxns: List<Txn>,
        daysLeft: Int
    ): BudgetSummary {
        val spentBy = spendByCategory(monthTxns)
        val prevBy = spendByCategory(prevMonthTxns)
        val lines = budgets.map { b ->
            val carry = if (b.rollover) maxOf(0L, b.limitMinor - (prevBy[b.category] ?: 0L)) else 0L
            BudgetLine(b.category, b.limitMinor, carry, spentBy[b.category] ?: 0L)
        }.sortedByDescending { it.fraction }
        val budgeted = budgets.map { it.category }.toSet()
        val unbudgeted = spentBy.filterKeys { it !in budgeted }.values.sum()
        return BudgetSummary(lines, unbudgeted, daysLeft)
    }

    fun spendByCategory(txns: List<Txn>): Map<String, Long> =
        txns.filter { Money.isSpend(it) }.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }

    /** Alert thresholds crossed by this category this month: 80 and/or 100. */
    fun thresholdsCrossed(line: BudgetLine): List<Int> {
        val out = mutableListOf<Int>()
        if (line.availableMinor <= 0) return out
        if (line.spentMinor * 100 >= line.availableMinor * 80) out.add(80)
        if (line.spentMinor >= line.availableMinor) out.add(100)
        return out
    }
}
