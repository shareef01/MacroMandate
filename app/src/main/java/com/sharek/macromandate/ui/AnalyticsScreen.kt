package com.sharek.macromandate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: MainViewModel) {
    val todayMeals by viewModel.todayMeals.collectAsState()
    val weeklyMeals by viewModel.weeklyMeals.collectAsState()
    val target by viewModel.calorieTarget.collectAsState()

    val totalCalories = todayMeals.sumOf { it.calories }
    val totalProtein = todayMeals.sumOf { it.proteinGrams.toDouble() }.toFloat()
    val totalCarbs = todayMeals.sumOf { it.carbsGrams.toDouble() }.toFloat()
    val totalFat = todayMeals.sumOf { it.fatGrams.toDouble() }.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DAILY COMPLIANCE",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(24.dp))
        DailyComplianceChart(totalCalories, target)
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "MACRO BREAKDOWN",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(16.dp))
        MacroBars(totalProtein, totalCarbs, totalFat)
        
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "WEEKLY SURVEILLANCE",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(16.dp))
        WeeklyBarChart(weeklyMeals)
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
                text = "/ $target KCAL",
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
        TerminalProgressBar(label = "PROTEIN", amount = p, progress = pWeight, color = Color(0xFF00FF00))
        TerminalProgressBar(label = "CARBS", amount = c, progress = cWeight, color = Color.White)
        TerminalProgressBar(label = "FAT", amount = f, progress = fWeight, color = Color(0xFFFFEA00))
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
                    text = "${amount.toInt()}G",
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
    val weeklyData = (6 downTo 0).map { daysAgo ->
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
                    color = Color.Gray.copy(alpha = 0.2f),
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
