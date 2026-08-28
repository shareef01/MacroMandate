package com.sharek.macromandate.util

import com.sharek.macromandate.data.repository.MealRepository
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.viewmodel.ComplianceStatus
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

object DossierReportGenerator {

    fun generateWeeklyMarkdown(
        meals: List<MealEntry>,
        calorieTarget: Int,
        complianceScore: Int,
        complianceStatus: ComplianceStatus,
        generatedTimestamp: Long = System.currentTimeMillis()
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val shortDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // The window must match the one the meals were selected with
        // (MealRepository.WEEK_LENGTH_DAYS, inclusive of today). Subtracting a
        // flat 7 days described a window one day wider than the data covered.
        val windowStart = Calendar.getInstance().apply {
            timeInMillis = generatedTimestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(MealRepository.WEEK_LENGTH_DAYS - 1))
        }.timeInMillis
        val startDateStr = shortDateFormat.format(Date(windowStart))
        val endDateStr = shortDateFormat.format(Date(generatedTimestamp))

        // Averaging over a fixed 7 regardless of how much history exists made a
        // two-day-old install report a "daily average" a fifth of what the person
        // actually ate, then a large "deviation" from target on top of it. Divide
        // by the days that actually have entries, and say how many that was.
        val daysWithEntries = meals
            .map { dayKey(it.timestamp) }
            .distinct()
            .size
            .coerceAtLeast(1)

        val totalMeals = meals.size
        val totalCalories = meals.sumOf { it.calories }
        val totalProtein = meals.sumOf { it.proteinGrams.toDouble() }
        val totalCarbs = meals.sumOf { it.carbsGrams.toDouble() }
        val totalFat = meals.sumOf { it.fatGrams.toDouble() }
        val liquidMealsCount = meals.count { it.isLiquid }

        val avgDailyCalories = (totalCalories / daysWithEntries.toDouble()).roundToInt()
        val avgDailyProtein = (totalProtein / daysWithEntries).roundToInt()
        val avgDailyCarbs = (totalCarbs / daysWithEntries).roundToInt()
        val avgDailyFat = (totalFat / daysWithEntries).roundToInt()

        val deviation = avgDailyCalories - calorieTarget
        val deviationPercent = if (calorieTarget > 0) ((abs(deviation).toDouble() / calorieTarget) * 100).roundToInt() else 0

        val nightRefuelingCount = meals.count { it.isNightRefueling }

        val liquidRatio = if (totalMeals > 0) ((liquidMealsCount.toDouble() / totalMeals) * 100).roundToInt() else 0

        val directive = when (complianceStatus) {
            ComplianceStatus.EXEMPLARY -> "COMMENDATION: Subject demonstrates strict metabolic discipline. Caloric intake conforms to central committee directives."
            ComplianceStatus.ACCEPTABLE -> "OBSERVATION: Subject remains within acceptable operational boundaries. Minor variance detected; surveillance continues."
            ComplianceStatus.SUBVERSIVE -> "WARNING: Unsanctioned nutritional deviations detected. Caloric intake exceeds mandated quotas. Corrective adherence required."
            ComplianceStatus.CRISIS -> "CRITICAL ALERT: Logged intake is far from the configured target across this window."
        }

        val sb = StringBuilder()
        sb.append("=====================================================\n")
        sb.append("   MACROMANDATE // WEEKLY SURVEILLANCE DOSSIER       \n")
        sb.append("=====================================================\n")
        sb.append("SURVEILLANCE WINDOW : $startDateStr -> $endDateStr\n")
        sb.append("REPORT GENERATED    : ${dateFormat.format(Date(generatedTimestamp))}\n")
        sb.append("STATUS VERDICT      : ${complianceStatus.name} ($complianceScore/100)\n\n")

        sb.append("--- [1] COMPLIANCE & METABOLIC TARGETS ---\n")
        sb.append("DAILY CALORIE TARGET : $calorieTarget kcal\n")
        sb.append("DAYS WITH ENTRIES    : $daysWithEntries of ${MealRepository.WEEK_LENGTH_DAYS}\n")
        sb.append("AVG DAILY CONSUMED   : $avgDailyCalories kcal (averaged over days with entries; ")
        if (deviation >= 0) sb.append("+$deviation kcal, ") else sb.append("$deviation kcal, ")
        sb.append("$deviationPercent% deviation)\n")
        sb.append("TOTAL 7-DAY INTAKE   : $totalCalories kcal across $totalMeals meals\n\n")

        sb.append("--- [2] MACRONUTRIENT TOTALS & DAILY AVERAGES ---\n")
        sb.append("PROTEIN : ${totalProtein.roundToInt()}g total | ~${avgDailyProtein}g/day\n")
        sb.append("CARBS   : ${totalCarbs.roundToInt()}g total | ~${avgDailyCarbs}g/day\n")
        sb.append("FAT     : ${totalFat.roundToInt()}g total | ~${avgDailyFat}g/day\n")
        sb.append("LIQUIDS : $liquidMealsCount liquid events ($liquidRatio% of total entries)\n\n")

        sb.append("--- [3] TIMING NOTES ---\n")
        sb.append("MEALS LOGGED 23:00-05:00     : $nightRefuelingCount\n\n")

        if (nightRefuelingCount > 0) {
            sb.append("LATE-NIGHT ENTRIES:\n")
            meals.filter { it.isNightRefueling }.forEach { entry ->
                sb.append(" - [${dateFormat.format(Date(entry.timestamp))}] ${entry.foodName} (${entry.calories} kcal)\n")
            }
            sb.append("\n")
        }

        sb.append("--- [4] COMMAND DIRECTIVE ---\n")
        sb.append("$directive\n")
        sb.append("\n")
        // Anything derived from a photo is a model estimate. Saying so once, in
        // the artefact people keep and share, costs a line and prevents the
        // report reading like a measured record.
        sb.append("NOTE: Values from photo analysis are AI estimates, not measurements.\n")
        sb.append("=====================================================\n")

        return sb.toString()
    }

    /** Year-qualified day identity, matching [ComplianceEngine]'s grouping. */
    private fun dayKey(timestamp: Long): Int =
        Calendar.getInstance().apply { timeInMillis = timestamp }
            .let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
}
