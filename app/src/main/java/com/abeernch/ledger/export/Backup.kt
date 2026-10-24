package com.abeernch.ledger.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.ui.MainActivity
import java.io.File
import kotlin.system.exitProcess

/**
 * Backup = a copy of the database file. The app has no internet, so this is how
 * the data moves to a new phone. Receipt photos are not included.
 */
object Backup {

    private val SQLITE_HEADER = "SQLite format 3".toByteArray()

    suspend fun save(context: Context, uri: Uri) {
        val db = AppDb.get(context)
        // Write everything pending in the log file into the main file first.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val file = context.getDatabasePath(AppDb.FILE_NAME)
        context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    /** Returns false if the file is not a Ledger backup. On success the app restarts. */
    fun restore(context: Context, uri: Uri): Boolean {
        val temp = File(context.cacheDir, "restore.db")
        context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } } ?: return false
        val header = temp.inputStream().use { s -> ByteArray(SQLITE_HEADER.size).also { s.read(it) } }
        if (!header.contentEquals(SQLITE_HEADER)) {
            temp.delete()
            return false
        }
        AppDb.closeForRestore()
        val target = context.getDatabasePath(AppDb.FILE_NAME)
        File(target.path + "-wal").delete()
        File(target.path + "-shm").delete()
        temp.copyTo(target, overwrite = true)
        temp.delete()

        // Restart so every screen reads the restored data.
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        exitProcess(0)
    }
}
