package de.mybudgets.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.mybudgets.app.data.model.*

@Database(
    entities = [
        Account::class,
        Transaction::class,
        Category::class,
        Label::class,
        TransactionLabel::class,
        GamificationBadge::class,
        StandingOrder::class,
        TransferTemplate::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun labelDao(): LabelDao
    abstract fun gamificationDao(): GamificationDao
    abstract fun standingOrderDao(): StandingOrderDao
    abstract fun transferTemplateDao(): TransferTemplateDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS standing_orders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceAccountId INTEGER NOT NULL,
                        recipientName TEXT NOT NULL,
                        recipientIban TEXT NOT NULL,
                        recipientBic TEXT NOT NULL DEFAULT '',
                        amount REAL NOT NULL,
                        purpose TEXT NOT NULL DEFAULT '',
                        intervalDays INTEGER NOT NULL DEFAULT 30,
                        firstExecutionDate INTEGER NOT NULL,
                        lastExecutionDate INTEGER,
                        nextExecutionDate INTEGER NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        sentToBank INTEGER NOT NULL DEFAULT 0,
                        remoteId TEXT,
                        createdAt INTEGER NOT NULL
                    )"""
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS transfer_templates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        sourceAccountId INTEGER NOT NULL,
                        recipientName TEXT NOT NULL,
                        recipientIban TEXT NOT NULL,
                        recipientBic TEXT NOT NULL DEFAULT '',
                        amount REAL NOT NULL,
                        purpose TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL
                    )"""
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Remove duplicate labels (keep lowest id per name) before adding unique index
                database.execSQL(
                    "DELETE FROM labels WHERE id NOT IN (SELECT MIN(id) FROM labels GROUP BY name)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_labels_name ON labels(name)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mybudgets.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { INSTANCE = it }
            }
    }
}

