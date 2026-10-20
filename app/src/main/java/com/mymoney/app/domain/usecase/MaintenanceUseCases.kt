package com.mymoney.app.domain.usecase

import com.mymoney.app.data.local.dao.AccountDao
import com.mymoney.app.data.local.dao.CategoryDao
import com.mymoney.app.data.local.dao.RecurrenceRuleDao
import com.mymoney.app.data.local.dao.TransactionDao
import com.mymoney.app.data.local.entity.RecurrenceRuleEntity
import com.mymoney.app.data.local.entity.TransactionEntity
import com.mymoney.app.data.repository.toDomain
import com.mymoney.app.data.settings.SettingsRepository
import com.mymoney.app.domain.rules.RecurrenceEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Materializes PLANNED transactions from active recurrence rules when the
 * app opens (rule 5.7.21). Duplicate protection: an occurrence is generated
 * at most once per rule+date, even if generation runs many times a day.
 */
@Singleton
class GeneratePlannedTransactionsUseCase @Inject constructor(
    private val rules: RecurrenceRuleDao,
    private val transactions: TransactionDao,
    private val settings: SettingsRepository,
) {
    /** Returns number of newly created planned transactions. */
    suspend operator fun invoke(todayEpochDay: Long): Int = withContext(Dispatchers.IO) {
        if (!settings.current().autoGeneratePlanned) return@withContext 0
        var created = 0
        for (rule in rules.getActive()) {
            val domain = rule.toDomain()
            val lastDone = domain.lastGeneratedOccurrenceEpochDay ?: (domain.startDateEpochDay - 1)
            val horizon = minOf(domain.endDateEpochDay ?: Long.MAX_VALUE, todayEpochDay + 31)
            val occurrences = RecurrenceEngine.occurrencesBetween(
                startDateEpochDay = maxOf(domain.startDateEpochDay, lastDone + 1),
                endDateEpochDay = domain.endDateEpochDay,
                frequency = domain.frequency,
                customIntervalDays = domain.customIntervalDays,
                dueDay = domain.dueDay,
                fromEpochDay = maxOf(domain.startDateEpochDay, lastDone + 1),
                untilEpochDay = horizon,
                ignored = domain.ignoredOccurrences.toSet(),
            )
            for (occ in occurrences) {
                val exists = transactions.existingOccurrences(rule.id, occ).isNotEmpty()
                if (!exists) {
                    val now = System.currentTimeMillis() / 1000
                    transactions.upsert(
                        TransactionEntity(
                            id = "${rule.id}_$occ",
                            type = rule.type,
                            amountCents = rule.amountCents,
                            dateEpochDay = occ,
                            timeText = null,
                            accountId = rule.accountId,
                            toAccountId = rule.toAccountId,
                            categoryId = rule.categoryId,
                            description = rule.description,
                            notes = "",
                            tagsCsv = "",
                            status = "PLANNED",
                            recurrenceId = rule.id,
                            occurrenceDateEpochDay = occ,
                            createdAtEpochSecond = now,
                            updatedAtEpochSecond = now,
                        )
                    )
                    created++
                }
                // advance watermark so we never rescan old occurrences
                rules.upsert(rule.copy(lastGeneratedOccurrenceEpochDay = occ))
            }
        }
        created
    }
}

/** Ignores one specific future occurrence of a rule (rule 5.7.24). */
@Singleton
class IgnoreOccurrenceUseCase @Inject constructor(
    private val rules: RecurrenceRuleDao,
    private val transactions: TransactionDao,
) {
    suspend operator fun invoke(recurrenceId: String, occurrenceEpochDay: Long) {
        val rule = rules.getById(recurrenceId) ?: return
        val ignored = (if (rule.ignoredCsv.isBlank()) emptyList() else rule.ignoredCsv.split(",").mapNotNull { it.toLongOrNull() }) + occurrenceEpochDay
        rules.upsert(rule.copy(ignoredCsv = ignored.distinct().joinToString(",")))
        // remove the planned transaction for that occurrence, if already generated
        transactions.existingOccurrences(recurrenceId, occurrenceEpochDay)
            .filter { it.status == "PLANNED" }
            .forEach { transactions.deleteById(it.id) }
    }
}

/**
 * When an account is deleted and cascade was NOT chosen, its transactions can
 * be reassigned to another account instead (dependency-safe deletion).
 */
@Singleton
class DeleteAccountUseCase @Inject constructor(
    private val accounts: AccountDao,
    private val transactions: TransactionDao,
) {
    sealed interface Result {
        data object Deleted : Result
        data class BlockedHasTransactions(val count: Int) : Result
        data object NotFound : Result
    }

    suspend operator fun invoke(accountId: String, deleteTransactionsToo: Boolean, reassignToAccountId: String?): Result =
        withContext(Dispatchers.IO) {
            val acc = accounts.getById(accountId) ?: return@withContext Result.NotFound
            val count = accounts.transactionCount(accountId)
            if (count > 0) {
                when {
                    deleteTransactionsToo -> accounts.deleteTransactionsOf(accountId)
                    reassignToAccountId != null && reassignToAccountId != accountId -> {
                        transactions.reassignAccount(accountId, reassignToAccountId)
                        transactions.reassignToAccount(accountId, reassignToAccountId)
                    }
                    else -> return@withContext Result.BlockedHasTransactions(count)
                }
            }
            accounts.delete(acc)
            Result.Deleted
        }
}

/**
 * When a category is deleted, its transactions are reclassified into a
 * fallback category (rules 5.4.12–5.4.14).
 */
@Singleton
class DeleteCategoryUseCase @Inject constructor(
    private val categories: CategoryDao,
) {
    sealed interface Result {
        data object Deleted : Result
        data object BlockedEssential : Result
        data object NotFound : Result
    }

    suspend operator fun invoke(categoryId: String, reassignToCategoryId: String?): Result =
        withContext(Dispatchers.IO) {
            val cat = categories.getById(categoryId) ?: return@withContext Result.NotFound
            val essentialFallback = categoryId == com.mymoney.app.data.repository.DefaultCategories.FALLBACK_EXPENSE_ID ||
                categoryId == com.mymoney.app.data.repository.DefaultCategories.FALLBACK_INCOME_ID
            if (essentialFallback) return@withContext Result.BlockedEssential
            if (reassignToCategoryId != null && reassignToCategoryId != categoryId) {
                categories.reassignTransactions(categoryId, reassignToCategoryId)
                categories.reassignRecurrences(categoryId, reassignToCategoryId)
            } else {
                categories.reassignTransactions(categoryId, fallbackFor(cat.kind))
                categories.reassignRecurrences(categoryId, fallbackFor(cat.kind))
            }
            categories.clearBudgetRefs(categoryId)
            categories.delete(cat)
            Result.Deleted
        }

    private fun fallbackFor(kind: String): String = when (kind) {
        "INCOME" -> com.mymoney.app.data.repository.DefaultCategories.FALLBACK_INCOME_ID
        else -> com.mymoney.app.data.repository.DefaultCategories.FALLBACK_EXPENSE_ID
    }
}


