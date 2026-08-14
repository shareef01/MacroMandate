package com.sharek.macromandate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharek.macromandate.data.repository.MealRepository
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.viewmodel.ComplianceStatus
import com.sharek.macromandate.viewmodel.MainViewModel
import com.sharek.macromandate.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: MainViewModel) {
    val todayMeals by viewModel.todayMeals.collectAsState()
    val weeklyMeals by viewModel.weeklyMeals.collectAsState()
    val target by viewModel.calorieTarget.collectAsState()
    val complianceStatus by viewModel.complianceStatus.collectAsState()
    val dailyBriefing by viewModel.dailyBriefing.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showMap by remember { mutableStateOf(false) }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // Edge-to-edge: without this the heading sits under the status bar.
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Today's progress",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(24.dp))
                DailyComplianceChart(totalCalories, target)
                Spacer(modifier = Modifier.height(48.dp))
                
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
                            containerColor = Color(0xFF0D47A1),
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
                WeeklyBarChart(weeklyMeals)
                // Clears the bottom navigation bar.
                Spacer(modifier = Modifier.height(96.dp))
            }
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
    val progress = (current.toFloat() / target).coerceIn(0f, 1f)
    val color = if (current > target) Color(0xFFFF1744) else Color(0xFF00E5FF)

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Canvas(modifier = Modifier.size(220.dp)) {
            drawArc(
                color = Color.DarkGray.copy(alpha = 0.3f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 24.dp.toPx())
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 24.dp.toPx())
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
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MacroBars(p: Float, c: Float, f: Float) {
    val total = p + c + f
    val pWeight = if (total > 0) p / total else 0f
    val cWeight = if (total > 0) c / total else 0f
    val fWeight = if (total > 0) f / total else 0f

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TerminalProgressBar(label = "Protein", amount = p, progress = pWeight, color = Color(0xFF00FF00))
        TerminalProgressBar(label = "Carbs", amount = c, progress = cWeight, color = Color.White)
        TerminalProgressBar(label = "Fat", amount = f, progress = fWeight, color = Color(0xFFFFEA00))
    }
}

@Composable
fun TerminalProgressBar(label: String, amount: Float, progress: Float, color: Color) {
    Column {
        Text(
            text = label, 
            style = MaterialTheme.typography.labelSmall, 
            fontWeight = FontWeight.Black,
            color = color
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(Color.DarkGray.copy(alpha = 0.3f), RectangleShape)
        ) {
            // Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(color, RectangleShape)
            )
            
            // Data Overlay
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "${amount.toInt()}g",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = if (progress > 0.8f) Color.Black else color
                )
            }
        }
    }
}

@Composable
fun WeeklyBarChart(meals: List<MealEntry>) {
    // Same window the compliance score is computed over, so the bars account for
    // exactly the days that move the score.
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

    val maxVal = weeklyData.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    
    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        // Monospace Grid
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridCount = 4
            for (i in 0..gridCount) {
                val y = size.height - (size.height / gridCount) * i
                drawLine(
                    color = Color.White.copy(alpha = 0.1f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            weeklyData.forEach { (day, calories) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val barHeight = (calories.toFloat() / maxVal) * 160
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(barHeight.dp)
                            .background(Color.White, RectangleShape)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = day, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun SurveillanceMap(meals: List<MealEntry>, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
            meals.forEach { meal ->
                if (meal.latitude != null && meal.longitude != null) {
                    // Simple relative projection for mock purpose
                    val x = (size.width / 2) + (meal.longitude.toFloat() % 1f) * size.width * 2
                    val y = (size.height / 2) - (meal.latitude.toFloat() % 1f) * size.height * 2
                    
                    val center = Offset(x.coerceIn(0f, size.width), y.coerceIn(0f, size.height))
                    
                    // Crosshair
                    val crossSize = 10.dp.toPx()
                    drawLine(Color(0xFF00FF00), Offset(center.x - crossSize, center.y), Offset(center.x + crossSize, center.y), 2.dp.toPx())
                    drawLine(Color(0xFF00FF00), Offset(center.x, center.y - crossSize), Offset(center.x, center.y + crossSize), 2.dp.toPx())
                    drawCircle(Color(0xFF00FF00), radius = 4.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
                }
            }
        }
        
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Where you ate", color = Color(0xFF00FF00), fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onBack, shape = RectangleShape, modifier = Modifier.fillMaxWidth()) {
                Text("Close map")
            }
        }
    }
}
