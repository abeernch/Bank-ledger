package com.abeernch.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Txn::class, Unparsed::class, Rule::class, Alias::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun txns(): TxnDao
    abstract fun unparsed(): UnparsedDao
    abstract fun rules(): RuleDao
    abstract fun aliases(): AliasDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "ledger.db")
                .build()
                .also { instance = it }
        }
    }
}
