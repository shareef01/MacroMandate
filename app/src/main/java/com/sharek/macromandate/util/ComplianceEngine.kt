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
    fun calculateScore(meals: List<MealEntry>, dailyTarget: Int): Int {
        if (meals.isEmpty() || dailyTarget <= 0) return 100

        val totalsByDay = meals.groupBy { dayOfYear(it.timestamp) }
            .mapValues { it.value.sumOf { meal -> meal.calories } }

        var totalDeviation = 0f
        val daysEvaluated = totalsByDay.size.coerceAtLeast(1)

        totalsByDay.values.forEach { dailyTotal ->
            val deviation = abs(dailyTotal - dailyTarget).toFloat() / dailyTarget
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

    private fun dayOfYear(timestamp: Long): Int =
        Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.DAY_OF_YEAR)
}
