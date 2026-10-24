package com.abeernch.ledger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One logged transaction. Money is in paisa (minor units). */
@Entity(tableName = "txn", indices = [Index(value = ["hash"], unique = true)])
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,        // when the transaction happened
    val receivedAt: Long,       // when the message arrived
    val bank: String,
    val direction: String,      // DEBIT or CREDIT
    val channel: String,        // CARD, ATM, TRANSFER, BILL, OTHER
    val amountMinor: Long,
    val counterparty: String?,
    val location: String?,
    val accountTail: String?,
    val balanceMinor: Long?,
    val feeMinor: Long?,
    val reference: String?,
    val category: String,
    val source: String,         // SMS, NOTIFICATION, IMPORT
    val senderKey: String,      // SMS sender ID or app package
    val isGeneric: Boolean,
    val rawText: String,
    val hash: String
)

/** A message that mentions money but could not be read. */
@Entity(tableName = "unparsed", indices = [Index(value = ["hash"], unique = true)])
data class Unparsed(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receivedAt: Long,
    val sender: String,
    val source: String,
    val text: String,
    val hash: String
)

/** If the counterparty contains [keyword], the category is [category]. User rules win over defaults. */
@Entity(tableName = "rule")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val category: String,
    val isUser: Boolean
)

/** A bank name the user chose for an SMS sender or app package. */
@Entity(tableName = "alias")
data class Alias(
    @PrimaryKey val senderKey: String,
    val bank: String
)
