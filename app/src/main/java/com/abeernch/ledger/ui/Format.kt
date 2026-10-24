package com.abeernch.ledger.ui

import com.abeernch.ledger.parse.TextUtil
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val whole = DecimalFormat("#,##0")
    private val withPaisa = DecimalFormat("#,##0.00")
    val zone: ZoneId get() = ZoneId.systemDefault()

    /** 229100 -> "PKR 2,291". Shows paisa only when there are some. */
    fun money(minor: Long, currency: String = "PKR"): String = "$currency ${number(minor)}"

    /** 229100 -> "2,291" (no currency), with a minus sign when negative. */
    fun number(minor: Long): String {
        val sign = if (minor < 0) "−" else ""
        val abs = kotlin.math.abs(minor)
        val text = if (abs % 100 == 0L) whole.format(abs / 100) else withPaisa.format(abs / 100.0)
        return sign + text
    }

    /** Compact: 6,521 / 57.5k / 1.2M. For tight spaces like the widget and chart labels. */
    fun moneyShort(minor: Long): String {
        val r = kotlin.math.abs(minor) / 100.0
        val sign = if (minor < 0) "−" else ""
        return sign + when {
            r >= 1_000_000 -> String.format(Locale.ENGLISH, "%.1fM", r / 1_000_000)
            r >= 100_000 -> String.format(Locale.ENGLISH, "%.0fk", r / 1_000)
            r >= 10_000 -> String.format(Locale.ENGLISH, "%.1fk", r / 1_000)
            else -> whole.format(r)
        }
    }

    /** "+ PKR 2,291" for money in, "− PKR 2,291" for money out. */
    fun signedMoney(minor: Long, credit: Boolean, currency: String = "PKR"): String =
        (if (credit) "+ " else "− ") + money(minor, currency)

    /** Text typed by the user -> minor units. "2,291.5" -> 229150. */
    fun parseMinor(text: String): Long? = TextUtil.toMinor(text.trim())

    /** Minor units -> text for an input field. 229150 -> "2291.50", 229100 -> "2291". */
    fun toInput(minor: Long): String =
        if (minor % 100 == 0L) (minor / 100).toString() else String.format(Locale.ENGLISH, "%d.%02d", minor / 100, minor % 100)

    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val fullFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val monthShortFmt = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val longDateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

    fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** "Today", "Yesterday", or "Fri 23 Oct". */
    fun dayHeader(ms: Long): String {
        val d = localDate(ms)
        val today = LocalDate.now()
        return when (d) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> dayFmt.format(d)
        }
    }

    fun dayHeader(d: LocalDate): String {
        val today = LocalDate.now()
        return when (d) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            else -> dayFmt.format(d)
        }
    }

    fun date(ms: Long): String = dateFmt.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun date(d: LocalDate): String = dateFmt.format(d)
    fun time(ms: Long): String = timeFmt.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun full(ms: Long): String = fullFmt.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun month(m: YearMonth): String = monthFmt.format(m)
    fun monthShort(m: YearMonth): String = monthShortFmt.format(m)
    fun todayLong(): String = longDateFmt.format(LocalDate.now())

    fun monthOf(ms: Long): YearMonth = YearMonth.from(Instant.ofEpochMilli(ms).atZone(zone))
    fun monthStart(m: YearMonth): Long = m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun monthEnd(m: YearMonth): Long = m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Days left in [m] including today. 0 for past months, the full length for future ones. */
    fun daysLeft(m: YearMonth): Int {
        val now = YearMonth.now()
        return when {
            m < now -> 0
            m > now -> m.lengthOfMonth()
            else -> m.lengthOfMonth() - LocalDate.now().dayOfMonth + 1
        }
    }

    /** Days passed in [m] including today. */
    fun daysElapsed(m: YearMonth): Int = m.lengthOfMonth() - daysLeft(m) + if (m == YearMonth.now()) 1 else 0

    fun ordinal(n: Int): String = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }

    fun greeting(): String {
        val h = java.time.LocalTime.now().hour
        return when {
            h < 12 -> "Good morning"
            h < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    fun channelName(c: String): String = when (c) {
        "CARD" -> "Card"
        "ATM" -> "ATM"
        "TRANSFER" -> "Transfer"
        "BILL" -> "Bill"
        else -> "Other"
    }

    /** "MUHAMMAD UMER NAEEM" -> "Muhammad Umer Naeem". Leaves mixed-case names alone. */
    fun title(name: String): String {
        if (name.any { it.isLowerCase() }) return name
        return name.lowercase().split(" ").joinToString(" ") { w ->
            w.replaceFirstChar { it.uppercase() }
        }
    }
}
