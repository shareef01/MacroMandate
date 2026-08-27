package com.sharek.macromandate.util

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

        val sevenDaysAgo = generatedTimestamp - 7L * 24 * 60 * 60 * 1000
        val startDateStr = shortDateFormat.format(Date(sevenDaysAgo))
        val endDateStr = shortDateFormat.format(Date(generatedTimestamp))

        val totalMeals = meals.size
        val totalCalories = meals.sumOf { it.calories }
        val totalProtein = meals.sumOf { it.proteinGrams.toDouble() }
        val totalCarbs = meals.sumOf { it.carbsGrams.toDouble() }
        val totalFat = meals.sumOf { it.fatGrams.toDouble() }
        val liquidMealsCount = meals.count { it.isLiquid }

        val avgDailyCalories = (totalCalories / 7.0).roundToInt()
        val avgDailyProtein = (totalProtein / 7.0).roundToInt()
        val avgDailyCarbs = (totalCarbs / 7.0).roundToInt()
        val avgDailyFat = (totalFat / 7.0).roundToInt()

        val deviation = avgDailyCalories - calorieTarget
        val deviationPercent = if (calorieTarget > 0) ((abs(deviation).toDouble() / calorieTarget) * 100).roundToInt() else 0

        val restrictedCount = meals.count { it.isRestricted }
        val nightRefuelingCount = meals.count { it.isNightRefueling }
        val totalViolations = restrictedCount + nightRefuelingCount

        val liquidRatio = if (totalMeals > 0) ((liquidMealsCount.toDouble() / totalMeals) * 100).roundToInt() else 0

        val directive = when (complianceStatus) {
            ComplianceStatus.EXEMPLARY -> "COMMENDATION: Subject demonstrates strict metabolic discipline. Caloric intake conforms to central committee directives."
            ComplianceStatus.ACCEPTABLE -> "OBSERVATION: Subject remains within acceptable operational boundaries. Minor variance detected; surveillance continues."
            ComplianceStatus.SUBVERSIVE -> "WARNING: Unsanctioned nutritional deviations detected. Caloric intake exceeds mandated quotas. Corrective adherence required."
            ComplianceStatus.CRISIS -> "CRITICAL ALERT: Subject is in active defiance of metabolic mandates. Immediate nutritional plea required to avert terminal lockdown."
            ComplianceStatus.LOCKED -> "TERMINAL DISCIPLINE: Permanent lockout instituted. Data locked down due to systemic non-compliance."
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
        sb.append("AVG DAILY CONSUMED   : $avgDailyCalories kcal (")
        if (deviation >= 0) sb.append("+$deviation kcal, ") else sb.append("$deviation kcal, ")
        sb.append("$deviationPercent% deviation)\n")
        sb.append("TOTAL 7-DAY INTAKE   : $totalCalories kcal across $totalMeals meals\n\n")

        sb.append("--- [2] MACRONUTRIENT TOTALS & DAILY AVERAGES ---\n")
        sb.append("PROTEIN : ${totalProtein.roundToInt()}g total | ~${avgDailyProtein}g/day\n")
        sb.append("CARBS   : ${totalCarbs.roundToInt()}g total | ~${avgDailyCarbs}g/day\n")
        sb.append("FAT     : ${totalFat.roundToInt()}g total | ~${avgDailyFat}g/day\n")
        sb.append("LIQUIDS : $liquidMealsCount liquid events ($liquidRatio% of total entries)\n\n")

        sb.append("--- [3] SECURITY INCIDENTS & ANOMALIES ---\n")
        sb.append("ZONE RESTRICTION INFRACTIONS : $restrictedCount\n")
        sb.append("NIGHT REFUELING VIOLATIONS   : $nightRefuelingCount\n")
        sb.append("TOTAL FLAGGED ANOMALIES      : $totalViolations\n\n")

        if (totalViolations > 0) {
            sb.append("FLAGGED LOG ENTRIES:\n")
            meals.filter { it.isRestricted || it.isNightRefueling }.forEach { violation ->
                val tags = buildList {
                    if (violation.isRestricted) add("RESTRICTED ZONE")
                    if (violation.isNightRefueling) add("NIGHT REFUELING")
                }.joinToString(", ")
                sb.append(" - [${dateFormat.format(Date(violation.timestamp))}] ${violation.foodName} (${violation.calories} kcal) => $tags\n")
            }
            sb.append("\n")
        }

        sb.append("--- [4] COMMAND DIRECTIVE ---\n")
        sb.append("$directive\n")
        sb.append("=====================================================\n")

        return sb.toString()
    }
}
