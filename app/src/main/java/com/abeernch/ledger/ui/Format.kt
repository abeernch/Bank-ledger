package com.abeernch.ledger.ui

import java.text.DecimalFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val whole = DecimalFormat("#,##0")
    private val withPaisa = DecimalFormat("#,##0.00")
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** 229100 -> "PKR 2,291". Shows paisa only when there are some. */
    fun money(minor: Long): String {
        val sign = if (minor < 0) "-" else ""
        val abs = kotlin.math.abs(minor)
        val text = if (abs % 100 == 0L) whole.format(abs / 100) else withPaisa.format(abs / 100.0)
        return "${sign}PKR $text"
    }

    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val fullFmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.ENGLISH)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    fun day(ms: Long): String = dayFmt.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun time(ms: Long): String = timeFmt.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun full(ms: Long): String = fullFmt.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun month(m: YearMonth): String = monthFmt.format(m)

    fun monthOf(ms: Long): YearMonth = YearMonth.from(Instant.ofEpochMilli(ms).atZone(zone))

    fun monthStart(m: YearMonth): Long = m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun monthEnd(m: YearMonth): Long = m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun channelName(c: String): String = when (c) {
        "CARD" -> "Card"
        "ATM" -> "ATM"
        "TRANSFER" -> "Transfer"
        "BILL" -> "Bill"
        else -> "Other"
    }
}
