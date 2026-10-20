package com.mymoney.app.domain.model

/** Money is always represented as Long cents — never float/double. */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(cents + other.cents)
    operator fun minus(other: Money) = Money(cents - other.cents)
    operator fun times(factor: Int) = Money(cents * factor)
    operator fun unaryMinus() = Money(-cents)
    val isPositive get() = cents > 0
    val isNegative get() = cents < 0
    val isZero get() = cents == 0L
    val absolute get() = Money(if (cents < 0) -cents else cents)
    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)
    override fun toString() = "$cents cents"

    companion object {
        val ZERO = Money(0L)
        fun fromCents(c: Long) = Money(c)
    }
}

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

enum class TransactionStatus { PLANNED, CONFIRMED, RECONCILED }

enum class AccountType { CASH, BANK, CREDIT_CARD, DIGITAL_WALLET, SAVINGS, INVESTMENT, OTHER }

enum class CategoryKind { INCOME, EXPENSE, BOTH }

enum class BudgetPeriod { MONTHLY, WEEKLY }

enum class GoalStatus { ACTIVE, COMPLETED, ARCHIVED }

enum class RecurrenceFrequency {
    DAILY, WEEKLY, BIWEEKLY, MONTHLY, BIMONTHLY, QUARTERLY, SEMIANNUAL, ANNUAL, CUSTOM;

    /** Day-anchored frequencies. */
    fun stepDays(): Int? = when (this) {
        DAILY -> 1
        WEEKLY -> 7
        BIWEEKLY -> 14
        else -> null
    }

    /** Month-anchored frequencies. */
    fun stepMonths(): Int? = when (this) {
        MONTHLY -> 1
        BIMONTHLY -> 2
        QUARTERLY -> 3
        SEMIANNUAL -> 6
        ANNUAL -> 12
        else -> null
    }
}

data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val initialBalance: Money,
    val colorHex: String,
    val iconKey: String,
    val currencyCode: String,
    val archived: Boolean,
    val createdAtEpochDay: Long,
    val notes: String,
)

data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val colorHex: String,
    val iconKey: String,
    val isDefault: Boolean,
    val archived: Boolean,
)

data class Transaction(
    val id: String,
    val type: TransactionType,
    val amount: Money,
    val dateEpochDay: Long,
    val timeText: String?,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
    val description: String,
    val notes: String,
    val tags: List<String>,
    val status: TransactionStatus,
    val recurrenceId: String?,
    val occurrenceDateEpochDay: Long?,
    val createdAtEpochSecond: Long,
    val updatedAtEpochSecond: Long,
)

data class RecurrenceRule(
    val id: String,
    val type: TransactionType,
    val description: String,
    val amount: Money,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
    val frequency: RecurrenceFrequency,
    val customIntervalDays: Int,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long?,
    val dueDay: Int,
    val active: Boolean,
    val autoGenerate: Boolean,
    val lastGeneratedOccurrenceEpochDay: Long?,
    val ignoredOccurrences: List<Long>,
)

data class Budget(
    val id: String,
    val name: String,
    val categoryId: String?,
    val period: BudgetPeriod,
    val limitAmount: Money,
    val startDateEpochDay: Long,
    val active: Boolean,
    val includePlanned: Boolean,
    val colorHex: String,
)

data class Goal(
    val id: String,
    val name: String,
    val targetAmount: Money,
    val deadlineEpochDay: Long?,
    val colorHex: String,
    val status: GoalStatus,
    val notes: String,
    val createdAtEpochDay: Long,
)

data class GoalContribution(
    val id: String,
    val goalId: String,
    val amount: Money,
    val dateEpochDay: Long,
    val note: String,
)
