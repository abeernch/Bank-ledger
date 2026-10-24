package com.abeernch.ledger.parse

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** Small helpers shared by the bank templates and the generic parser. */
object TextUtil {

    /** "2,291.00" -> 229100 paisa. Returns null if the text is not a number. */
    fun toMinor(amount: String): Long? {
        val clean = amount.replace(",", "").trim().trimEnd('.')
        if (clean.isEmpty()) return null
        return try {
            BigDecimal(clean).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    /** Collapse runs of whitespace so the same message always looks the same. */
    fun normalize(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    // ---------- dates ----------

    private fun formatter(pattern: String): DateTimeFormatter =
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH)

    private val dateFormats = listOf(
        formatter("d-MMM-yy"),     // 23-OCT-26
        formatter("d-MMM-yyyy"),   // 15-Oct-2026
        formatter("d/M/yyyy"),     // 15/10/2026
        formatter("d-M-yyyy"),     // 15-10-2026
        formatter("d/M/yy")        // 15/10/26
    )

    private val timeFormats = listOf(
        formatter("H:mm:ss"),
        formatter("H:mm"),
        formatter("h:mm a"),
        formatter("h:mma")
    )

    fun parseDate(s: String): LocalDate? {
        for (f in dateFormats) {
            try { return LocalDate.parse(s.trim(), f) } catch (e: Exception) { }
        }
        return null
    }

    fun parseTime(s: String): LocalTime? {
        for (f in timeFormats) {
            try { return LocalTime.parse(s.trim(), f) } catch (e: Exception) { }
        }
        return null
    }

    /**
     * Combine a parsed date and time into epoch ms.
     * Falls back to [receivedAt] if the date is missing or looks wrong
     * (more than a day after the message arrived).
     */
    fun toEpoch(date: String?, time: String?, receivedAt: Long, zone: ZoneId): Long {
        val d = date?.let { parseDate(it) } ?: return receivedAt
        val t = time?.let { parseTime(it) } ?: LocalTime.NOON
        val ms = d.atTime(t).atZone(zone).toInstant().toEpochMilli()
        val oneDay = 24L * 3600 * 1000
        return if (ms > receivedAt + oneDay) receivedAt else ms
    }

    // ---------- merchant descriptor ----------

    /**
     * Card networks send merchant names as fixed-width fields, e.g.
     * "JUICE LAND               ISLAMABAD    PK".
     * Splits that into ("JUICE LAND", "ISLAMABAD, PK").
     */
    fun splitMerchant(raw: String): Pair<String, String?> {
        val parts = raw.trim().split(Regex("\\s{2,}")).filter { it.isNotBlank() }
        if (parts.size < 2) return Pair(raw.trim(), null)
        return Pair(parts[0], parts.drop(1).joinToString(", "))
    }

    // ---------- account / balance ----------

    private val accountRegex = Regex(
        "(?:A/C|Acc(?:oun)?t|AC|Card)\\s*(?:No\\.?|#)?\\s*:?\\s*[0-9]*[*xX]+\\s*([0-9]{3,4})",
        RegexOption.IGNORE_CASE
    )

    fun accountTail(text: String): String? = accountRegex.find(text)?.groupValues?.get(1)

    private val balanceRegex = Regex(
        "(?:avail(?:able)?\\.?\\s*)?bal(?:ance)?\\.?\\s*(?:is)?\\s*:?\\s*(?:PKR|Rs)\\.?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )

    fun balance(text: String): Long? = balanceRegex.find(text)?.groupValues?.get(1)?.let { toMinor(it) }

    // ---------- message filters ----------

    private val otpRegex = Regex(
        "\\bOTP\\b|one[- ]time|do not share|don't share|verification code|\\bPIN\\b|passcode",
        RegexOption.IGNORE_CASE
    )

    private val promoRegex = Regex(
        "\\boffer\\b|discount|% ?off|\\bwin\\b|avail now|apply now|limited time|download|\\bdeal\\b|\\bsale\\b",
        RegexOption.IGNORE_CASE
    )

    val amountRegex = Regex(
        "(?:PKR|Rs)\\.?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )

    fun isOtp(text: String) = otpRegex.containsMatchIn(text)

    fun isPromo(text: String) = promoRegex.containsMatchIn(text)

    fun hasAmount(text: String) = amountRegex.containsMatchIn(text)

    /** Personal mobile numbers have 10+ digits. Banks use short codes or names. */
    fun isPersonalSender(sender: String): Boolean {
        val digits = sender.count { it.isDigit() }
        val letters = sender.count { it.isLetter() }
        return letters == 0 && digits >= 10
    }
}
