package com.mymoney.app.data.repository

import androidx.room.withTransaction
import com.mymoney.app.core.util.Ids
import com.mymoney.app.data.local.MyMoneyDatabase
import com.mymoney.app.data.local.dao.BudgetDao
import com.mymoney.app.data.local.dao.GoalDao
import com.mymoney.app.data.local.dao.RecurrenceRuleDao
import com.mymoney.app.data.local.dao.TransactionDao
import com.mymoney.app.data.local.entity.BudgetEntity
import com.mymoney.app.data.local.entity.GoalContributionEntity
import com.mymoney.app.data.local.entity.GoalEntity
import com.mymoney.app.data.local.entity.RecurrenceRuleEntity
import com.mymoney.app.data.local.entity.TransactionEntity
import com.mymoney.app.domain.model.Budget
import com.mymoney.app.domain.model.BudgetPeriod
import com.mymoney.app.domain.model.Goal
import com.mymoney.app.domain.model.GoalContribution
import com.mymoney.app.domain.model.GoalStatus
import com.mymoney.app.domain.model.Money
import com.mymoney.app.domain.model.RecurrenceFrequency
import com.mymoney.app.domain.model.RecurrenceRule
import com.mymoney.app.domain.model.Transaction
import com.mymoney.app.domain.model.TransactionStatus
import com.mymoney.app.domain.model.TransactionType
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
    amount = Money(amountCents),
    dateEpochDay = dateEpochDay,
    timeText = timeText,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    description = description,
    notes = notes,
    tags = if (tagsCsv.isBlank()) emptyList() else tagsCsv.split("|").map { it.trim() }.filter { it.isNotEmpty() },
    status = runCatching { TransactionStatus.valueOf(status) }.getOrDefault(TransactionStatus.CONFIRMED),
    recurrenceId = recurrenceId,
    occurrenceDateEpochDay = occurrenceDateEpochDay,
    createdAtEpochSecond = createdAtEpochSecond,
    updatedAtEpochSecond = updatedAtEpochSecond,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id, type = type.name, amountCents = amount.cents, dateEpochDay = dateEpochDay,
    timeText = timeText, accountId = accountId, toAccountId = toAccountId, categoryId = categoryId,
    description = description, notes = notes, tagsCsv = tags.joinToString("|"), status = status.name,
    recurrenceId = recurrenceId, occurrenceDateEpochDay = occurrenceDateEpochDay,
    createdAtEpochSecond = createdAtEpochSecond, updatedAtEpochSecond = updatedAtEpochSecond,
)

fun RecurrenceRuleEntity.toDomain() = RecurrenceRule(
    id = id,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
    description = description, amount = Money(amountCents), accountId = accountId,
    toAccountId = toAccountId, categoryId = categoryId,
    frequency = runCatching { RecurrenceFrequency.valueOf(frequency) }.getOrDefault(RecurrenceFrequency.MONTHLY),
    customIntervalDays = customIntervalDays, startDateEpochDay = startDateEpochDay,
    endDateEpochDay = endDateEpochDay, dueDay = dueDay, active = active, autoGenerate = autoGenerate,
    lastGeneratedOccurrenceEpochDay = lastGeneratedOccurrenceEpochDay,
    ignoredOccurrences = if (ignoredCsv.isBlank()) emptyList() else ignoredCsv.split(",").mapNotNull { it.toLongOrNull() },
)

fun RecurrenceRule.toEntity() = RecurrenceRuleEntity(
    id = id, type = type.name, description = description, amountCents = amount.cents,
    accountId = accountId, toAccountId = toAccountId, categoryId = categoryId,
    frequency = frequency.name, customIntervalDays = customIntervalDays,
    startDateEpochDay = startDateEpochDay, endDateEpochDay = endDateEpochDay, dueDay = dueDay,
    active = active, autoGenerate = autoGenerate,
    lastGeneratedOccurrenceEpochDay = lastGeneratedOccurrenceEpochDay,
    ignoredCsv = ignoredOccurrences.joinToString(","),
)

fun BudgetEntity.toDomain() = Budget(
    id = id, name = name, categoryId = categoryId,
    period = runCatching { BudgetPeriod.valueOf(period) }.getOrDefault(BudgetPeriod.MONTHLY),
    limitAmount = Money(limitCents), startDateEpochDay = startDateEpochDay,
    active = active, includePlanned = includePlanned, colorHex = colorHex,
)

fun Budget.toEntity() = BudgetEntity(
    id = id, name = name, categoryId = categoryId, period = period.name,
    limitCents = limitAmount.cents, startDateEpochDay = startDateEpochDay,
    active = active, includePlanned = includePlanned, colorHex = colorHex,
)

fun GoalEntity.toDomain() = Goal(
    id = id, name = name, targetAmount = Money(targetCents), deadlineEpochDay = deadlineEpochDay,
    colorHex = colorHex, status = runCatching { GoalStatus.valueOf(status) }.getOrDefault(GoalStatus.ACTIVE),
    notes = notes, createdAtEpochDay = createdAtEpochDay,
)

fun Goal.toEntity() = GoalEntity(
    id = id, name = name, targetCents = targetAmount.cents, deadlineEpochDay = deadlineEpochDay,
    colorHex = colorHex, status = status.name, notes = notes, createdAtEpochDay = createdAtEpochDay,
)

fun GoalContributionEntity.toDomain() =
    GoalContribution(id = id, goalId = goalId, amount = Money(amountCents), dateEpochDay = dateEpochDay, note = note)

fun GoalContribution.toEntity() =
    GoalContributionEntity(id = id, goalId = goalId, amountCents = amount.cents, dateEpochDay = dateEpochDay, note = note)

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val db: MyMoneyDatabase,
) {
    fun paged(
        fromDay: Long?, toDay: Long?, type: String?, status: String?,
        accountId: String?, categoryId: String?, query: String?, sort: String,
        limit: Int, offset: Int,
    ): Flow<List<Transaction>> =
        dao.paged(fromDay, toDay, type, status, accountId, categoryId, query, sort, limit, offset)
            .map { list -> list.map(TransactionEntity::toDomain) }

    fun countFiltered(
        fromDay: Long?, toDay: Long?, type: String?, status: String?,
        accountId: String?, categoryId: String?, query: String?,
    ): Flow<Int> = dao.countFiltered(fromDay, toDay, type, status, accountId, categoryId, query)

    fun observeUpcoming(untilEpochDay: Long): Flow<List<Transaction>> =
        dao.observeUpcoming(untilEpochDay).map { it.map(TransactionEntity::toDomain) }

    suspend fun getById(id: String): Transaction? = dao.getById(id)?.toDomain()
    suspend fun getAll(): List<Transaction> = dao.getAll().map(TransactionEntity::toDomain)
    suspend fun between(from: Long, to: Long): List<Transaction> = dao.between(from, to).map(TransactionEntity::toDomain)

    /** Saves a transaction; returns the saved entity (new id generated when empty). */
    suspend fun save(t: Transaction): Transaction {
        val now = System.currentTimeMillis() / 1000
        val saved = if (t.id.isEmpty()) {
            t.copy(id = Ids.new(), createdAtEpochSecond = now, updatedAtEpochSecond = now)
        } else {
            t.copy(updatedAtEpochSecond = now)
        }
        dao.upsert(saved.toEntity())
        return saved
    }

    suspend fun delete(id: String) = dao.deleteById(id)
    suspend fun restore(t: Transaction) = dao.upsert(t.toEntity()) // used by undo

    suspend fun setStatuses(ids: List<String>, status: TransactionStatus) {
        if (ids.isEmpty()) return
        dao.setStatuses(ids, status.name, System.currentTimeMillis() / 1000)
    }

    /** Bulk insert used by CSV import & backup restore; skips ids already present. */
    suspend fun insertAllIgnoreDuplicates(list: List<Transaction>): Int = db.withTransaction {
        val inserted = dao.insertAll(list.map(Transaction::toEntity))
        inserted.count { it > 0 }
    }

    suspend fun existingOccurrences(ruleId: String, occDay: Long): List<Transaction> =
        dao.existingOccurrences(ruleId, occDay).map(TransactionEntity::toDomain)

    suspend fun deletePlannedOfRule(ruleId: String) = dao.deletePlannedOfRule(ruleId)

    fun observeTags(): Flow<List<String>> = dao.observeTagSources().map { rows ->
        rows.flatMap { it.split("|") }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }
}

@Singleton
class RecurrenceRepository @Inject constructor(private val dao: RecurrenceRuleDao) {
    fun observeAll(): Flow<List<RecurrenceRule>> = dao.observeAll().map { it.map(RecurrenceRuleEntity::toDomain) }
    suspend fun getActive(): List<RecurrenceRule> = dao.getActive().map(RecurrenceRuleEntity::toDomain)
    suspend fun getAll(): List<RecurrenceRule> = dao.getAll().map(RecurrenceRuleEntity::toDomain)
    suspend fun getById(id: String): RecurrenceRule? = dao.getById(id)?.toDomain()
    suspend fun save(rule: RecurrenceRule): RecurrenceRule {
        val saved = if (rule.id.isEmpty()) rule.copy(id = Ids.new()) else rule
        dao.upsert(saved.toEntity())
        return saved
    }

    suspend fun setActive(id: String, active: Boolean) {
        dao.getById(id)?.let { dao.upsert(it.copy(active = active)) }
    }

    suspend fun delete(id: String) = dao.deleteById(id)
    suspend fun update(rule: RecurrenceRule) = dao.upsert(rule.toEntity())
}

@Singleton
class BudgetRepository @Inject constructor(private val dao: BudgetDao) {
    fun observeAll(): Flow<List<Budget>> = dao.observeAll().map { it.map(BudgetEntity::toDomain) }
    suspend fun getAll(): List<Budget> = dao.getAll().map(BudgetEntity::toDomain)
    suspend fun save(b: Budget): Budget {
        val saved = if (b.id.isEmpty()) b.copy(id = Ids.new()) else b
        dao.upsert(saved.toEntity())
        return saved
    }

    suspend fun delete(id: String) = dao.deleteById(id)
}

@Singleton
class GoalRepository @Inject constructor(private val dao: GoalDao) {
    fun observeGoals(): Flow<List<Goal>> = dao.observeGoals().map { it.map(GoalEntity::toDomain) }
    fun observeContributions(goalId: String): Flow<List<GoalContribution>> =
        dao.observeContributions(goalId).map { it.map(GoalContributionEntity::toDomain) }

    suspend fun getAllContributions(): List<GoalContribution> = dao.getAllContributions().map(GoalContributionEntity::toDomain)
    suspend fun getGoalById(id: String): Goal? = dao.getGoalById(id)?.toDomain()

    suspend fun save(g: Goal): Goal {
        val saved = if (g.id.isEmpty()) g.copy(id = Ids.new(), createdAtEpochDay = LocalDate.now().toEpochDay()) else g
        dao.upsertGoal(saved.toEntity())
        return saved
    }

    suspend fun delete(id: String) {
        dao.deleteContributionsOf(id)
        dao.deleteGoalById(id)
    }

    suspend fun addContribution(c: GoalContribution): GoalContribution {
        val saved = if (c.id.isEmpty()) c.copy(id = Ids.new()) else c
        dao.upsertContribution(saved.toEntity())
        return saved
    }

    suspend fun removeContribution(id: String) = dao.deleteContributionById(id)
    suspend fun updateGoalStatus(goal: Goal, status: GoalStatus) = dao.upsertGoal(goal.copy(status = status).toEntity())
}
