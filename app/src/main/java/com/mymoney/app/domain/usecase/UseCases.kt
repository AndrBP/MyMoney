package com.mymoney.app.domain.usecase

import com.mymoney.app.data.repository.AccountRepository
import com.mymoney.app.data.repository.BudgetRepository
import com.mymoney.app.data.repository.CategoryRepository
import com.mymoney.app.data.repository.GoalRepository
import com.mymoney.app.data.repository.RecurrenceRepository
import com.mymoney.app.data.repository.TransactionRepository
import com.mymoney.app.data.settings.AppSettings
import com.mymoney.app.data.settings.SettingsRepository
import com.mymoney.app.domain.model.Account
import com.mymoney.app.domain.model.Category
import com.mymoney.app.domain.model.Money
import com.mymoney.app.domain.model.Transaction
import com.mymoney.app.domain.model.TransactionStatus
import com.mymoney.app.domain.model.TransactionType
import com.mymoney.app.domain.rules.BalanceCalculator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Dashboard aggregates: balances, month totals, per-account balances. */
data class DashboardSnapshot(
    val totalBalance: Money,
    val monthIncome: Money,
    val monthExpense: Money,
    val monthResult: Money,
    val accountBalances: List<Pair<Account, Money>>,
)

class GetDashboardUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
) {
    operator fun invoke(monthStartDay: Long, monthEndDay: Long): Flow<DashboardSnapshot> =
        combine(accounts.observeAll(), transactions.pagedRaw()) { accList, txList ->
            val active = accList.filter { !it.archived }
            val monthTx = txList.filter { it.dateEpochDay in monthStartDay..monthEndDay }
            DashboardSnapshot(
                totalBalance = BalanceCalculator.totalBalance(active, txList),
                monthIncome = BalanceCalculator.periodIncome(monthTx),
                monthExpense = BalanceCalculator.periodExpense(monthTx),
                monthResult = BalanceCalculator.periodResult(monthTx),
                accountBalances = active.map { it to BalanceCalculator.accountBalance(it, txList) },
            )
        }

    private fun TransactionRepository.pagedRaw(): Flow<List<Transaction>> =
        paged(null, null, null, null, null, null, null, "DATE_DESC", 100_000, 0)
}

/** Validates a transaction draft against the mandatory financial rules (5.5.32–5.5.35). */
sealed interface TxValidation {
    data object Valid : TxValidation
    data class Invalid(val field: String, val messageKey: String) : TxValidation
}

class ValidateTransactionUseCase @Inject constructor() {
    fun validate(
        type: TransactionType,
        amountCents: Long?,
        accountId: String?,
        toAccountId: String?,
        categoryId: String?,
    ): TxValidation {
        if (amountCents == null || amountCents <= 0L) return TxValidation.Invalid("amount", "validate_amount_positive")
        if (accountId.isNullOrBlank()) return TxValidation.Invalid("account", "validate_account_required")
        if (type != TransactionType.TRANSFER && categoryId.isNullOrBlank()) {
            return TxValidation.Invalid("category", "validate_category_required")
        }
        if (type == TransactionType.TRANSFER) {
            if (toAccountId.isNullOrBlank()) return TxValidation.Invalid("toAccount", "validate_account_required")
            if (toAccountId == accountId) return TxValidation.Invalid("toAccount", "validate_accounts_different")
        }
        return TxValidation.Valid
    }
}

/** Confirms planned transactions individually or in batch (rules 5.7.22/5.7.23). */
class ConfirmPlannedUseCase @Inject constructor(private val transactions: TransactionRepository) {
    suspend operator fun invoke(ids: List<String>) = transactions.setStatuses(ids, TransactionStatus.CONFIRMED)
}

/** Budgets with live progress for the current period window. */
data class BudgetWithProgress(
    val budget: com.mymoney.app.domain.model.Budget,
    val progress: com.mymoney.app.domain.rules.BudgetProgress,
    val category: Category?,
)

class ObserveBudgetsWithProgressUseCase @Inject constructor(
    private val budgets: BudgetRepository,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
) {
    operator fun invoke(todayEpochDay: Long): Flow<List<BudgetWithProgress>> =
        combine(budgets.observeAll(), categories.observeAll(), transactions.paged(
            null, null, null, null, null, null, null, "DATE_DESC", 100_000, 0
        )) { budgetList, catList, txList ->
            budgetList.map { b ->
                val start = com.mymoney.app.domain.rules.BudgetCalculator.currentWindowStart(b, todayEpochDay)
                val end = com.mymoney.app.domain.rules.BudgetCalculator.currentWindowEnd(b, start)
                val inWindow = txList.filter { it.dateEpochDay in start..end }
                BudgetWithProgress(
                    budget = b,
                    progress = com.mymoney.app.domain.rules.BudgetCalculator.progress(b, inWindow),
                    category = catList.firstOrNull { it.id == b.categoryId },
                )
            }
        }
}

/** Goals with saved amounts and pace estimates. */
data class GoalWithProgress(
    val goal: com.mymoney.app.domain.model.Goal,
    val progress: com.mymoney.app.domain.rules.GoalProgress,
    val estimatedCompletionEpochDay: Long?,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ObserveGoalsUseCase @Inject constructor(
    private val goals: GoalRepository,
) {
    operator fun invoke(todayEpochDay: Long): Flow<List<GoalWithProgress>> =
        flatMapLatest(goals.observeGoals()) { list ->
            // Re-emit goal list joined with the contributions snapshot.
            kotlinx.coroutines.flow.flow {
                val allContribs = goals.getAllContributions()
                emit(
                    list.map { g ->
                        val contribs = allContribs.filter { it.goalId == g.id }
                        GoalWithProgress(
                            goal = g,
                            progress = com.mymoney.app.domain.rules.GoalCalculator.progress(g, contribs),
                            estimatedCompletionEpochDay =
                                com.mymoney.app.domain.rules.GoalCalculator.estimatedCompletionEpochDay(g, contribs, todayEpochDay),
                        )
                    }
                )
            }
        }
}

/** Registers a contribution and auto-completes the goal at 100% (rule 5.9.16). */
class AddGoalContributionUseCase @Inject constructor(private val goals: GoalRepository) {
    suspend operator fun invoke(goalId: String, amountCents: Long, dateEpochDay: Long): Boolean {
        if (amountCents <= 0L) return false
        val goal = goals.getGoalById(goalId) ?: return false
        goals.addContribution(
            com.mymoney.app.domain.model.GoalContribution(
                id = "", goalId = goalId, amount = Money(amountCents), dateEpochDay = dateEpochDay, note = "",
            )
        )
        val contribs = goals.getAllContributions().filter { it.goalId == goalId }
        val newStatus = com.mymoney.app.domain.rules.GoalCalculator.statusAfterContribution(goal, contribs)
        if (newStatus != goal.status) goals.updateGoalStatus(goal, newStatus)
        return true
    }
}

/** Undo the most recent contribution of a goal (rule 5.9.13). */
class UndoGoalContributionUseCase @Inject constructor(private val goals: GoalRepository) {
    suspend operator fun invoke(goalId: String): Boolean {
        val contribs = goals.getAllContributions().filter { it.goalId == goalId }
        val last = contribs.maxByOrNull { it.dateEpochDay } ?: return false
        goals.removeContribution(last.id)
        val goal = goals.getGoalById(goalId) ?: return true
        val remaining = contribs.filter { it.id != last.id }
        val newStatus = com.mymoney.app.domain.rules.GoalCalculator.statusAfterContribution(goal, remaining)
        if (newStatus != goal.status) goals.updateGoalStatus(goal, newStatus)
        return true
    }
}

/** App-wide settings flow used by the UI shell (theme, hide balances etc.). */
class ObserveSettingsUseCase @Inject constructor(private val settings: SettingsRepository) {
    operator fun invoke(): Flow<AppSettings> = settings.settingsFlow
}
