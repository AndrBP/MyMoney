package com.mymoney.app.domain.rules

import com.mymoney.app.domain.model.Goal
import com.mymoney.app.domain.model.GoalContribution
import com.mymoney.app.domain.model.GoalStatus
import com.mymoney.app.domain.model.Money


/** Goal progress + simple pace-based completion estimate (rules 5.9.14–5.9.16). */
data class GoalProgress(
    val goal: Goal,
    val saved: Money,
    val target: Money,
) {
    val percent: Int get() = if (target.cents <= 0L) 0 else ((saved.cents * 100) / target.cents).toInt().coerceAtMost(100)
    val fraction: Float get() = if (target.cents <= 0L) 0f else (saved.cents.toFloat() / target.cents.toFloat()).coerceAtMost(1f)
    val isComplete: Boolean get() = saved >= target
}

object GoalCalculator {

    fun savedAmount(contributions: List<GoalContribution>): Money {
        var s = 0L
        for (c in contributions) s += c.amount.cents
        return Money(s)
    }

    fun progress(goal: Goal, contributions: List<GoalContribution>): GoalProgress {
        val saved = savedAmount(contributions)
        return GoalProgress(goal, saved, goal.targetAmount)
    }

    /** A goal reaching 100% is automatically completed (rule 5.9.16). */
    fun statusAfterContribution(goal: Goal, contributions: List<GoalContribution>): GoalStatus {
        val p = progress(goal, contributions)
        return when {
            goal.status == GoalStatus.ARCHIVED -> GoalStatus.ARCHIVED
            p.isComplete -> GoalStatus.COMPLETED
            else -> GoalStatus.ACTIVE
        }
    }

    /** Estimated finish epoch-day based on contribution pace, or null. */
    fun estimatedCompletionEpochDay(goal: Goal, contributions: List<GoalContribution>, todayEpochDay: Long): Long? {
        val p = progress(goal, contributions)
        if (p.isComplete) return todayEpochDay
        if (contributions.isEmpty()) return null
        val first = contributions.minOf { it.dateEpochDay }
        val span = (todayEpochDay - first).coerceAtLeast(1)
        val rate = p.saved.cents.toDouble() / span
        if (rate <= 0.0) return null
        val remaining = goal.targetAmount.cents - p.saved.cents
        val days = Math.ceil(remaining / rate).toLong()
        return if (days > 3650) null else todayEpochDay + days
    }
}
