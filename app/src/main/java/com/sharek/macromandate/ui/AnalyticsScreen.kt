package com.sharek.macromandate.ui

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharek.macromandate.data.repository.MealRepository
import com.sharek.macromandate.R
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.ui.theme.NutritionColors
import com.sharek.macromandate.viewmodel.MainViewModel
import com.sharek.macromandate.viewmodel.UiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val todayMeals by viewModel.todayMeals.collectAsState()
    val weeklyMeals by viewModel.weeklyMeals.collectAsState()
    // The map button was gated on the compliance status; what actually matters is
    // whether there is anything to plot. Opening a full-screen tactical grid to
    // announce "no data" is expensive UI doing no work.
    // Counted over exactly the list the map plots (todayMeals), so the button's
    // label cannot promise pins the map will not draw.
    val geotaggedCount = remember(todayMeals) {
        todayMeals.count { meal -> meal.latitude != null && meal.longitude != null }
    }
    val target by viewModel.calorieTarget.collectAsState()
    val dailyBriefing by viewModel.dailyBriefing.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showMap by remember { mutableStateOf(false) }
    var activeReport by remember { mutableStateOf<String?>(null) }

    val reportExported = stringResource(R.string.trends_report_exported)
    val reportExportFailed = stringResource(R.string.trends_report_export_failed)
    val reportCopied = stringResource(R.string.trends_report_copied)
    val briefingCopied = stringResource(R.string.trends_briefing_copied)

    val exportReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
        onResult = { uri ->
            uri?.let { targetUri ->
                val text = activeReport ?: return@let
                viewModel.exportReportTo(context, targetUri, text) { succeeded ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (succeeded) reportExported else reportExportFailed
                        )
                    }
                }
            }
        }
    )

    // Summary failures used to be discarded silently: the spinner vanished and
    // nothing replaced it. Surface them and return the state to Idle.
    //
    // The message is resolved here, in composition, rather than inside the
    // effect. A LaunchedEffect body is a suspend lambda, not a composable, so
    // reaching for a resource from inside it means going through LocalContext —
    // which does not re-read when the configuration changes.
    val errorMessage = (uiState as? UiState.Error)?.let { stringResource(it.messageRes) }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.resetUiState()
        }
    }

    val totalCalories = todayMeals.sumOf { it.calories }
    val totalProtein = todayMeals.sumOf { it.proteinGrams.toDouble() }.toFloat()
    val totalCarbs = todayMeals.sumOf { it.carbsGrams.toDouble() }.toFloat()
    val totalFat = todayMeals.sumOf { it.fatGrams.toDouble() }.toFloat()

    Box(modifier = Modifier.fillMaxSize()) {
        if (showMap) {
            SurveillanceMap(todayMeals) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                showMap = false
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Pinned Tactical Top Bar (Prevents content scrolling under status bar)
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.trends_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.trends_todays_progress),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    DailyComplianceChart(totalCalories, target)
                    Spacer(modifier = Modifier.height(36.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            showMap = true
                        },
                        modifier = Modifier.weight(1f),
                        // Only disabled when there is genuinely nothing to plot.
                        // It used to be disabled for being off target as well.
                        enabled = geotaggedCount > 0,
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.DarkGray,
                            disabledContainerColor = Color(0xFF1A1A1A)
                        )
                    ) {
                        Text(
                            text = if (geotaggedCount > 0) {
                                stringResource(R.string.trends_meal_map, geotaggedCount)
                            } else {
                                stringResource(R.string.trends_no_geotagged)
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.generateDailyBriefing()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = todayMeals.isNotEmpty(),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = Color(0xFF1A1A1A)
                        )
                    ) {
                        Text(
                            stringResource(R.string.trends_daily_summary),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }

                // "Daily summary" sends today's meal names and totals to the
                // configured provider as text. That is a second network boundary
                // beyond the photo upload, and it was previously undisclosed.
                Text(
                    text = stringResource(R.string.trends_summary_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.Start).padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = stringResource(R.string.trends_macros_today),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(16.dp))
                MacroBars(totalProtein, totalCarbs, totalFat)

                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = stringResource(R.string.trends_last_7_days),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(4.dp))
                // The chart's contentDescription already states the target in
                // words for TalkBack; sighted users got a line on the chart
                // with no on-screen key saying what it was.
                Row(
                    modifier = Modifier.align(Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(TARGET_LINE_STROKE)
                            .background(MaterialTheme.colorScheme.error)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.trends_target_line_legend),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                WeeklyBarChart(weeklyMeals, target)

                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        activeReport = viewModel.generateWeeklyReport()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = weeklyMeals.isNotEmpty(),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = Color(0xFF1A1A1A)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.trends_generate_dossier),
                        fontWeight = FontWeight.Black
                    )
                }

                // Clears the bottom navigation bar.
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }

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
                            text = stringResource(R.string.trends_debrief_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { activeReport = null }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.content_description_close),
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
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
                            .background(Color.Black, RectangleShape)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = reportText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            exportReportLauncher.launch("MacroMandate_Weekly_Debrief_${exportTimestamp()}.md")
                        },
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.trends_export_file), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            clipboardManager.setText(AnnotatedString(reportText))
                            scope.launch {
                                snackbarHostState.showSnackbar(reportCopied)
                            }
                        },
                        shape = RectangleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.trends_copy),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                shape = RectangleShape,
                containerColor = Color(0xFF101010)
            )
        }


        // Daily Briefing Overlay
        dailyBriefing?.let { briefing ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { viewModel.dismissBriefing() }
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Theme-derived, not a fixed cyan: this panel used to stay
                        // cyan regardless of which of the four themes was active.
                        .hudFraming(MaterialTheme.colorScheme.primary, length = 40.dp, thickness = 4.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.trends_daily_summary),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        // Parity with the weekly dossier dialog, which already has
                        // a copy action — this summary previously had no way to
                        // keep a copy of what the model said.
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            clipboardManager.setText(AnnotatedString(briefing))
                            scope.launch { snackbarHostState.showSnackbar(briefingCopied) }
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.trends_copy),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Was a character-by-character typewriter reveal with a
                    // blinking block cursor driven by an endless loop. A summary
                    // the user has to wait to finish reading, and which a screen
                    // reader re-announces on every character, is not worth the
                    // effect.
                    Text(
                        text = briefing,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        stringResource(R.string.trends_dismiss),
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        if (uiState is UiState.Loading) {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.trends_writing_summary),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        // The photo-analysis loading overlay on Today has always
                        // had a way out of a slow request; this one previously
                        // trapped the user until the network call resolved.
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                viewModel.cancelDailyBriefing()
                            },
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, Color.Gray)
                        ) {
                            Text(
                                stringResource(R.string.action_cancel_operation),
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
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

@Composable
fun DailyComplianceChart(current: Int, target: Int) {
    val safeTarget = target.coerceAtLeast(1)
    val progress = (current.toFloat() / safeTarget).coerceIn(0f, 1f)
    val isOverTarget = current > target
    val color = if (isOverTarget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val chartDescription = if (isOverTarget) {
        stringResource(R.string.chart_daily_description_over, current, target, current - target)
    } else {
        stringResource(R.string.chart_daily_description_remaining, current, target, target - current)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(220.dp)
            // clearAndSetSemantics, not semantics: without it, TalkBack visits
            // this description *and then* the current/target/delta Text nodes
            // inside it, reading the same numbers twice.
            .clearAndSetSemantics { contentDescription = chartDescription }
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            drawArc(
                color = Color.DarkGray.copy(alpha = 0.3f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Square)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Square)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = current.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black
            )
            Text(
                text = stringResource(R.string.trends_of_target, target),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            val delta = current - target
            val deltaLabel = if (isOverTarget) "+$delta kcal over" else "${-delta} kcal left"
            Text(
                text = deltaLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
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

    val pPct = if (totalMacroCal > 0f) ((pCal / totalMacroCal) * 100).toInt() else 0
    val cPct = if (totalMacroCal > 0f) ((cCal / totalMacroCal) * 100).toInt() else 0
    val fPct = if (totalMacroCal > 0f) ((fCal / totalMacroCal) * 100).toInt() else 0

    val pProgress = if (totalMacroCal > 0f) (pCal / totalMacroCal).coerceIn(0f, 1f) else 0f
    val cProgress = if (totalMacroCal > 0f) (cCal / totalMacroCal).coerceIn(0f, 1f) else 0f
    val fProgress = if (totalMacroCal > 0f) (fCal / totalMacroCal).coerceIn(0f, 1f) else 0f

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TerminalProgressBar(label = "Protein", amount = p, pct = pPct, progress = pProgress, color = NutritionColors.Protein)
        TerminalProgressBar(label = "Carbs", amount = c, pct = cPct, progress = cProgress, color = NutritionColors.Carbs)
        TerminalProgressBar(label = "Fat", amount = f, pct = fPct, progress = fProgress, color = NutritionColors.Fat)
    }
}

@Composable
fun TerminalProgressBar(label: String, amount: Float, pct: Int, progress: Float, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = color
            )
            val pctText = if (amount > 0f && progress > 0f) "${pct}% cal" else "0%"
            Text(
                text = stringResource(R.string.macro_amount_with_share, formatGramsValue(amount), pctText),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(Color.DarkGray.copy(alpha = 0.3f), RectangleShape)
        ) {
            if (progress > 0f && amount > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.01f, 1f))
                        .fillMaxHeight()
                        .background(color, RectangleShape)
                )
            }
        }
    }
}

@Composable
fun WeeklyBarChart(meals: List<MealEntry>, target: Int) {
    // Grouping was by DAY_OF_YEAR alone, which is not a day identity across a
    // year boundary; the compliance engine was fixed for this and the chart was
    // not. Match its year-qualified key. Locale.getDefault() so the weekday
    // abbreviations are the user's, not always English.
    val dayLabelFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val weeklyData = remember(meals, dayLabelFormat) {
        ((MealRepository.WEEK_LENGTH_DAYS - 1) downTo 0).map { daysAgo ->
            val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            val label = dayLabelFormat.format(date.time).uppercase(Locale.getDefault())
            val key = date.get(Calendar.YEAR) * 1000 + date.get(Calendar.DAY_OF_YEAR)

            val dayTotal = meals.filter { meal ->
                val cal = Calendar.getInstance().apply { timeInMillis = meal.timestamp }
                cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR) == key
            }.sumOf { it.calories }

            label to dayTotal
        }
    }

    val maxVal = maxOf(target, weeklyData.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    // Averaged over the days that actually have entries. Dividing by a fixed 7
    // told a two-day-old install its "7-day average" was two sevenths of what
    // the person ate — a number stated as fact, and read out verbatim by
    // TalkBack.
    val daysWithEntries = weeklyData.count { it.second > 0 }
    val avgCalories = if (daysWithEntries > 0) {
        weeklyData.sumOf { it.second } / daysWithEntries
    } else {
        0
    }
    val weeklyDescription = if (daysWithEntries == 0) {
        pluralStringResource(
            R.plurals.chart_weekly_description_empty,
            MealRepository.WEEK_LENGTH_DAYS,
            MealRepository.WEEK_LENGTH_DAYS,
            target
        )
    } else {
        pluralStringResource(
            R.plurals.chart_weekly_description,
            daysWithEntries,
            avgCalories,
            daysWithEntries,
            target
        )
    }

    val gridColor = MaterialTheme.colorScheme.onSurface
    val targetLineColor = MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            // clearAndSetSemantics, not semantics: without it, TalkBack visits
            // this description and then each day/value Text below it, reading
            // the same seven numbers twice.
            .clearAndSetSemantics { contentDescription = weeklyDescription }
    ) {
        // Grid, plus the target reference line.
        //
        // The target line used to be positioned with `height - (target/max) *
        // (height - 40dp)` while the bars were sized as `(calories/max) * 140dp`
        // measured up from a different baseline. Two scales on one chart: the
        // line did not sit where a bar of that value would reach, so reading a
        // day as over or under target off the graphic gave the wrong answer.
        // Both now use BAR_MAX_HEIGHT from the same baseline.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseline = size.height - BASELINE_INSET.toPx()
            val gridCount = 4
            for (i in 0..gridCount) {
                val y = baseline - (BAR_MAX_HEIGHT.toPx() / gridCount) * i
                drawLine(
                    color = gridColor.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            val targetY = baseline - (target.toFloat() / maxVal) * BAR_MAX_HEIGHT.toPx()
            drawLine(
                color = targetLineColor.copy(alpha = 0.35f),
                start = Offset(0f, targetY),
                end = Offset(size.width, targetY),
                strokeWidth = TARGET_LINE_STROKE.toPx()
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            weeklyData.forEach { (day, calories) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = if (calories > 0) "$calories" else "-",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (calories > target) MaterialTheme.colorScheme.error else Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val barHeight = if (calories > 0) {
                        ((calories.toFloat() / maxVal) * BAR_MAX_HEIGHT.value).coerceAtLeast(4f)
                    } else {
                        2f
                    }
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(barHeight.dp)
                            .background(
                                if (calories > target) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                RectangleShape
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

/**
 * The centroid-relative projection this map plots meals with.
 *
 * The previous version placed each pin at `(longitude % 1f, latitude % 1f)` —
 * the fractional part of the coordinate, with the whole degree discarded. Two
 * meals eaten a block apart, or on different continents, could render in the
 * same place; two meals eaten in the same room could render far apart if a
 * whole-degree boundary happened to sit between them. This computes each
 * meal's real offset from the group's center in meters (an equirectangular
 * approximation — accurate at the city scale this feature is used at) so
 * relative position is actually meaningful, and exposes [gridStepMeters] so
 * the screen can state its own scale instead of implying a precision the old
 * version didn't have.
 */
private class MapReference(
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

        /** Never zoom in past this span, so a single pin or a tight cluster still shows a real, legible scale. */
        private const val MIN_SPAN_METERS = 25.0

        /**
         * Filters to geotagged meals itself rather than trusting the caller
         * to have done so — [SurveillanceMap] already pre-filters before
         * calling this, but a `!!` on a field the type system says is
         * nullable should not also depend on every future call site
         * remembering to filter first.
         */
        fun of(meals: List<MealEntry>): MapReference? {
            val coordinates = meals.mapNotNull { meal ->
                val lat = meal.latitude
                val lon = meal.longitude
                if (lat != null && lon != null) lat to lon else null
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

        /** Snaps a raw distance to a 1/2/5 x 10^n step so the on-screen scale reads as a real number. */
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
    val geotaggedMeals = remember(meals) { meals.filter { it.latitude != null && it.longitude != null } }
    val primaryColor = MaterialTheme.colorScheme.primary
    val mapRef = remember(geotaggedMeals) { MapReference.of(geotaggedMeals) }
    val mapDescription = if (mapRef != null) {
        pluralStringResource(
            R.plurals.trends_map_description,
            geotaggedMeals.size,
            geotaggedMeals.size,
            mapRef.gridStepMeters * 3
        )
    } else {
        stringResource(R.string.empty_no_map_points)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = mapDescription }
        ) {
            if (mapRef != null) {
                // Fit the widest spread of points inside a circle around the
                // canvas center, leaving room for the crosshair glyphs at the edge.
                val usableRadiusPx = (minOf(size.width, size.height) / 2f) - 40.dp.toPx()
                val pxPerMeter = (usableRadiusPx / mapRef.maxDeltaMeters.toFloat()).coerceAtLeast(0.001f)
                val gridStepPx = mapRef.gridStepMeters * pxPerMeter

                if (gridStepPx > 1f) {
                    val cols = (size.width / gridStepPx).toInt() + 1
                    val rows = (size.height / gridStepPx).toInt() + 1
                    for (x in 0..cols) {
                        drawLine(Color.DarkGray.copy(alpha = 0.3f), Offset(x * gridStepPx, 0f), Offset(x * gridStepPx, size.height))
                    }
                    for (y in 0..rows) {
                        drawLine(Color.DarkGray.copy(alpha = 0.3f), Offset(0f, y * gridStepPx), Offset(size.width, y * gridStepPx))
                    }
                }

                geotaggedMeals.forEach { meal ->
                    val (dxMeters, dyMeters) = mapRef.offsetMeters(meal.latitude!!, meal.longitude!!)
                    val x = (size.width / 2) + (dxMeters * pxPerMeter).toFloat()
                    val y = (size.height / 2) - (dyMeters * pxPerMeter).toFloat()
                    val center = Offset(x.coerceIn(20f, size.width - 20f), y.coerceIn(20f, size.height - 20f))

                    // Crosshair
                    val crossSize = 10.dp.toPx()
                    drawLine(primaryColor, Offset(center.x - crossSize, center.y), Offset(center.x + crossSize, center.y), 2.dp.toPx())
                    drawLine(primaryColor, Offset(center.x, center.y - crossSize), Offset(center.x, center.y + crossSize), 2.dp.toPx())
                    drawCircle(primaryColor, radius = 4.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
                }
            }
        }

        if (geotaggedMeals.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                // Sentence case, and it says what to do next. All-caps suits a
                // short status label; a two-line instruction set in it is slower
                // to read for no gain. This state is now also unreachable from the
                // Trends tab - the button that opens the map is disabled when there
                // is nothing to plot, rather than opening a full-screen tactical
                // grid to announce that there is no data.
                Text(
                    text = stringResource(R.string.empty_no_map_points),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.trends_where_you_ate),
                color = primaryColor,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium
            )
            if (mapRef != null) {
                // States the map's own scale rather than implying a precision
                // that a grid with no legend would only pretend to have.
                Text(
                    text = stringResource(R.string.trends_map_scale, mapRef.gridStepMeters),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onBack,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(R.string.trends_close_map), fontWeight = FontWeight.Black)
            }
        }
    }
}


/**
 * The drawing geometry the weekly chart's bars and its target line share.
 *
 * Kept as named constants precisely because they were previously implicit and
 * disagreed: any change to one has to move the other.
 */
private val BAR_MAX_HEIGHT = 140.dp

/** Space below the bars for the weekday labels. */
private val BASELINE_INSET = 28.dp

/** Shared by the target line's Canvas stroke and its on-screen legend swatch, so the two can't drift apart. */
private val TARGET_LINE_STROKE = 1.dp
