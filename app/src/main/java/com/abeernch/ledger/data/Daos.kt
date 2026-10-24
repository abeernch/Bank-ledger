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

    @Query("SELECT * FROM txn WHERE timestamp >= :from AND timestamp < :to")
    suspend fun between(from: Long, to: Long): List<Txn>

    @Query("SELECT COUNT(*) FROM txn WHERE hash = :hash")
    suspend fun countHash(hash: String): Int

    /** Same amount and direction arriving close together: probably the same transaction seen twice. */
    @Query(
        "SELECT * FROM txn WHERE amountMinor = :amount AND direction = :direction " +
            "AND receivedAt BETWEEN :from AND :to"
    )
    suspend fun nearby(amount: Long, direction: String, from: Long, to: Long): List<Txn>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: Txn): Long

    @Update
    suspend fun update(t: Txn)

    @Delete
    suspend fun delete(t: Txn)

    @Query("UPDATE txn SET category = :category WHERE counterparty = :counterparty")
    suspend fun setCategoryFor(counterparty: String, category: String)

    @Query("UPDATE txn SET bank = :newName WHERE bank = :oldName")
    suspend fun renameBank(oldName: String, newName: String)

    @Query("SELECT DISTINCT senderKey FROM txn WHERE bank = :bank")
    suspend fun senderKeysFor(bank: String): List<String>
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
