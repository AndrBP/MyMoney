package com.mymoney.app.domain.rules

import com.mymoney.app.domain.model.Budget
import com.mymoney.app.domain.model.Money
import com.mymoney.app.domain.model.Transaction
import com.mymoney.app.domain.model.TransactionStatus
import com.mymoney.app.domain.model.TransactionType
import java.time.LocalDate

/** Budget progress computation. Transfers are always ignored (rule 5.8.18). */
data class BudgetProgress(
    val budget: Budget,
    val spent: Money,
    val limit: Money,
) {
    val remaining: Money get() = limit - spent
    val fractionUsed: Float get() = if (limit.cents <= 0L) 0f else (spent.cents.toFloat() / limit.cents.toFloat())
    val percentUsed: Int get() = if (limit.cents <= 0L) 0 else ((spent.cents * 100) / limit.cents).toInt()
    val isOverspent: Boolean get() = spent > limit
    val isNearLimit: Boolean get() = !isOverspent && spent.cents * 100 >= limit.cents * 90
}

object BudgetCalculator {

    /** Start of the current budget window for [budget], anchored on its start date. */
    fun currentWindowStart(budget: Budget, todayEpochDay: Long): Long {
        val start = LocalDate.ofEpochDay(budget.startDateEpochDay)
        val today = LocalDate.ofEpochDay(todayEpochDay)
        return when (budget.period) {
            com.mymoney.app.domain.model.BudgetPeriod.WEEKLY -> {
                val days = (today.toEpochDay() - start.toEpochDay()).coerceAtLeast(0)
                start.plusDays((days / 7) * 7).toEpochDay()
            }
            com.mymoney.app.domain.model.BudgetPeriod.MONTHLY -> {
                // Recurring monthly window: same month as today (rule 5.8.19).
                LocalDate.of(today.year, today.monthValue, 1).coerceAtLeast(start).toEpochDay()
            }
        }
    }

    fun currentWindowEnd(budget: Budget, windowStart: Long): Long {
        val s = LocalDate.ofEpochDay(windowStart)
        return when (budget.period) {
            com.mymoney.app.domain.model.BudgetPeriod.WEEKLY -> s.plusDays(6).toEpochDay()
            com.mymoney.app.domain.model.BudgetPeriod.MONTHLY -> s.withDayOfMonth(s.lengthOfMonth()).toEpochDay()
        }
    }

    /**
     * Computes progress for a budget given all transactions in the window.
     * Only EXPENSE transactions whose category matches (or any expense when
     * the budget has no category) count. PLANNED expenses count only when
     * [Budget.includePlanned] is true (rule 5.8.17).
     */
    fun progress(budget: Budget, transactionsInWindow: List<Transaction>): BudgetProgress {
        var spent = 0L
        for (t in transactionsInWindow) {
            if (t.type != TransactionType.EXPENSE) continue // transfers & incomes excluded
            if (budget.categoryId != null && t.categoryId != budget.categoryId) continue
            when (t.status) {
                TransactionStatus.CONFIRMED, TransactionStatus.RECONCILED -> spent += t.amount.cents
                TransactionStatus.PLANNED -> if (budget.includePlanned) spent += t.amount.cents
            }
        }
        return BudgetProgress(budget, Money(spent), budget.limitAmount)
    }
}
