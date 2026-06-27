package de.mybudgets.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.mybudgets.app.data.db.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        val prefs = context.getSharedPreferences("mybudgets_prefs", Context.MODE_PRIVATE)
        val prevVersion = prefs.getInt("db_version", 0)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "mybudgets.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15
            )
            .build()
        if (prevVersion != 0 && prevVersion < 13) {
            val mig = AppDatabase.lastMigrationVersion ?: "v$prevVersion→v13"
            prefs.edit().putString("migration_info", mig).apply()
        }
        prefs.edit().putInt("db_version", 13).apply()
        return db
    }

    @Provides fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()
    @Provides fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideLabelDao(db: AppDatabase): LabelDao = db.labelDao()
    @Provides fun provideGamificationDao(db: AppDatabase): GamificationDao = db.gamificationDao()
    @Provides fun provideStandingOrderDao(db: AppDatabase): StandingOrderDao = db.standingOrderDao()
    @Provides fun provideTransferTemplateDao(db: AppDatabase): TransferTemplateDao = db.transferTemplateDao()
    @Provides fun provideCategoryPatternDao(db: AppDatabase): CategoryPatternDao = db.categoryPatternDao()
    @Provides fun provideRecurringRuleDao(db: AppDatabase): RecurringRuleDao = db.recurringRuleDao()
    @Provides fun provideSyncIntervalDao(db: AppDatabase): SyncIntervalDao = db.syncIntervalDao()
}
