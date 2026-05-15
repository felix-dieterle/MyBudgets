package de.mybudgets.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper for backing up and restoring the Room database.
 * Used before migrations to prevent data loss.
 */
object DatabaseBackupHelper {
    
    private const val TAG = "DatabaseBackup"
    private const val DB_NAME = "mybudgets.db"
    private const val BACKUP_DIR = "db_backups"
    private const val MAX_BACKUPS = 5
    
    /**
     * Creates a backup of the current database before migration.
     * Returns backup file path if successful, null otherwise.
     */
    fun backupBeforeMigration(context: Context, fromVersion: Int, toVersion: Int): String? {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) {
                Log.w(TAG, "Database file does not exist, skipping backup")
                return null
            }
            
            val backupDir = File(context.filesDir, BACKUP_DIR)
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val backupFileName = "mybudgets-v$fromVersion-to-v$toVersion-$timestamp.db"
            val backupFile = File(backupDir, backupFileName)
            
            FileInputStream(dbFile).use { input ->
                FileOutputStream(backupFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.i(TAG, "Database backed up: ${backupFile.absolutePath}")
            cleanupOldBackups(backupDir)
            
            return backupFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to backup database", e)
            return null
        }
    }
    
    /**
     * Restores database from backup file.
     * WARNING: Closes all database connections first!
     */
    fun restoreFromBackup(context: Context, backupPath: String): Boolean {
        try {
            val backupFile = File(backupPath)
            if (!backupFile.exists()) {
                Log.e(TAG, "Backup file not found: $backupPath")
                return false
            }
            
            val dbFile = context.getDatabasePath(DB_NAME)
            
            FileInputStream(backupFile).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.i(TAG, "Database restored from: $backupPath")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore database", e)
            return false
        }
    }
    
    /**
     * Lists all available backup files.
     */
    fun listBackups(context: Context): List<File> {
        val backupDir = File(context.filesDir, BACKUP_DIR)
        if (!backupDir.exists()) return emptyList()
        
        return backupDir.listFiles { file -> file.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
    
    /**
     * Deletes old backups, keeping only MAX_BACKUPS most recent.
     */
    private fun cleanupOldBackups(backupDir: File) {
        val backups = backupDir.listFiles { file -> file.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        
        backups.drop(MAX_BACKUPS).forEach { oldBackup ->
            if (oldBackup.delete()) {
                Log.i(TAG, "Deleted old backup: ${oldBackup.name}")
            }
        }
    }
    
    /**
     * Creates a manual backup (user-initiated).
     */
    fun createManualBackup(context: Context): String? {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) {
                Log.w(TAG, "Database file does not exist")
                return null
            }
            
            val backupDir = File(context.filesDir, BACKUP_DIR)
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val backupFileName = "mybudgets-manual-$timestamp.db"
            val backupFile = File(backupDir, backupFileName)
            
            FileInputStream(dbFile).use { input ->
                FileOutputStream(backupFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.i(TAG, "Manual backup created: ${backupFile.absolutePath}")
            return backupFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create manual backup", e)
            return null
        }
    }
}
