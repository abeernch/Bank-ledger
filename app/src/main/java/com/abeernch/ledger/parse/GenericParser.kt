package com.abeernch.ledger.parse

/**
 * Fallback for banks or formats without a template.
 * It needs an amount with a currency and a clear debit or credit word.
 * It uses the arrival time of the message as the transaction time.
 */
object GenericParser {

    private val creditWords = Regex(
        "credited|received|deposited|has been added|added to your|transferred to your|sent you|refund|reversal|reversed|cash ?back|inward",
        RegexOption.IGNORE_CASE
    )

    private val debitWords = Regex(
        "debited|\\bpaid\\b|\\bsent\\b|transferred to|withdrawn|withdrawal|purchase|\\bspent\\b|deducted|\\bPOS\\b|charged|payment of|bill payment",
        RegexOption.IGNORE_CASE
    )

    private val atmWords = Regex("\\bATM\\b|withdraw", RegexOption.IGNORE_CASE)
    private val cardWords = Regex("\\bPOS\\b|\\bcard\\b|purchase", RegexOption.IGNORE_CASE)
    private val transferWords = Regex("IBFT|raast|transfer|sent to|received from|sent you|funds", RegexOption.IGNORE_CASE)
    private val billWords = Regex("\\bbill\\b|top-?up|recharge|mobile load", RegexOption.IGNORE_CASE)

    // The word after which a merchant or person name usually appears, up to a stop word or punctuation.
    private val counterpartyRegex = Regex(
        "\\b(?:at|on|to|from)\\s+([A-Za-z][A-Za-z0-9&'./\\- ]{1,40}?)(?=\\s*(?:[,;:(]|\\.\\s|\\.$|$|\\s(?:on|at|dated|from|via|ref|tid|trx|with|is|of|for|\\d)))",
        RegexOption.IGNORE_CASE
    )

    // Words that are never a counterparty
    private val notNames = setOf(
        "your", "your a/c", "your account", "account", "a/c", "ac", "card", "the", "you"
    )

    fun direction(text: String): Direction? {
        val c = creditWords.find(text)?.range?.first
        val d = debitWords.find(text)?.range?.first
        return when {
            c == null && d == null -> null
            c == null -> Direction.DEBIT
            d == null -> Direction.CREDIT
            c < d -> Direction.CREDIT
            else -> Direction.DEBIT
        }
    }

    fun channel(text: String): Channel = when {
        atmWords.containsMatchIn(text) -> Channel.ATM
        transferWords.containsMatchIn(text) -> Channel.TRANSFER
        billWords.containsMatchIn(text) -> Channel.BILL
        cardWords.containsMatchIn(text) -> Channel.CARD
        else -> Channel.OTHER
    }

    /**
     * The transaction amount is the first currency amount that is not a balance or fee.
     * PKR wins: a foreign card payment that also states the PKR charge is logged in PKR.
     */
    private fun amount(text: String): Pair<Long, String>? {
        for (m in TextUtil.amountRegex.findAll(text)) {
            if (isSideAmount(text, m.range.first)) continue
            return TextUtil.toMinor(m.groupValues[1])?.let { it to "PKR" }
        }
        for (m in TextUtil.foreignRegex.findAll(text)) {
            if (isSideAmount(text, m.range.first)) continue
            return TextUtil.toMinor(m.groupValues[2])?.let { it to TextUtil.currencyCode(m.groupValues[1]) }
        }
        return null
    }

    private fun isSideAmount(text: String, start: Int): Boolean {
        val before = text.substring(maxOf(0, start - 25), start).lowercase()
        return "bal" in before || "fee" in before || "limit" in before || "charges" in before
    }

    private fun counterparty(text: String): String? {
        for (m in counterpartyRegex.findAll(text)) {
            val name = m.groupValues[1].trim().trimEnd('.')
            if (name.lowercase() in notNames) continue
            if (name.lowercase().startsWith("your ")) continue
            if (name.length < 2) continue
            return name
        }
        return null
    }

    fun parse(text: String, receivedAt: Long): ParsedTxn? {
        if (TextUtil.isOtp(text)) return null
        val (amt, currency) = amount(text) ?: return null
        val dir = direction(text) ?: return null
        val raw = counterparty(text)
        val (name, place) = if (raw != null) TextUtil.splitMerchant(raw) else Pair(null, null)
        return ParsedTxn(
            bank = null,
            direction = dir,
            channel = channel(text),
            amountMinor = amt,
            counterparty = name,
            location = place,
            timestamp = receivedAt,
            accountTail = TextUtil.accountTail(text),
            balanceMinor = TextUtil.balance(text),
            feeMinor = null,
            reference = null,
            isGeneric = true,
            currency = currency
        )
    }
}
