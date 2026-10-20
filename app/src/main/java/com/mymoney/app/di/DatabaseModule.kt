package com.mymoney.app.di

import android.content.Context
import androidx.room.Room
import com.mymoney.app.data.local.MyMoneyDatabase
import com.mymoney.app.data.local.dao.AccountDao
import com.mymoney.app.data.local.dao.BudgetDao
import com.mymoney.app.data.local.dao.CategoryDao
import com.mymoney.app.data.local.dao.GoalDao
import com.mymoney.app.data.local.dao.RecurrenceRuleDao
import com.mymoney.app.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MyMoneyDatabase =
        Room.databaseBuilder(context, MyMoneyDatabase::class.java, "mymoney.db")
            .addMigrations(*MyMoneyDatabase.ALL_MIGRATIONS) // explicit migrations only, never destructive
            .build()

    @Provides fun provideAccountDao(db: MyMoneyDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: MyMoneyDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideTransactionDao(db: MyMoneyDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideRecurrenceDao(db: MyMoneyDatabase): RecurrenceRuleDao = db.recurrenceRuleDao()
    @Provides fun provideBudgetDao(db: MyMoneyDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideGoalDao(db: MyMoneyDatabase): GoalDao = db.goalDao()
}
