package com.abeernch.ledger.parse

enum class Direction { DEBIT, CREDIT }

enum class Channel { CARD, ATM, TRANSFER, BILL, OTHER }

/**
 * One transaction read out of a bank message.
 * Amounts are in minor units (paisa): PKR 2,291.00 is stored as 229100.
 */
data class ParsedTxn(
    val bank: String?,          // null when the generic parser cannot tell
    val direction: Direction,
    val channel: Channel,
    val amountMinor: Long,
    val counterparty: String?,  // merchant, payee or sender
    val location: String?,      // city / country when the message gives one
    val timestamp: Long,        // epoch ms of the transaction
    val accountTail: String?,   // last digits of account or card
    val balanceMinor: Long?,    // available balance after the transaction
    val feeMinor: Long?,
    val reference: String?,     // TID, Trx ID, etc.
    val isGeneric: Boolean,     // true when no bank template matched
    val currency: String = "PKR"
)
