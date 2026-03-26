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
        StandingOrder::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun labelDao(): LabelDao
    abstract fun gamificationDao(): GamificationDao
    abstract fun standingOrderDao(): StandingOrderDao

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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mybudgets.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}

