package com.sharek.macromandate.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharek.macromandate.data.repository.MealRepository
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.ui.components.*
import com.sharek.macromandate.ui.theme.NutritionColors
import com.sharek.macromandate.ui.theme.TerminalDesign
import com.sharek.macromandate.util.NutritionFormat
import com.sharek.macromandate.viewmodel.MainViewModel
import com.sharek.macromandate.viewmodel.UiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

enum class AnalyticsPeriod(val days: Int, val label: String) {
    SEVEN_DAYS(MealRepository.WEEK_LENGTH_DAYS, "7 DAYS"),
    THIRTY_DAYS(MealRepository.MONTH_LENGTH_DAYS, "30 DAYS")
}

data class DailyBucket(
    val date: LocalDate,
    val shortLabel: String,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val mealCount: Int
) {
    /** Distinguishes an unrecorded day (0 entries) from a recorded 0-kcal day (e.g. water/black coffee). */
    val hasRecordedMeals: Boolean get() = mealCount > 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val tokens = TerminalDesign.tokens
    val snackbarHostState = remember { SnackbarHostState() }

    val todayMeals by viewModel.todayMeals.collectAsStateWithLifecycle()
    val weeklyMeals by viewModel.weeklyMeals.collectAsStateWithLifecycle()
    val monthlyMeals by viewModel.monthlyMeals.collectAsStateWithLifecycle()
    val target by viewModel.calorieTarget.collectAsStateWithLifecycle()
    val dailyBriefing by viewModel.dailyBriefing.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedPeriodOrdinal by rememberSaveable { mutableIntStateOf(0) }
    val selectedPeriod = AnalyticsPeriod.entries.getOrElse(selectedPeriodOrdinal) { AnalyticsPeriod.SEVEN_DAYS }
    val activePeriodMeals = if (selectedPeriod == AnalyticsPeriod.SEVEN_DAYS) weeklyMeals else monthlyMeals

    var showMap by rememberSaveable { mutableStateOf(false) }
    var activeReport by rememberSaveable { mutableStateOf<String?>(null) }

    val geotaggedMeals = remember(activePeriodMeals) {
        activePeriodMeals.filter { it.latitude != null && it.longitude != null }
    }

    val exportReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
        onResult = { uri ->
            uri?.let { targetUri ->
                val text = activeReport ?: return@let
                viewModel.exportReportTo(context, targetUri, text) { succeeded ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (succeeded) "Weekly dossier debrief exported." else "Export failed."
                        )
                    }
                }
            }
        }
    )

    LaunchedEffect(uiState) {
        (uiState as? UiState.Error)?.let { error ->
            snackbarHostState.showSnackbar(error.message)
            viewModel.resetUiState()
        }
    }

    val totalCalories = todayMeals.sumOf { it.calories }
    val totalProtein = todayMeals.sumOf { it.proteinGrams.toDouble() }.toFloat()
    val totalCarbs = todayMeals.sumOf { it.carbsGrams.toDouble() }.toFloat()
    val totalFat = todayMeals.sumOf { it.fatGrams.toDouble() }.toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
    ) {
        if (showMap) {
            SurveillanceMap(geotaggedMeals) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                showMap = false
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                TerminalTopBar(
                    title = "TRENDS & ANALYTICS",
                    subtitle = "METABOLIC INTAKE TELEMETRY"
                )

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = tokens.maxContentWidth)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Today's Progress & Macro Distribution Card
                        TerminalCard(withHudCorners = true) {
                            TerminalSectionHeader(
                                title = "Today's Intake & Macro Balance",
                                badge = "${todayMeals.size} MEALS"
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                DailyComplianceChart(current = totalCalories, target = target)
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            HorizontalDivider(color = tokens.divider)
                            Spacer(modifier = Modifier.height(16.dp))

                            MacroBars(p = totalProtein, c = totalCarbs, f = totalFat)

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TerminalSecondaryButton(
                                    text = if (geotaggedMeals.isNotEmpty()) {
                                        "MEAL MAP (${geotaggedMeals.size})"
                                    } else {
                                        "NO GEOTAGS"
                                    },
                                    icon = Icons.Default.Map,
                                    enabled = geotaggedMeals.isNotEmpty(),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        showMap = true
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                TerminalPrimaryButton(
                                    text = "AI SUMMARY",
                                    icon = Icons.Default.AutoAwesome,
                                    enabled = todayMeals.isNotEmpty(),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        viewModel.generateDailyBriefing()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Daily summary sends today's meal names and nutrition totals to your configured analysis provider.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.secondaryText
                            )
                        }

                        // Period Selector (7-Day vs 30-Day) + Historical Trends Card
                        TerminalCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TerminalSectionHeader(
                                    title = "Intake History (${selectedPeriod.label})",
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AnalyticsPeriod.entries.forEach { period ->
                                        val isSelected = period == selectedPeriod
                                        Surface(
                                            color = if (isSelected) tokens.primaryAccent else tokens.elevatedSurface,
                                            shape = tokens.controlShape,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) tokens.primaryAccent else tokens.border
                                            ),
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 36.dp)
                                                .semantics {
                                                    role = Role.Tab
                                                    selected = isSelected
                                                }
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                    selectedPeriodOrdinal = period.ordinal
                                                }
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = period.label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSelected) tokens.background else tokens.primaryText,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            PeriodAnalyticsContent(
                                meals = activePeriodMeals,
                                period = selectedPeriod,
                                target = target
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            TerminalSecondaryButton(
                                text = "GENERATE WEEKLY DOSSIER DEBRIEF",
                                icon = Icons.Default.Assessment,
                                enabled = weeklyMeals.isNotEmpty(),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    activeReport = viewModel.generateWeeklyReport()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // Weekly Dossier Report Dialog
        activeReport?.let { reportText ->
            AlertDialog(
                onDismissRequest = { activeReport = null },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WEEKLY INTELLIGENCE DEBRIEF",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = tokens.primaryAccent
                        )
                        IconButton(
                            onClick = { activeReport = null },
                            modifier = Modifier.size(tokens.minTouchTarget)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close debrief",
                                tint = tokens.secondaryText
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                            .background(tokens.background, tokens.controlShape)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = reportText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = tokens.primaryText
                        )
                    }
                },
                confirmButton = {
                    TerminalPrimaryButton(
                        text = "EXPORT FILE",
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                            exportReportLauncher.launch("MacroMandate_Weekly_Debrief_$stamp.md")
                        }
                    )
                },
                dismissButton = {
                    TerminalSecondaryButton(
                        text = "COPY",
                        icon = Icons.Default.ContentCopy,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            copyToClipboard(context, "MacroMandate Debrief", reportText)
                            scope.launch {
                                snackbarHostState.showSnackbar("Dossier copied to clipboard.")
                            }
                        }
                    )
                },
                shape = tokens.cardShape,
                containerColor = tokens.surface
            )
        }

        // Daily Summary Briefing Modal
        dailyBriefing?.let { briefing ->
            AlertDialog(
                onDismissRequest = viewModel::dismissBriefing,
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DAILY NUTRITION SUMMARY",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = tokens.primaryAccent
                        )
                        IconButton(
                            onClick = viewModel::dismissBriefing,
                            modifier = Modifier.size(tokens.minTouchTarget)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close summary",
                                tint = tokens.secondaryText
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = briefing,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.primaryText
                        )
                    }
                },
                confirmButton = {
                    TerminalPrimaryButton(
                        text = "DONE",
                        onClick = viewModel::dismissBriefing
                    )
                },
                dismissButton = {
                    TerminalSecondaryButton(
                        text = "COPY",
                        icon = Icons.Default.ContentCopy,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            copyToClipboard(context, "Daily Summary", briefing)
                            scope.launch {
                                snackbarHostState.showSnackbar("Summary copied to clipboard.")
                            }
                        }
                    )
                },
                shape = tokens.cardShape,
                containerColor = tokens.surface
            )
        }

        if (uiState is UiState.Loading) {
            TerminalLoadingOverlay(
                title = "SYNTHESIZING DAILY SUMMARY",
                subtitle = "Summarizing today's logged meals…",
                onCancel = viewModel::cancelDailyBriefing
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
fun PeriodAnalyticsContent(
    meals: List<MealEntry>,
    period: AnalyticsPeriod,
    target: Int
) {
    val tokens = TerminalDesign.tokens
    val zoneId = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zoneId) }
    val locale = remember { Locale.getDefault() }

    val buckets = remember(meals, period, today, zoneId, locale) {
        val grouped = meals.groupBy { meal ->
            Instant.ofEpochMilli(meal.timestamp).atZone(zoneId).toLocalDate()
        }
        val dayFormatter = if (period == AnalyticsPeriod.SEVEN_DAYS) {
            DateTimeFormatter.ofPattern("EEE", locale)
        } else {
            DateTimeFormatter.ofPattern("d MMM", locale)
        }
        ((period.days - 1) downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val dayMeals = grouped[date].orEmpty()
            DailyBucket(
                date = date,
                shortLabel = date.format(dayFormatter).uppercase(locale),
                calories = dayMeals.sumOf { it.calories },
                proteinGrams = dayMeals.sumOf { it.proteinGrams.toDouble() }.toFloat(),
                carbsGrams = dayMeals.sumOf { it.carbsGrams.toDouble() }.toFloat(),
                fatGrams = dayMeals.sumOf { it.fatGrams.toDouble() }.toFloat(),
                mealCount = dayMeals.size
            )
        }
    }

    val recordedBuckets = remember(buckets) { buckets.filter { it.hasRecordedMeals } }
    val recordedDaysCount = recordedBuckets.size
    val avgCalories = if (recordedDaysCount > 0) {
        (recordedBuckets.sumOf { it.calories }.toDouble() / recordedDaysCount).roundToInt()
    } else 0
    val avgProtein = if (recordedDaysCount > 0) {
        (recordedBuckets.sumOf { it.proteinGrams.toDouble() } / recordedDaysCount).toFloat()
    } else 0f
    val avgCarbs = if (recordedDaysCount > 0) {
        (recordedBuckets.sumOf { it.carbsGrams.toDouble() } / recordedDaysCount).toFloat()
    } else 0f
    val avgFat = if (recordedDaysCount > 0) {
        (recordedBuckets.sumOf { it.fatGrams.toDouble() } / recordedDaysCount).toFloat()
    } else 0f

    // Textual Statistical Summary Metrics (Recorded Days, Avg Intake, Avg Macros)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnalyticsMetricTile(
            label = "RECORDED DAYS",
            value = "$recordedDaysCount / ${period.days}",
            subvalue = if (recordedDaysCount == 0) "No entries yet" else "Unrecorded days excluded",
            accent = tokens.primaryAccent,
            modifier = Modifier.weight(1f)
        )
        AnalyticsMetricTile(
            label = "AVG DAILY INTAKE",
            value = if (recordedDaysCount > 0) "$avgCalories kcal" else "—",
            subvalue = if (target > 0) "Target: $target kcal" else "No target set",
            accent = if (target in 1..<avgCalories) tokens.warning else tokens.primaryAccent,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Average Daily Macronutrients Row
    Surface(
        color = tokens.elevatedSurface,
        shape = tokens.controlShape,
        border = BorderStroke(1.dp, tokens.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AVG DAILY MACROS:",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroPill("P", "${NutritionFormat.formatGramsValue(avgProtein)}g", NutritionColors.Protein)
                MacroPill("C", "${NutritionFormat.formatGramsValue(avgCarbs)}g", NutritionColors.Carbs)
                MacroPill("F", "${NutritionFormat.formatGramsValue(avgFat)}g", NutritionColors.Fat)
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Chart Legend (Non-color + Color indicators)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendSwatch(color = tokens.primaryAccent, label = "Within target")
        LegendSwatch(color = tokens.warning, label = "Over target (+)")
        if (target > 0) {
            LegendSwatch(color = tokens.error, label = "Target ($target kcal)", isLine = true)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (recordedDaysCount == 0) {
        Surface(
            color = tokens.elevatedSurface.copy(alpha = 0.5f),
            shape = tokens.controlShape,
            border = BorderStroke(1.dp, tokens.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "No meals recorded in the last ${period.days} days. Logged meals will appear here automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp)
            )
        }
    } else {
        PeriodIntakeBarChart(
            buckets = buckets,
            target = target,
            avgCalories = avgCalories,
            recordedDaysCount = recordedDaysCount
        )
    }
}

@Composable
private fun AnalyticsMetricTile(
    label: String,
    value: String,
    subvalue: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val tokens = TerminalDesign.tokens
    Surface(
        color = tokens.elevatedSurface,
        shape = tokens.controlShape,
        border = BorderStroke(1.dp, tokens.border),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subvalue,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText
            )
        }
    }
}

@Composable
private fun LegendSwatch(
    color: Color,
    label: String,
    isLine: Boolean = false
) {
    val tokens = TerminalDesign.tokens
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isLine) {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(2.dp)
                    .background(color)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, tokens.controlShape)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText
        )
    }
}

@Composable
fun WeeklyBarChart(meals: List<MealEntry>, target: Int) {
    PeriodAnalyticsContent(
        meals = meals,
        period = AnalyticsPeriod.SEVEN_DAYS,
        target = target
    )
}

@Composable
private fun PeriodIntakeBarChart(
    buckets: List<DailyBucket>,
    target: Int,
    avgCalories: Int,
    recordedDaysCount: Int
) {
    val tokens = TerminalDesign.tokens
    val safeTarget = target.coerceAtLeast(0)
    val maxVal = maxOf(safeTarget, buckets.maxOfOrNull { it.calories } ?: 0).coerceAtLeast(1)

    val chartSummary = buildString {
        append("${buckets.size}-day intake chart. ")
        append("$recordedDaysCount days recorded, averaging $avgCalories calories per recorded day. ")
        if (safeTarget > 0) {
            append("Daily target is $safeTarget calories.")
        }
    }

    val isSevenDay = buckets.size <= 7

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clearAndSetSemantics { contentDescription = chartSummary }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseline = size.height - BASELINE_INSET.toPx()
            val gridCount = 4
            for (i in 0..gridCount) {
                val y = baseline - (BAR_MAX_HEIGHT.toPx() / gridCount) * i
                drawLine(
                    color = tokens.border.copy(alpha = 0.45f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            if (safeTarget > 0) {
                val targetY = baseline - (safeTarget.toFloat() / maxVal) * BAR_MAX_HEIGHT.toPx()
                drawLine(
                    color = tokens.error.copy(alpha = 0.7f),
                    start = Offset(0f, targetY),
                    end = Offset(size.width, targetY),
                    strokeWidth = TARGET_LINE_STROKE.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            buckets.forEachIndexed { index, bucket ->
                val isOver = safeTarget > 0 && bucket.calories > safeTarget
                val barColor = when {
                    !bucket.hasRecordedMeals -> tokens.border.copy(alpha = 0.4f)
                    isOver -> tokens.warning
                    else -> tokens.primaryAccent
                }
                val barHeightDp = when {
                    !bucket.hasRecordedMeals -> 2f
                    bucket.calories == 0 -> 4f
                    else -> ((bucket.calories.toFloat() / maxVal) * BAR_MAX_HEIGHT.value).coerceAtLeast(6f)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSevenDay) {
                        val valueLabel = when {
                            !bucket.hasRecordedMeals -> "—"
                            isOver -> "+${bucket.calories}"
                            else -> "${bucket.calories}"
                        }
                        Text(
                            text = valueLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOver) tokens.warning else tokens.secondaryText,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    } else if (isOver) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.warning
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(if (isSevenDay) 24.dp else 7.dp)
                            .height(barHeightDp.dp)
                            .background(barColor, tokens.controlShape)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val showDateTick = isSevenDay || index == 0 || index == buckets.lastIndex || index % 7 == 0
                    Text(
                        text = if (showDateTick) bucket.shortLabel else "",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = if (isSevenDay) 11.sp else 9.sp,
                        color = tokens.secondaryText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun DailyComplianceChart(current: Int, target: Int) {
    val tokens = TerminalDesign.tokens
    val safeTarget = target.coerceAtLeast(1)
    val progress = (current.toFloat() / safeTarget).coerceIn(0f, 1f)
    val isOverTarget = target > 0 && current > target
    val color = if (isOverTarget) tokens.warning else tokens.primaryAccent
    val delta = current - target
    val deltaLabel = when {
        target <= 0 -> "No target configured"
        isOverTarget -> "+$delta kcal over target"
        else -> "${-delta} kcal remaining"
    }
    val chartDescription = "Today's progress: $current of $target calories consumed. $deltaLabel."

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(210.dp)
            .clearAndSetSemantics { contentDescription = chartDescription }
    ) {
        Canvas(modifier = Modifier.size(210.dp)) {
            drawArc(
                color = tokens.elevatedSurface,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Butt)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Butt)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = current.toString(),
                style = MaterialTheme.typography.displayMedium,
                color = tokens.primaryText,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "of $target kcal",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.secondaryText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = deltaLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun MacroBars(p: Float, c: Float, f: Float) {
    val pCal = p * 4f
    val cCal = c * 4f
    val fCal = f * 9f
    val totalMacroCal = pCal + cCal + fCal

    val pPct = if (totalMacroCal > 0f) ((pCal / totalMacroCal) * 100f).roundToInt() else 0
    val cPct = if (totalMacroCal > 0f) ((cCal / totalMacroCal) * 100f).roundToInt() else 0
    val fPct = if (totalMacroCal > 0f) ((fCal / totalMacroCal) * 100f).roundToInt() else 0

    val pProgress = if (totalMacroCal > 0f) (pCal / totalMacroCal).coerceIn(0f, 1f) else 0f
    val cProgress = if (totalMacroCal > 0f) (cCal / totalMacroCal).coerceIn(0f, 1f) else 0f
    val fProgress = if (totalMacroCal > 0f) (fCal / totalMacroCal).coerceIn(0f, 1f) else 0f

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TerminalProgressBar(label = "PROTEIN", amount = p, pct = pPct, progress = pProgress, color = NutritionColors.Protein)
        TerminalProgressBar(label = "CARBS", amount = c, pct = cPct, progress = cProgress, color = NutritionColors.Carbs)
        TerminalProgressBar(label = "FAT", amount = f, pct = fPct, progress = fProgress, color = NutritionColors.Fat)
    }
}

@Composable
fun TerminalProgressBar(label: String, amount: Float, pct: Int, progress: Float, color: Color) {
    val tokens = TerminalDesign.tokens
    val amountText = NutritionFormat.formatGramsValue(amount)
    val pctText = if (amount > 0f && progress > 0f) "$pct% cal" else "0%"

    Column(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "$label: $amountText grams ($pctText)"
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = "${amountText}g ($pctText)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = tokens.secondaryText
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(tokens.controlShape)
                .background(tokens.elevatedSurface)
        ) {
            if (progress > 0f && amount > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
    }
}

/**
 * Centroid-relative equirectangular meter projection for SurveillanceMap.
 */
internal class MapReference(
    val refLat: Double,
    val refLon: Double,
    val metersPerDegLon: Double,
    val maxDeltaMeters: Double
) {
    val gridStepMeters: Int = niceGridStepMeters(maxDeltaMeters)

    fun offsetMeters(lat: Double, lon: Double): Pair<Double, Double> {
        val dx = (lon - refLon) * metersPerDegLon
        val dy = (lat - refLat) * METERS_PER_DEGREE_LAT
        return dx to dy
    }

    companion object {
        private const val METERS_PER_DEGREE_LAT = 111_320.0
        private const val MIN_SPAN_METERS = 25.0

        fun of(meals: List<MealEntry>): MapReference? {
            val coordinates = meals.mapNotNull { meal ->
                val lat = meal.latitude
                val lon = meal.longitude
                if (lat != null && lon != null && lat.isFinite() && lon.isFinite()) lat to lon else null
            }
            if (coordinates.isEmpty()) return null
            val refLat = coordinates.map { it.first }.average()
            val refLon = coordinates.map { it.second }.average()
            val metersPerDegLon = METERS_PER_DEGREE_LAT * cos(Math.toRadians(refLat))
            val maxDelta = coordinates.maxOf { (lat, lon) ->
                val dx = (lon - refLon) * metersPerDegLon
                val dy = (lat - refLat) * METERS_PER_DEGREE_LAT
                max(abs(dx), abs(dy))
            }.coerceAtLeast(MIN_SPAN_METERS)
            return MapReference(refLat, refLon, metersPerDegLon, maxDelta)
        }

        private fun niceGridStepMeters(maxDeltaMeters: Double): Int {
            val rawStep = (maxDeltaMeters / 3.0).coerceAtLeast(1.0)
            val magnitude = 10.0.pow(floor(log10(rawStep)))
            val normalized = rawStep / magnitude
            val niceNormalized = when {
                normalized < 1.5 -> 1.0
                normalized < 3.5 -> 2.0
                normalized < 7.5 -> 5.0
                else -> 10.0
            }
            return (niceNormalized * magnitude).roundToInt().coerceAtLeast(1)
        }
    }
}

@Composable
fun SurveillanceMap(meals: List<MealEntry>, onBack: () -> Unit) {
    val tokens = TerminalDesign.tokens
    val geotaggedMeals = remember(meals) { meals.filter { it.latitude != null && it.longitude != null } }
    val primaryColor = tokens.primaryAccent
    val mapRef = remember(geotaggedMeals) { MapReference.of(geotaggedMeals) }
    val mapDescription = if (mapRef != null) {
        "Meal location map: ${geotaggedMeals.size} meals plotted, grid scale approximately ${mapRef.gridStepMeters} meters per square."
    } else {
        "No geotagged meals recorded."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
    ) {
        TerminalTopBar(
            title = "GEOSPATIAL MEAL MAP",
            subtitle = if (mapRef != null) "Grid: ≈${mapRef.gridStepMeters} m per square" else null,
            onBack = onBack
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = mapDescription }
            ) {
                if (mapRef != null) {
                    val usableRadiusPx = (minOf(size.width, size.height) / 2f) - 40.dp.toPx()
                    val pxPerMeter = (usableRadiusPx / mapRef.maxDeltaMeters.toFloat()).coerceAtLeast(0.001f)
                    val gridStepPx = mapRef.gridStepMeters * pxPerMeter

                    if (gridStepPx > 1f) {
                        val cols = (size.width / gridStepPx).toInt() + 1
                        val rows = (size.height / gridStepPx).toInt() + 1
                        for (x in 0..cols) {
                            drawLine(tokens.border.copy(alpha = 0.45f), Offset(x * gridStepPx, 0f), Offset(x * gridStepPx, size.height))
                        }
                        for (y in 0..rows) {
                            drawLine(tokens.border.copy(alpha = 0.45f), Offset(0f, y * gridStepPx), Offset(size.width, y * gridStepPx))
                        }
                    }

                    geotaggedMeals.forEach { meal ->
                        val (dxMeters, dyMeters) = mapRef.offsetMeters(meal.latitude!!, meal.longitude!!)
                        val x = (size.width / 2f) + (dxMeters * pxPerMeter).toFloat()
                        val y = (size.height / 2f) - (dyMeters * pxPerMeter).toFloat()
                        val center = Offset(x.coerceIn(24f, size.width - 24f), y.coerceIn(24f, size.height - 24f))

                        val crossSize = 10.dp.toPx()
                        drawLine(primaryColor, Offset(center.x - crossSize, center.y), Offset(center.x + crossSize, center.y), 2.dp.toPx())
                        drawLine(primaryColor, Offset(center.x, center.y - crossSize), Offset(center.x, center.y + crossSize), 2.dp.toPx())
                        drawCircle(primaryColor, radius = 5.dp.toPx(), center = center, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }

            if (geotaggedMeals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TerminalEmptyState(
                        title = "NO GEOTAGGED MEALS",
                        message = "Enable 'Tag meals with location' in Settings before capturing a meal photo to plot relative locations here.",
                        actionLabel = "CLOSE MAP",
                        onAction = onBack
                    )
                }
            }
        }

        Surface(
            color = tokens.surface,
            border = BorderStroke(1.dp, tokens.border),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${geotaggedMeals.size} plotted meal(s)",
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.primaryText,
                        fontWeight = FontWeight.Bold
                    )
                    if (mapRef != null) {
                        Text(
                            text = "Relative meter projection · 1 square ≈ ${mapRef.gridStepMeters} m",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.secondaryText
                        )
                    }
                }
                TerminalPrimaryButton(
                    text = "CLOSE MAP",
                    onClick = onBack
                )
            }
        }
    }
}

private val BAR_MAX_HEIGHT = 135.dp
private val BASELINE_INSET = 28.dp
private val TARGET_LINE_STROKE = 1.5.dp
