package com.sharek.macromandate.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sharek.macromandate.viewmodel.ComplianceStatus
import com.sharek.macromandate.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(
    viewModel: MainViewModel,
    mealId: String,
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val complianceStatus by viewModel.complianceStatus.collectAsState()
    val mealEntries by viewModel.mealEntries.collectAsState()
    val meal = mealEntries.find { it.id == mealId }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US) }

    if (meal == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("ERROR: RECORD CORRUPTED OR PURGED", color = Color.Red, fontWeight = FontWeight.Black)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            TopAppBar(
                title = { Text("EVIDENCE ARCHIVE", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )

            // Visual Evidence
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(16.dp)
                    .hudFraming(if (meal.isRestricted) Color.Red else Color(0xFF00E5FF), length = 40.dp, thickness = 4.dp)
            ) {
                AsyncImage(
                    model = meal.imageUri,
                    contentDescription = "Visual Evidence",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                if (meal.isRestricted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Red.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "RESTRICTED SECTOR VIOLATION",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.background(Color.Red).padding(4.dp)
                        )
                    }
                }
                
                if (meal.isNightRefueling) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Yellow.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            "CIRCADIAN DISCIPLINE BREACH",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.background(Color.Yellow).padding(4.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                meal.assessment?.let {
                    Surface(
                        color = Color(0xFF0D47A1), // Intense State Blue
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "STATE ASSESSMENT", 
                                color = Color(0xFF00E5FF), 
                                style = MaterialTheme.typography.labelSmall, 
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                it.uppercase(), 
                                color = Color.White, 
                                style = MaterialTheme.typography.bodyMedium, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Text(
                    text = meal.foodName.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ID: ${meal.id.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Text(
                    text = "TIMESTAMP: ${dateFormat.format(Date(meal.timestamp)).uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(thickness = 2.dp, color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(24.dp))

                // Macro Interrogation
                Text("MACRO INTERROGATION", color = Color(0xFF00E5FF), fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(16.dp))
                
                DetailRow("CALORIES", "${meal.calories} KCAL")
                DetailRow("PROTEIN", "${meal.proteinGrams.toInt()}G")
                DetailRow("CARBS", "${meal.carbsGrams.toInt()}G")
                DetailRow("FAT", "${meal.fatGrams.toInt()}G")

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(thickness = 2.dp, color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(24.dp))

                // Geospatial Intelligence
                Text("GEOSPATIAL INTELLIGENCE", color = Color(0xFF00E5FF), fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(16.dp))
                
                val coordText = if (meal.latitude != null && meal.longitude != null) {
                    "${"%.6f".format(meal.latitude)}, ${"%.6f".format(meal.longitude)}"
                } else {
                    "UNKNOWN (SIGNAL JAMMED)"
                }
                DetailRow("COORDINATES", coordText)

                Spacer(modifier = Modifier.height(48.dp))

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.deleteMealEntry(meal.id)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PURGE RECORD", fontWeight = FontWeight.Black)
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (complianceStatus == ComplianceStatus.SUBVERSIVE) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Red.copy(alpha = 0.6f),
                shape = RectangleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "[ EVIDENCE ACCESS REVOKED ]\nSUBVERSIVE BEHAVIOR DETECTED",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
    }
}
