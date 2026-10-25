package com.abeernch.ledger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One logged transaction. Money is in minor units (paisa).
 *
 * Rules used everywhere for totals (see logic/Money.kt):
 *  - isTransfer = money moved between your own accounts: not spending, not income.
 *  - isSplit    = this row was split into child rows: the children count, this row does not.
 */
@Entity(tableName = "txn", indices = [Index(value = ["hash"], unique = true)])
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,        // when the transaction happened
    val receivedAt: Long,       // when the message arrived (or when entered by hand)
    val bank: String,           // bank name as detected from the message
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
    val source: String,         // SMS, NOTIFICATION, IMPORT, MANUAL
    val senderKey: String,      // SMS sender ID, app package, or "manual"
    val isGeneric: Boolean,
    val rawText: String,
    val hash: String,
    // ---- added in database version 2 ----
    val accountId: Long? = null,
    val fundId: Long? = null,
    val note: String? = null,
    val isTransfer: Boolean = false,
    val isSplit: Boolean = false,
    val splitParentId: Long? = null,
    val currency: String = "PKR",
    val receiptPath: String? = null
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

/**
 * A bank account, wallet, or cash.
 * Transactions are linked to an account by the detected [bank] name.
 * Balance = openingMinor + money in - money out, counting from openingDate.
 */
@Entity(tableName = "account")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,           // what the user calls it
    val bank: String,           // detected bank name that links transactions here ("" for cash)
    val kind: String,           // BANK, WALLET, CASH, SAVINGS
    val color: Long,            // ARGB
    val currency: String = "PKR",
    val openingMinor: Long = 0,
    val openingDate: Long = 0,
    val archived: Boolean = false
)

/**
 * FUND: money set aside for a purpose. Spending assigned to it is tracked (e.g. a wedding).
 * GOAL: a savings target. Saved = startMinor + money in assigned - money out assigned.
 */
@Entity(tableName = "fund")
data class Fund(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,           // FUND or GOAL
    val color: Long,
    val targetMinor: Long? = null,   // budget for a fund, target for a goal
    val startMinor: Long = 0,        // amount already saved when the goal was created
    val note: String? = null,
    val archived: Boolean = false
)

/** Monthly limit for one category. With rollover, last month's unused amount is added. */
@Entity(tableName = "budget")
data class Budget(
    @PrimaryKey val category: String,
    val limitMinor: Long,
    val rollover: Boolean = false
)

/** Money lent to or borrowed from a person. */
@Entity(tableName = "debt")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val direction: String,      // LENT (they owe me) or BORROWED (I owe them)
    val amountMinor: Long,
    val date: Long,
    val note: String? = null,
    val settledAt: Long? = null,
    val txnId: Long? = null
)

// ---- added in database version 3 ----

/**
 * Money set aside inside an account. It stays in the bank, but the app treats it as
 * not usable: Available = account balance - all locks on that account.
 *
 * Auto-lock rule (optional): when money arrives in the account, lock part of it.
 *  autoMode PERCENT: autoValue is a percentage (20 = 20%).
 *  autoMode FIXED:   autoValue is an amount in minor units.
 *  Only credits of at least autoMinCreditMinor count (e.g. only the salary).
 */
@Entity(tableName = "savings_lock")
data class SavingsLock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val name: String,
    val amountMinor: Long,
    val fundId: Long? = null,           // linked goal: its "saved" includes this lock
    val note: String? = null,
    val createdAt: Long,
    val autoMode: String = "NONE",      // NONE, PERCENT, FIXED
    val autoValue: Long = 0,
    val autoMinCreditMinor: Long = 0,
    val autoIncludeTransfers: Boolean = false
)

/** History of a lock: every add, release and auto-lock. */
@Entity(tableName = "lock_event")
data class LockEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lockId: Long,
    val lockName: String,       // kept so history reads well after a lock is deleted
    val deltaMinor: Long,       // + locked, - released
    val at: Long,
    val reason: String,         // CREATE, ADD, RELEASE, AUTO
    val txnId: Long? = null
)
