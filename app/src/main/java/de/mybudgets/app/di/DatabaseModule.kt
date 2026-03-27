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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "mybudgets.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    @Provides fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()
    @Provides fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideLabelDao(db: AppDatabase): LabelDao = db.labelDao()
    @Provides fun provideGamificationDao(db: AppDatabase): GamificationDao = db.gamificationDao()
    @Provides fun provideStandingOrderDao(db: AppDatabase): StandingOrderDao = db.standingOrderDao()
    @Provides fun provideTransferTemplateDao(db: AppDatabase): TransferTemplateDao = db.transferTemplateDao()
}

