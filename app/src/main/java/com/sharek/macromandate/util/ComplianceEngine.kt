package com.sharek.macromandate.util

import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.viewmodel.ComplianceStatus
import java.util.Calendar
import kotlin.math.abs

/**
 * Pure, side-effect-free compliance scoring rules shared by the ViewModel,
 * the surveillance service, and unit tests.
 */
object ComplianceEngine {

    /**
     * Compliance score (0..100) for a set of meals against a daily calorie target.
     * Score starts at 100 and is reduced by the average absolute daily deviation
     * from the target. An empty log is considered a perfect score.
     */
    fun calculateScore(
        meals: List<MealEntry>,
        dailyTarget: Int,
        now: Long = System.currentTimeMillis()
    ): Int {
        if (meals.isEmpty() || dailyTarget <= 0) return 100

        val todayKey = dayKey(now)
        val totalsByDay = meals.groupBy { dayKey(it.timestamp) }
            .mapValues { it.value.sumOf { meal -> meal.calories } }

        var totalDeviation = 0f
        val daysEvaluated = totalsByDay.size.coerceAtLeast(1)

        totalsByDay.forEach { (day, dailyTotal) ->
            val difference = dailyTotal - dailyTarget
            // Today is still in progress, so only exceeding the target counts
            // against you. Scoring the shortfall would mean a single breakfast
            // reads as an 80% deviation and drops the user straight into CRISIS
            // — which replaces the whole app with the leniency screen.
            val deviation = if (day == todayKey) {
                maxOf(0, difference).toFloat() / dailyTarget
            } else {
                abs(difference).toFloat() / dailyTarget
            }
            totalDeviation += deviation
        }

        val averageDeviationPercent = (totalDeviation / daysEvaluated) * 100
        return (100 - averageDeviationPercent.toInt()).coerceIn(0, 100)
    }

    fun statusFor(score: Int): ComplianceStatus = when {
        score >= 90 -> ComplianceStatus.EXEMPLARY
        score >= 70 -> ComplianceStatus.ACCEPTABLE
        score >= 40 -> ComplianceStatus.SUBVERSIVE
        else -> ComplianceStatus.CRISIS
    }

    /** Year-qualified so days exactly a year apart cannot collide. */
    private fun dayKey(timestamp: Long): Int =
        Calendar.getInstance().apply { timeInMillis = timestamp }
            .let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
}
