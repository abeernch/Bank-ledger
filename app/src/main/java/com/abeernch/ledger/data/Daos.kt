package com.abeernch.ledger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TxnDao {
    @Query("SELECT * FROM txn ORDER BY timestamp DESC")
    fun all(): Flow<List<Txn>>

    @Query("SELECT * FROM txn ORDER BY timestamp ASC")
    suspend fun allNow(): List<Txn>

    @Query("SELECT * FROM txn WHERE id = :id")
    suspend fun byId(id: Long): Txn?

    @Query("SELECT * FROM txn WHERE timestamp >= :from AND timestamp < :to")
    suspend fun between(from: Long, to: Long): List<Txn>

    @Query("SELECT * FROM txn WHERE splitParentId = :parentId")
    suspend fun children(parentId: Long): List<Txn>

    @Query("SELECT COUNT(*) FROM txn WHERE hash = :hash")
    suspend fun countHash(hash: String): Int

    /** Same amount and direction arriving close together: probably the same transaction seen twice. */
    @Query(
        "SELECT * FROM txn WHERE amountMinor = :amount AND direction = :direction " +
            "AND receivedAt BETWEEN :from AND :to"
    )
    suspend fun nearby(amount: Long, direction: String, from: Long, to: Long): List<Txn>

    /** Candidates for an own-account transfer: same amount, any direction, close in time. */
    @Query("SELECT * FROM txn WHERE amountMinor = :amount AND timestamp BETWEEN :from AND :to")
    suspend fun sameAmountAround(amount: Long, from: Long, to: Long): List<Txn>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: Txn): Long

    @Update
    suspend fun update(t: Txn)

    @Delete
    suspend fun delete(t: Txn)

    @Query("DELETE FROM txn WHERE splitParentId = :parentId")
    suspend fun deleteChildren(parentId: Long)

    @Query("UPDATE txn SET category = :category WHERE counterparty = :counterparty")
    suspend fun setCategoryFor(counterparty: String, category: String)

    @Query("UPDATE txn SET fundId = NULL WHERE fundId = :fundId")
    suspend fun clearFund(fundId: Long)

    @Query("SELECT DISTINCT senderKey FROM txn WHERE accountId = :accountId")
    suspend fun senderKeysFor(accountId: Long): List<String>

    @Query("UPDATE txn SET accountId = :toId WHERE accountId = :fromId")
    suspend fun moveAccount(fromId: Long, toId: Long)
}

@Dao
interface UnparsedDao {
    @Query("SELECT * FROM unparsed ORDER BY receivedAt DESC")
    fun all(): Flow<List<Unparsed>>

    @Query("SELECT COUNT(*) FROM unparsed WHERE hash = :hash")
    suspend fun countHash(hash: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(u: Unparsed)

    @Delete
    suspend fun delete(u: Unparsed)

    @Query("DELETE FROM unparsed")
    suspend fun deleteAll()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rule ORDER BY isUser DESC, category, keyword")
    fun all(): Flow<List<Rule>>

    @Query("SELECT * FROM rule ORDER BY isUser DESC")
    suspend fun allNow(): List<Rule>

    @Insert
    suspend fun insert(r: Rule)

    @Insert
    suspend fun insertAll(r: List<Rule>)

    @Delete
    suspend fun delete(r: Rule)

    @Query("DELETE FROM rule WHERE isUser = 1 AND keyword = :keyword")
    suspend fun deleteUserRule(keyword: String)
}

@Dao
interface AliasDao {
    @Query("SELECT bank FROM alias WHERE senderKey = :key")
    suspend fun bankFor(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(a: Alias)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM account ORDER BY archived, name")
    fun all(): Flow<List<Account>>

    @Query("SELECT * FROM account")
    suspend fun allNow(): List<Account>

    @Query("SELECT * FROM account WHERE bank = :bank ORDER BY archived LIMIT 1")
    suspend fun byBank(bank: String): Account?

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun byId(id: Long): Account?

    @Insert
    suspend fun insert(a: Account): Long

    @Update
    suspend fun update(a: Account)

    @Delete
    suspend fun delete(a: Account)
}

@Dao
interface FundDao {
    @Query("SELECT * FROM fund ORDER BY archived, name")
    fun all(): Flow<List<Fund>>

    @Query("SELECT * FROM fund")
    suspend fun allNow(): List<Fund>

    @Insert
    suspend fun insert(f: Fund): Long

    @Update
    suspend fun update(f: Fund)

    @Delete
    suspend fun delete(f: Fund)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget ORDER BY category")
    fun all(): Flow<List<Budget>>

    @Query("SELECT * FROM budget")
    suspend fun allNow(): List<Budget>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(b: Budget)

    @Delete
    suspend fun delete(b: Budget)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debt ORDER BY settledAt IS NOT NULL, date DESC")
    fun all(): Flow<List<Debt>>

    @Insert
    suspend fun insert(d: Debt): Long

    @Update
    suspend fun update(d: Debt)

    @Delete
    suspend fun delete(d: Debt)
}

@Dao
interface LockDao {
    @Query("SELECT * FROM savings_lock ORDER BY accountId, createdAt")
    fun all(): Flow<List<SavingsLock>>

    @Query("SELECT * FROM savings_lock")
    suspend fun allNow(): List<SavingsLock>

    @Query("SELECT * FROM savings_lock WHERE accountId = :accountId")
    suspend fun forAccount(accountId: Long): List<SavingsLock>

    @Insert
    suspend fun insert(l: SavingsLock): Long

    @Update
    suspend fun update(l: SavingsLock)

    @Delete
    suspend fun delete(l: SavingsLock)

    @Query("UPDATE savings_lock SET fundId = NULL WHERE fundId = :fundId")
    suspend fun clearFund(fundId: Long)

    @Query("UPDATE savings_lock SET accountId = :toId WHERE accountId = :fromId")
    suspend fun moveAccount(fromId: Long, toId: Long)
}

@Dao
interface LockEventDao {
    @Query("SELECT * FROM lock_event ORDER BY at DESC")
    fun all(): Flow<List<LockEvent>>

    @Insert
    suspend fun insert(e: LockEvent)
}
