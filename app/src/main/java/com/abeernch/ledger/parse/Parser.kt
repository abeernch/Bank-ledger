package com.abeernch.ledger.parse

import java.time.ZoneId

/** What the parser decided about one incoming message. */
sealed class Outcome {
    data class Parsed(val txn: ParsedTxn) : Outcome()
    object NeedsReview : Outcome()   // looks like money moved, but could not be read
    object Ignore : Outcome()        // OTP, promotion, or not about money
}

object Parser {

    fun parse(text: String, receivedAt: Long, zone: ZoneId = ZoneId.systemDefault()): Outcome {
        if (text.isBlank() || TextUtil.isOtp(text)) return Outcome.Ignore

        // 1. Known bank formats
        for (t in Templates.all) {
            val m = t.regex.find(text) ?: continue
            val txn = t.build(m, text, receivedAt, zone) ?: continue
            return Outcome.Parsed(txn)
        }

        // 2. Generic fallback
        if (!TextUtil.hasAmount(text)) return Outcome.Ignore
        val generic = GenericParser.parse(text, receivedAt)
        if (generic != null && !TextUtil.isPromo(text)) return Outcome.Parsed(generic)

        // 3. Has money in it but we could not read it
        return if (TextUtil.isPromo(text)) Outcome.Ignore else Outcome.NeedsReview
    }
}
