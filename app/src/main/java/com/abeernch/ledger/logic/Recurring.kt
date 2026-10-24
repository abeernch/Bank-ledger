package com.abeernch.ledger.logic

import com.abeernch.ledger.data.Txn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A payment that seems to happen every month. */
data class RecurringItem(
    val name: String,
    val typicalMinor: Long,
    val dayOfMonth: Int,
    val lastDate: LocalDate,
    val nextDate: LocalDate,
    val months: Int,            // how many months it was seen in
    val category: String
)

/**
 * Spots monthly payments (bills, subscriptions, rent) from the log.
 * A payee counts as recurring when it was paid in at least 2 different months
 * of the last 6, with amounts within 20% of their median.
 */
object Recurring {

    fun detect(txns: List<Txn>, today: LocalDate, zone: ZoneId): List<RecurringItem> {
        val since = today.minusMonths(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val groups = txns
            .filter { Money.isSpend(it) && it.timestamp >= since && !it.counterparty.isNullOrBlank() }
            .groupBy { key(it.counterparty!!) }

        val out = mutableListOf<RecurringItem>()
        for ((_, list) in groups) {
            val dates = list.map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            val months = dates.map { YearMonth.from(it) }.toSet()
            if (months.size < 2) continue

            val amounts = list.map { it.amountMinor }.sorted()
            val median = amounts[amounts.size / 2]
            if (amounts.any { kotlin.math.abs(it - median) * 5 > median }) continue   // more than 20% apart

            val days = dates.map { it.dayOfMonth }.sorted()
            val day = days[days.size / 2]
            val last = dates.max()
            out.add(
                RecurringItem(
                    name = list.maxBy { it.timestamp }.counterparty!!,
                    typicalMinor = median,
                    dayOfMonth = day,
                    lastDate = last,
                    nextDate = nextOn(day, last),
                    months = months.size,
                    category = list.maxBy { it.timestamp }.category
                )
            )
        }
        return out.sortedBy { it.nextDate }
    }

    /** The same day in the month after [last], clamped to the month's length. */
    private fun nextOn(day: Int, last: LocalDate): LocalDate {
        val ym = YearMonth.from(last).plusMonths(1)
        return ym.atDay(minOf(day, ym.lengthOfMonth()))
    }

    /** "K-ELECTRIC 1234" and "K-Electric" should be the same payee. */
    private fun key(name: String): String =
        name.uppercase().replace(Regex("[^A-Z ]"), " ").replace(Regex("\\s+"), " ").trim()
}
