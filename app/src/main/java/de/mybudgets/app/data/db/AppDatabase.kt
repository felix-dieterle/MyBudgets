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
    version = 8,
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
                // Step 1: Re-link transaction_labels entries that point to a duplicate label
                // so they point to the kept (lowest-id) label with the same name instead.
                // OR IGNORE prevents conflicts when the transaction is already linked to the kept label.
                database.execSQL("""
                    INSERT OR IGNORE INTO transaction_labels (transactionId, labelId)
                    SELECT tl.transactionId,
                           (SELECT MIN(l2.id) FROM labels l2 WHERE l2.name = l.name)
                    FROM transaction_labels tl
                    JOIN labels l ON l.id = tl.labelId
                    WHERE tl.labelId NOT IN (SELECT MIN(id) FROM labels GROUP BY name)
                """.trimIndent())
                // Step 2: Remove the now-superseded entries that still point to duplicate label IDs.
                database.execSQL("""
                    DELETE FROM transaction_labels
                    WHERE labelId NOT IN (SELECT MIN(id) FROM labels GROUP BY name)
                """.trimIndent())
                // Step 3: Remove duplicate label rows (keep lowest id per name).
                database.execSQL(
                    "DELETE FROM labels WHERE id NOT IN (SELECT MIN(id) FROM labels GROUP BY name)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_labels_name ON labels(name)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transaction_labels_labelId ON transaction_labels(labelId)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE accounts ADD COLUMN userId TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE accounts ADD COLUMN tanMethod TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE accounts ADD COLUMN autoAssignPattern TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE accounts ADD COLUMN targetAmount REAL"
                )
                database.execSQL(
                    "ALTER TABLE accounts ADD COLUMN targetDueDate INTEGER"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transaction_labels_labelId ON transaction_labels(labelId)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mybudgets.db"
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8
                ).build().also { INSTANCE = it }
            }
    }
}
