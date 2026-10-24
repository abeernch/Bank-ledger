package com.abeernch.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Txn::class, Unparsed::class, Rule::class, Alias::class, Account::class, Fund::class, Budget::class, Debt::class],
    version = 2,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun txns(): TxnDao
    abstract fun unparsed(): UnparsedDao
    abstract fun rules(): RuleDao
    abstract fun aliases(): AliasDao
    abstract fun accounts(): AccountDao
    abstract fun funds(): FundDao
    abstract fun budgets(): BudgetDao
    abstract fun debts(): DebtDao

    companion object {
        const val FILE_NAME = "ledger.db"

        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, FILE_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }

        /** Used by restore: closes the database so its file can be replaced. */
        fun closeForRestore() = synchronized(this) {
            instance?.close()
            instance = null
        }

        /**
         * Version 1 -> 2: keeps every logged transaction.
         * Adds the new columns, the new tables, and one account per bank already seen.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE txn ADD COLUMN accountId INTEGER")
                db.execSQL("ALTER TABLE txn ADD COLUMN fundId INTEGER")
                db.execSQL("ALTER TABLE txn ADD COLUMN note TEXT")
                db.execSQL("ALTER TABLE txn ADD COLUMN isTransfer INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE txn ADD COLUMN isSplit INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE txn ADD COLUMN splitParentId INTEGER")
                db.execSQL("ALTER TABLE txn ADD COLUMN currency TEXT NOT NULL DEFAULT 'PKR'")
                db.execSQL("ALTER TABLE txn ADD COLUMN receiptPath TEXT")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS account (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, bank TEXT NOT NULL, kind TEXT NOT NULL, color INTEGER NOT NULL, " +
                        "currency TEXT NOT NULL, openingMinor INTEGER NOT NULL, openingDate INTEGER NOT NULL, " +
                        "archived INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS fund (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, kind TEXT NOT NULL, color INTEGER NOT NULL, targetMinor INTEGER, " +
                        "startMinor INTEGER NOT NULL, note TEXT, archived INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS budget (category TEXT PRIMARY KEY NOT NULL, " +
                        "limitMinor INTEGER NOT NULL, rollover INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS debt (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "person TEXT NOT NULL, direction TEXT NOT NULL, amountMinor INTEGER NOT NULL, " +
                        "date INTEGER NOT NULL, note TEXT, settledAt INTEGER, txnId INTEGER)"
                )

                // One account per bank already in the log, then link the transactions to it.
                // Colour is a fixed teal; the user can change it on the Accounts tab.
                db.execSQL(
                    "INSERT INTO account (name, bank, kind, color, currency, openingMinor, openingDate, archived) " +
                        "SELECT DISTINCT bank, bank, 'BANK', 4279203438, 'PKR', 0, 0, 0 FROM txn"
                )
                db.execSQL("UPDATE txn SET accountId = (SELECT id FROM account WHERE account.bank = txn.bank)")
            }
        }
    }
}
