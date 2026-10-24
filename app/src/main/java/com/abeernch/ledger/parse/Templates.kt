package com.abeernch.ledger.parse

import java.time.ZoneId

/**
 * One known message format from one bank.
 * To support a new format, add a Template to [Templates.all] and a test case
 * in ParserTest with the real message.
 */
class Template(
    val bank: String,
    val regex: Regex,
    val build: (m: MatchResult, text: String, receivedAt: Long, zone: ZoneId) -> ParsedTxn?
)

object Templates {

    // A money amount like 2,291.00 or 230
    private const val AMT = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"

    private fun rx(pattern: String) = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    // ---------------- Askari Bank ----------------

    // Dear Customer, you have performed a POS transaction of PKR. 2,291.00 from Account: 012303*****189
    // on AL LATIF PHARMACY, at 22:06:28 Dated: 23-OCT-26
    private val askariPos = Template(
        bank = "Askari Bank",
        regex = rx(
            "performed\\s+an?\\s+(\\w+)\\s+transaction\\s+of\\s+PKR\\.?\\s*$AMT\\s+from\\s+Account:?\\s*\\S*?(\\d{3,4})\\s+" +
                "on\\s+(.+?),?\\s+at\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?)\\s+Dated:?\\s*(\\d{1,2}-[A-Za-z]{3}-\\d{2,4})"
        ),
        build = { m, text, receivedAt, zone ->
            val kind = m.groupValues[1].uppercase()
            val (name, place) = TextUtil.splitMerchant(m.groupValues[4])
            ParsedTxn(
                bank = "Askari Bank",
                direction = Direction.DEBIT,
                channel = if (kind == "ATM") Channel.ATM else Channel.CARD,
                amountMinor = TextUtil.toMinor(m.groupValues[2]) ?: return@Template null,
                counterparty = name,
                location = place,
                timestamp = TextUtil.toEpoch(m.groupValues[6], m.groupValues[5], receivedAt, zone),
                accountTail = m.groupValues[3],
                balanceMinor = TextUtil.balance(text),
                feeMinor = null,
                reference = null,
                isGeneric = false
            )
        }
    )

    // ---------------- Meezan Bank ----------------

    // PKR 4,000.00 sent to MUHAMMAD UMER NAEEM (MBL AC from your A/C xxx3319 of DHA-II BR ISD
    // on 15-Oct-2026 at 18:39 TID:094763
    // Note: the bracket after the payee is often left unclosed by the bank.
    private val meezanSent = Template(
        bank = "Meezan Bank",
        regex = rx(
            "PKR\\.?\\s*$AMT\\s+sent\\s+to\\s+(.+?)\\s*(?:\\(([^)]*?)\\)?\\s*)?from\\s+your\\s+A/C\\s*\\S*?(\\d{3,4})" +
                "(?:\\s+of\\s+.+?)?\\s+on\\s+(\\d{1,2}-[A-Za-z]{3}-\\d{2,4})\\s+at\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?)" +
                "(?:\\s*TID:?\\s*(\\d+))?"
        ),
        build = { m, text, receivedAt, zone ->
            val toBank = m.groupValues[3].replace(Regex("\\s*AC$", RegexOption.IGNORE_CASE), "").trim()
            ParsedTxn(
                bank = "Meezan Bank",
                direction = Direction.DEBIT,
                channel = Channel.TRANSFER,
                amountMinor = TextUtil.toMinor(m.groupValues[1]) ?: return@Template null,
                counterparty = m.groupValues[2].trim() + if (toBank.isNotEmpty()) " ($toBank)" else "",
                location = null,
                timestamp = TextUtil.toEpoch(m.groupValues[5], m.groupValues[6], receivedAt, zone),
                accountTail = m.groupValues[4],
                balanceMinor = TextUtil.balance(text),
                feeMinor = null,
                reference = m.groupValues[7].ifEmpty { null },
                isGeneric = false
            )
        }
    )

    // ---------------- Easypaisa ----------------

    // Trx ID 57160923189: You have paid Rs. 230.00 via VISA Virtual Debit Card ***7705 at
    // JUICE LAND               ISLAMABAD    PK. Fee: Rs. 0.00.
    private val easypaisaCard = Template(
        bank = "Easypaisa",
        regex = rx(
            "Trx\\s*ID\\s*(\\d+):?\\s*You\\s+have\\s+paid\\s+Rs\\.?\\s*$AMT\\s+via\\s+.*?\\*+\\s*(\\d{3,4})\\s+at\\s+(.+?)" +
                "(?:\\.\\s*Fee:?\\s*Rs\\.?\\s*$AMT)?\\.?\\s*$"
        ),
        build = { m, text, receivedAt, _ ->
            val (name, place) = TextUtil.splitMerchant(m.groupValues[4])
            ParsedTxn(
                bank = "Easypaisa",
                direction = Direction.DEBIT,
                channel = Channel.CARD,
                amountMinor = TextUtil.toMinor(m.groupValues[2]) ?: return@Template null,
                counterparty = name,
                location = place,
                timestamp = receivedAt, // this message carries no time
                accountTail = m.groupValues[3],
                balanceMinor = TextUtil.balance(text),
                feeMinor = m.groupValues[5].ifEmpty { null }?.let { TextUtil.toMinor(it) },
                reference = m.groupValues[1],
                isGeneric = false
            )
        }
    )

    val all: List<Template> = listOf(askariPos, meezanSent, easypaisaCard)
}
