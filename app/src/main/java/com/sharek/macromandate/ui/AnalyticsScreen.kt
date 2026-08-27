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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharek.macromandate.data.repository.MealRepository
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.ui.theme.NutritionColors
import com.sharek.macromandate.viewmodel.ComplianceStatus
import com.sharek.macromandate.viewmodel.MainViewModel
import com.sharek.macromandate.viewmodel.UiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val todayMeals by viewModel.todayMeals.collectAsState()
    val weeklyMeals by viewModel.weeklyMeals.collectAsState()
    val target by viewModel.calorieTarget.collectAsState()
    val complianceStatus by viewModel.complianceStatus.collectAsState()
    val dailyBriefing by viewModel.dailyBriefing.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showMap by remember { mutableStateOf(false) }
    var activeReport by remember { mutableStateOf<String?>(null) }

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

    // Summary failures used to be discarded silently: the spinner vanished and
    // nothing replaced it. Surface them and return the state to Idle.
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

    Box(modifier = Modifier.fillMaxSize()) {
        if (showMap) {
            SurveillanceMap(todayMeals) { 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                            text = "Trends",
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
                        text = "Today's progress",
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
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMap = true 
                        },
                        modifier = Modifier.weight(1f),
                        enabled = complianceStatus != ComplianceStatus.SUBVERSIVE,
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.DarkGray,
                            disabledContainerColor = Color(0xFF1A1A1A)
                        )
                    ) {
                        Text(
                            text = if (complianceStatus == ComplianceStatus.SUBVERSIVE) "Map locked" else "Meal map",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.generateDailyBriefing()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = todayMeals.isNotEmpty() && complianceStatus != ComplianceStatus.SUBVERSIVE,
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = Color(0xFF1A1A1A)
                        )
                    ) {
                        Text(
                            "Daily summary",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Macros today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(16.dp))
                MacroBars(totalProtein, totalCarbs, totalFat)
                
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = "Last 7 days",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(16.dp))
                WeeklyBarChart(weeklyMeals, target)

                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        activeReport = viewModel.generateWeeklyReport()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = weeklyMeals.isNotEmpty() && complianceStatus != ComplianceStatus.SUBVERSIVE,
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
                        text = if (complianceStatus == ComplianceStatus.SUBVERSIVE) "Debrief locked" else "Generate weekly dossier debrief",
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
                            text = "INTELLIGENCE DEBRIEF",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { activeReport = null }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
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
                            exportReportLauncher.launch("MacroMandate_Weekly_Debrief_${System.currentTimeMillis()}.md")
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
                        Text("EXPORT FILE", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            clipboardManager.setText(AnnotatedString(reportText))
                            scope.launch {
                                snackbarHostState.showSnackbar("Dossier copied to clipboard.")
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
                            "COPY",
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

        if (complianceStatus == ComplianceStatus.SUBVERSIVE) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Red.copy(alpha = 0.4f),
                shape = RectangleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Trends are locked while you're well off target.",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
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
                        .hudFraming(Color(0xFF00E5FF), length = 40.dp, thickness = 4.dp)
                        .background(Color(0xFF001A1A))
                        .padding(24.dp)
                ) {
                    Text(
                        "Daily summary",
                        color = Color(0xFF00E5FF),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TerminalTypewriterText(
                        text = briefing,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Tap anywhere to dismiss",
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
                        CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 4.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Writing your summary...",
                            color = Color(0xFF00E5FF),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black
                        )
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
    val chartDescription = "Daily compliance: $current of $target calories consumed, ${if (isOverTarget) "${current - target} calories over target" else "${target - current} calories remaining"}."

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(220.dp)
            .semantics { contentDescription = chartDescription }
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
                text = "of $target kcal",
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
                text = "${amount.toInt()}g ($pctText)",
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
    val weeklyData = ((MealRepository.WEEK_LENGTH_DAYS - 1) downTo 0).map { daysAgo ->
        val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
        val dayKey = SimpleDateFormat("EEE", Locale.US).format(date.time).uppercase()
        val dayOfYear = date.get(Calendar.DAY_OF_YEAR)
        
        val dayTotal = meals.filter {
            val entryCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            entryCal.get(Calendar.DAY_OF_YEAR) == dayOfYear
        }.sumOf { it.calories }
        
        dayKey to dayTotal
    }

    val maxVal = maxOf(target, weeklyData.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    
    val avgCalories = if (weeklyData.isNotEmpty()) (weeklyData.map { it.second }.average()).toInt() else 0
    val weeklyDescription = "Weekly intake chart: 7-day average $avgCalories calories, daily target $target calories."

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = weeklyDescription }
    ) {
        // Monospace Grid
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridCount = 4
            for (i in 0..gridCount) {
                val y = size.height - (size.height / gridCount) * i
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            // Target line
            val targetY = size.height - (target.toFloat() / maxVal) * (size.height - 40.dp.toPx())
            drawLine(
                color = Color.Red.copy(alpha = 0.3f),
                start = Offset(0f, targetY),
                end = Offset(size.width, targetY),
                strokeWidth = 1.dp.toPx()
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
                    val barHeight = if (calories > 0) ((calories.toFloat() / maxVal) * 140).coerceAtLeast(4f) else 2f
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

@Composable
fun SurveillanceMap(meals: List<MealEntry>, onBack: () -> Unit) {
    val geotaggedMeals = remember(meals) { meals.filter { it.latitude != null && it.longitude != null } }
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw grid
            val step = 50.dp.toPx()
            for (x in 0..(size.width / step).toInt()) {
                drawLine(Color.DarkGray.copy(alpha = 0.3f), Offset(x * step, 0f), Offset(x * step, size.height))
            }
            for (y in 0..(size.height / step).toInt()) {
                drawLine(Color.DarkGray.copy(alpha = 0.3f), Offset(0f, y * step), Offset(size.width, y * step))
            }

            // Plot meals
            geotaggedMeals.forEach { meal ->
                if (meal.latitude != null && meal.longitude != null) {
                    val x = (size.width / 2) + (meal.longitude.toFloat() % 1f) * size.width * 2
                    val y = (size.height / 2) - (meal.latitude.toFloat() % 1f) * size.height * 2
                    
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
                Text(
                    text = "NO GEOTAGGED DOSSIER ENTRIES\n(ENABLE LOCATION IN SETTINGS TO TRACK)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Where you ate", color = primaryColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
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
                Text("Close map", fontWeight = FontWeight.Black)
            }
        }
    }
}
