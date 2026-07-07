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
        TransferTemplate::class,
        CategoryPattern::class,
        RecurringRule::class,
        SyncInterval::class,
        RecurrencePattern::class
    ],
    version = 17,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun syncIntervalDao(): SyncIntervalDao
    abstract fun labelDao(): LabelDao
    abstract fun gamificationDao(): GamificationDao
    abstract fun standingOrderDao(): StandingOrderDao
    abstract fun transferTemplateDao(): TransferTemplateDao
    abstract fun categoryPatternDao(): CategoryPatternDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun recurrencePatternDao(): RecurrencePatternDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        @Volatile var lastMigrationVersion: String? = null

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

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add performance indices for categories and transactions
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transactions_categoryId ON transactions(categoryId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_categories_parentCategoryId ON categories(parentCategoryId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transactions_date ON transactions(date)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transactions_accountId ON transactions(accountId)"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS category_patterns (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        patternType TEXT NOT NULL,
                        patternValue TEXT NOT NULL,
                        confidence REAL NOT NULL DEFAULT 0.7,
                        usageCount INTEGER NOT NULL DEFAULT 0,
                        lastUsed INTEGER,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE CASCADE
                    )"""
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_category_patterns_categoryId ON category_patterns(categoryId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_category_patterns_type_value ON category_patterns(patternType, patternValue)"
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recurring_rules ADD COLUMN matchAmountTolerance REAL")
                lastMigrationVersion = "v11→v12"
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS sync_intervals (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        accountId INTEGER NOT NULL,
                        startDate INTEGER NOT NULL,
                        endDate INTEGER NOT NULL,
                        isHistorical INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )"""
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sync_intervals_accountId ON sync_intervals(accountId)"
                )
                lastMigrationVersion = "v12→v13"
            }
        }
        
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add recurrence_patterns table
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS recurrence_patterns (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        keywords TEXT,
                        targetIban TEXT,
                        amountMin REAL,
                        amountMax REAL,
                        intervalDays INTEGER,
                        createdAt INTEGER NOT NULL,
                        lastUsed INTEGER
                    )"""
                )
                
                // Add recurrencePatternId column to transactions
                database.execSQL(
                    "ALTER TABLE transactions ADD COLUMN recurrencePatternId INTEGER"
                )
                
                lastMigrationVersion = "v13→v14"
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE category_patterns ADD COLUMN matchedName TEXT NOT NULL DEFAULT ''")
                lastMigrationVersion = "v14→v15"
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN originalDescription TEXT NOT NULL DEFAULT ''")
                lastMigrationVersion = "v15→v16"
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE category_patterns ADD COLUMN amountMin REAL")
                database.execSQL("ALTER TABLE category_patterns ADD COLUMN amountMax REAL")
                database.execSQL("ALTER TABLE category_patterns ADD COLUMN filterIncome INTEGER")
                lastMigrationVersion = "v16→v17"
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS recurring_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        matchKeyword TEXT NOT NULL,
                        matchAmount REAL,
                        intervalDays INTEGER NOT NULL DEFAULT 30,
                        categoryId INTEGER,
                        accountId INTEGER,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL
                    )"""
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recurring_rules_matchKeyword ON recurring_rules(matchKeyword)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recurring_rules_accountId ON recurring_rules(accountId)"
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
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17
                ).build().also { INSTANCE = it }
            }
    }
}
