package com.mymoney.app.domain.rules

import com.mymoney.app.domain.model.Account
import com.mymoney.app.domain.model.Money
import com.mymoney.app.domain.model.Transaction
import com.mymoney.app.domain.model.TransactionStatus
import com.mymoney.app.domain.model.TransactionType

/**
 * Core financial rules of MyMoney. Pure Kotlin, no Android imports, fully
 * unit tested. All amounts are Long cents.
 */
object BalanceCalculator {

    /**
     * Account balance = initial + incomes - expenses - transfersOut + transfersIn.
     * PLANNED transactions do not affect real balances yet; CONFIRMED and
     * RECONCILED do.
     */
    fun accountBalance(account: Account, transactions: List<Transaction>): Money {
        var balance = account.initialBalance
        for (t in transactions) {
            if (t.status == TransactionStatus.PLANNED) continue
            when (t.type) {
                TransactionType.INCOME -> if (t.accountId == account.id) balance += t.amount
                TransactionType.EXPENSE -> if (t.accountId == account.id) balance -= t.amount
                TransactionType.TRANSFER -> {
                    if (t.accountId == account.id) balance -= t.amount
                    if (t.toAccountId == account.id) balance += t.amount
                }
            }
        }
        return balance
    }

    fun totalBalance(accounts: List<Account>, transactions: List<Transaction>): Money {
        val byId = accounts.associateBy { it.id }
        var total = Money.ZERO
        for (a in accounts) total += accountBalance(a.copy(), transactions.filterByAccount(a.id, byId))
        return total
    }

    private fun List<Transaction>.filterByAccount(accountId: String, all: Map<String, Account>): List<Transaction> =
        filter { it.accountId == accountId || it.toAccountId == accountId }

    /**
     * Period result = incomes - expenses. Transfers NEVER count towards the
     * result (rules 5.6.8 / 5.6.9). Only non-PLANNED statuses count.
     */
    fun periodResult(transactions: List<Transaction>): Money {
        var result = Money.ZERO
        for (t in transactions) {
            if (t.status == TransactionStatus.PLANNED) continue
            when (t.type) {
                TransactionType.INCOME -> result += t.amount
                TransactionType.EXPENSE -> result -= t.amount
                TransactionType.TRANSFER -> Unit // excluded on purpose
            }
        }
        return result
    }

    fun periodIncome(transactions: List<Transaction>): Money =
        sumByType(transactions, TransactionType.INCOME)

    fun periodExpense(transactions: List<Transaction>): Money =
        sumByType(transactions, TransactionType.EXPENSE)

    private fun sumByType(transactions: List<Transaction>, type: TransactionType): Money {
        var s = 0L
        for (t in transactions) {
            if (t.status == TransactionStatus.PLANNED) continue
            if (t.type == type) s += t.amount.cents
        }
        return Money(s)
    }
}
