package com.mymoney.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val initialBalanceCents: Long,
    val colorHex: String,
    val iconKey: String,
    val currencyCode: String,
    val archived: Boolean,
    val createdAtEpochDay: Long,
    val notes: String,
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["kind"])],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val colorHex: String,
    val iconKey: String,
    val isDefault: Boolean,
    val archived: Boolean,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("dateEpochDay"),
        Index("accountId"),
        Index("toAccountId"),
        Index("categoryId"),
        Index("type"),
        Index("status"),
        Index("recurrenceId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val amountCents: Long,
    val dateEpochDay: Long,
    val timeText: String?,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
    val description: String,
    val notes: String,
    val tagsCsv: String,
    val status: String,
    val recurrenceId: String?,
    val occurrenceDateEpochDay: Long?,
    val createdAtEpochSecond: Long,
    val updatedAtEpochSecond: Long,
)

@Entity(
    tableName = "recurrence_rules",
    indices = [Index("active"), Index("accountId")],
)
data class RecurrenceRuleEntity(
    @PrimaryKey val id: String,
    val type: String,
    val description: String,
    val amountCents: Long,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
    val frequency: String,
    val customIntervalDays: Int,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long?,
    val dueDay: Int,
    val active: Boolean,
    val autoGenerate: Boolean,
    val lastGeneratedOccurrenceEpochDay: Long?,
    val ignoredCsv: String,
)

@Entity(
    tableName = "budgets",
    indices = [Index("categoryId")],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String?,
    val period: String,
    val limitCents: Long,
    val startDateEpochDay: Long,
    val active: Boolean,
    val includePlanned: Boolean,
    val colorHex: String,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetCents: Long,
    val deadlineEpochDay: Long?,
    val colorHex: String,
    val status: String,
    val notes: String,
    val createdAtEpochDay: Long,
)

@Entity(
    tableName = "goal_contributions",
    indices = [Index("goalId")],
)
data class GoalContributionEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val amountCents: Long,
    val dateEpochDay: Long,
    val note: String,
)
