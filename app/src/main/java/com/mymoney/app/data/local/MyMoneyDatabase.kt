package com.mymoney.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mymoney.app.data.local.dao.AccountDao
import com.mymoney.app.data.local.dao.BudgetDao
import com.mymoney.app.data.local.dao.CategoryDao
import com.mymoney.app.data.local.dao.GoalDao
import com.mymoney.app.data.local.dao.RecurrenceRuleDao
import com.mymoney.app.data.local.dao.TransactionDao
import com.mymoney.app.data.local.entity.AccountEntity
import com.mymoney.app.data.local.entity.BudgetEntity
import com.mymoney.app.data.local.entity.CategoryEntity
import com.mymoney.app.data.local.entity.GoalContributionEntity
import com.mymoney.app.data.local.entity.GoalEntity
import com.mymoney.app.data.local.entity.RecurrenceRuleEntity
import com.mymoney.app.data.local.entity.TransactionEntity

/**
 * MyMoney local database. Versioned with explicit migrations — never a
 * destructive migration in production (rule 8.15).
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        RecurrenceRuleEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        GoalContributionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MyMoneyDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurrenceRuleDao(): RecurrenceRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao

    companion object {
        /** v1 -> v2: added the goals & goal_contributions tables. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goals` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`targetCents` INTEGER NOT NULL, `deadlineEpochDay` INTEGER, `colorHex` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAtEpochDay` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goal_contributions` (`id` TEXT NOT NULL, `goalId` TEXT NOT NULL, " +
                        "`amountCents` INTEGER NOT NULL, `dateEpochDay` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_contributions_goalId` ON `goal_contributions` (`goalId`)")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}
