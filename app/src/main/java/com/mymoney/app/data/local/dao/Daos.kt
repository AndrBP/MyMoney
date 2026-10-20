package com.mymoney.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.mymoney.app.data.local.entity.AccountEntity
import com.mymoney.app.data.local.entity.BudgetEntity
import com.mymoney.app.data.local.entity.CategoryEntity
import com.mymoney.app.data.local.entity.GoalContributionEntity
import com.mymoney.app.data.local.entity.GoalEntity
import com.mymoney.app.data.local.entity.RecurrenceRuleEntity
import com.mymoney.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Upsert suspend fun upsert(account: AccountEntity)
    @Delete suspend fun delete(account: AccountEntity)
    @Query("SELECT * FROM accounts ORDER BY archived ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts") suspend fun getAll(): List<AccountEntity>
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun getById(id: String): AccountEntity?
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :id OR toAccountId = :id")
    suspend fun transactionCount(id: String): Int
    @Query("DELETE FROM transactions WHERE accountId = :id OR toAccountId = :id")
    suspend fun deleteTransactionsOf(id: String)
}

@Dao
interface CategoryDao {
    @Upsert suspend fun upsert(category: CategoryEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(categories: List<CategoryEntity>)
    @Delete suspend fun delete(category: CategoryEntity)
    @Query("SELECT * FROM categories ORDER BY kind ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY kind ASC, name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories") suspend fun getAll(): List<CategoryEntity>
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun getById(id: String): CategoryEntity?
    @Query("UPDATE transactions SET categoryId = :targetId WHERE categoryId = :sourceId")
    suspend fun reassignTransactions(sourceId: String, targetId: String)
    @Query("UPDATE recurrence_rules SET categoryId = :targetId WHERE categoryId = :sourceId")
    suspend fun reassignRecurrences(sourceId: String, targetId: String)
    @Query("UPDATE budgets SET categoryId = NULL WHERE categoryId = :sourceId")
    suspend fun clearBudgetRefs(sourceId: String)
}

@Dao
interface TransactionDao {
    @Upsert suspend fun upsert(transaction: TransactionEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(list: List<TransactionEntity>): List<Long>
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteById(id: String)
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun getById(id: String): TransactionEntity?

    @Query(
        """SELECT * FROM transactions
        WHERE (:fromDay IS NULL OR dateEpochDay >= :fromDay)
          AND (:toDay IS NULL OR dateEpochDay <= :toDay)
          AND (:type IS NULL OR type = :type)
          AND (:status IS NULL OR status = :status)
          AND (:accountId IS NULL OR accountId = :accountId OR toAccountId = :accountId)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:query IS NULL OR description LIKE '%' || :query || '%'
                            OR notes LIKE '%' || :query || '%'
                            OR tagsCsv LIKE '%' || :query || '%')
        ORDER BY
          CASE WHEN :sort = 'DATE_DESC' THEN -dateEpochDay END,
          CASE WHEN :sort = 'DATE_ASC' THEN dateEpochDay END,
          CASE WHEN :sort = 'AMOUNT_DESC' THEN -amountCents END,
          CASE WHEN :sort = 'AMOUNT_ASC' THEN amountCents END,
          CASE WHEN :sort = 'CATEGORY' THEN IFNULL(categoryId, '~') END,
          CASE WHEN :sort = 'ACCOUNT' THEN accountId END,
          dateEpochDay DESC
        LIMIT :limit OFFSET :offset"""
    )
    fun paged(
        fromDay: Long?, toDay: Long?, type: String?, status: String?,
        accountId: String?, categoryId: String?, query: String?,
        sort: String, limit: Int, offset: Int,
    ): Flow<List<TransactionEntity>>

    @Query(
        """SELECT COUNT(*) FROM transactions
        WHERE (:fromDay IS NULL OR dateEpochDay >= :fromDay)
          AND (:toDay IS NULL OR dateEpochDay <= :toDay)
          AND (:type IS NULL OR type = :type)
          AND (:status IS NULL OR status = :status)
          AND (:accountId IS NULL OR accountId = :accountId OR toAccountId = :accountId)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:query IS NULL OR description LIKE '%' || :query || '%'
                            OR notes LIKE '%' || :query || '%'
                            OR tagsCsv LIKE '%' || :query || '%')"""
    )
    fun countFiltered(
        fromDay: Long?, toDay: Long?, type: String?, status: String?,
        accountId: String?, categoryId: String?, query: String?,
    ): Flow<Int>

    @Query("SELECT * FROM transactions WHERE dateEpochDay BETWEEN :from AND :to")
    suspend fun between(from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions") suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE status = 'PLANNED' AND dateEpochDay <= :until ORDER BY dateEpochDay ASC")
    fun observeUpcoming(until: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE recurrenceId = :ruleId AND occurrenceDateEpochDay = :occDay")
    suspend fun existingOccurrences(ruleId: String, occDay: Long): List<TransactionEntity>

    @Query("UPDATE transactions SET status = :status, updatedAtEpochSecond = :now WHERE id IN (:ids)")
    suspend fun setStatuses(ids: List<String>, status: String, now: Long)

    @Query("DELETE FROM transactions WHERE recurrenceId = :ruleId AND status = 'PLANNED'")
    suspend fun deletePlannedOfRule(ruleId: String)

    @Query("SELECT DISTINCT tagsCsv FROM transactions WHERE tagsCsv != ''")
    fun observeTagSources(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: String): Int

    @Query("UPDATE transactions SET accountId = :targetAccount WHERE accountId = :sourceAccount")
    suspend fun reassignAccount(sourceAccount: String, targetAccount: String)

    @Query("UPDATE transactions SET toAccountId = :targetAccount WHERE toAccountId = :sourceAccount")
    suspend fun reassignToAccount(sourceAccount: String, targetAccount: String)
}

@Dao
interface RecurrenceRuleDao {
    @Upsert suspend fun upsert(rule: RecurrenceRuleEntity)
    @Query("DELETE FROM recurrence_rules WHERE id = :id") suspend fun deleteById(id: String)
    @Query("SELECT * FROM recurrence_rules ORDER BY active DESC, startDateEpochDay ASC")
    fun observeAll(): Flow<List<RecurrenceRuleEntity>>
    @Query("SELECT * FROM recurrence_rules WHERE active = 1") suspend fun getActive(): List<RecurrenceRuleEntity>
    @Query("SELECT * FROM recurrence_rules") suspend fun getAll(): List<RecurrenceRuleEntity>
    @Query("SELECT * FROM recurrence_rules WHERE id = :id") suspend fun getById(id: String): RecurrenceRuleEntity?
}

@Dao
interface BudgetDao {
    @Upsert suspend fun upsert(budget: BudgetEntity)
    @Query("DELETE FROM budgets WHERE id = :id") suspend fun deleteById(id: String)
    @Query("SELECT * FROM budgets ORDER BY active DESC, name COLLATE NOCASE ASC") fun observeAll(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets") suspend fun getAll(): List<BudgetEntity>
    @Query("SELECT * FROM budgets WHERE id = :id") suspend fun getById(id: String): BudgetEntity?
}

@Dao
interface GoalDao {
    @Upsert suspend fun upsertGoal(goal: GoalEntity)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun deleteGoalById(id: String)
    @Query("SELECT * FROM goals ORDER BY status ASC, createdAtEpochDay DESC") fun observeGoals(): Flow<List<GoalEntity>>
    @Query("SELECT * FROM goals") suspend fun getAllGoals(): List<GoalEntity>
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun getGoalById(id: String): GoalEntity?

    @Upsert suspend fun upsertContribution(c: GoalContributionEntity)
    @Query("DELETE FROM goal_contributions WHERE id = :id") suspend fun deleteContributionById(id: String)
    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId ORDER BY dateEpochDay DESC, id DESC")
    fun observeContributions(goalId: String): Flow<List<GoalContributionEntity>>
    @Query("SELECT * FROM goal_contributions") suspend fun getAllContributions(): List<GoalContributionEntity>
    @Query("DELETE FROM goal_contributions WHERE goalId = :goalId") suspend fun deleteContributionsOf(goalId: String)
}
