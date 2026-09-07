package com.sharek.macromandate.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sharek.macromandate.R
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.ui.theme.NutritionColors
import com.sharek.macromandate.viewmodel.MainViewModel
import com.sharek.macromandate.viewmodel.SaveResult
import kotlinx.coroutines.launch
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
    val coroutineScope = rememberCoroutineScope()
    val mealEntries by viewModel.mealEntries.collectAsStateWithLifecycle()
    val meal = mealEntries.find { it.id == mealId }
    val dateFormat = remember { SimpleDateFormat("EEEE d MMMM, HH:mm z", Locale.getDefault()) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (meal == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.detail_unavailable),
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Black
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
        ) {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_meal), fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back)
                        )
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
                    .hudFraming(if (meal.isRestricted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, length = 40.dp, thickness = 4.dp)
            ) {
                AsyncImage(
                    model = meal.imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.detail_sensor_telemetry),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (meal.isRestricted) {
                            PhotoBadge(
                                stringResource(R.string.badge_restricted),
                                MaterialTheme.colorScheme.error
                            )
                        }
                        if (meal.isNightRefueling) {
                            PhotoBadge(
                                stringResource(R.string.badge_late_refueling),
                                Color(0xFFFFB300)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                if (meal.assessment != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.detail_assessment),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                meal.assessment,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    text = meal.id.take(8).uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Text(
                    text = dateFormat.format(Date(meal.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(thickness = 2.dp, color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(24.dp))

                // Macro Interrogation
                Text(
                    stringResource(R.string.detail_nutrition),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(16.dp))

                DetailRow(
                    stringResource(R.string.detail_calories),
                    stringResource(R.string.detail_kcal, meal.calories),
                    MaterialTheme.colorScheme.primary
                )
                DetailRow(stringResource(R.string.field_protein), formatGrams(meal.proteinGrams), NutritionColors.Protein)
                DetailRow(stringResource(R.string.field_carbs), formatGrams(meal.carbsGrams), NutritionColors.Carbs)
                DetailRow(stringResource(R.string.field_fat), formatGrams(meal.fatGrams), NutritionColors.Fat)
                DetailRow(
                    stringResource(R.string.detail_type),
                    stringResource(
                        if (meal.isLiquid) R.string.detail_type_liquid else R.string.detail_type_solid
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(thickness = 2.dp, color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(24.dp))

                // Geospatial Intelligence
                Text(
                    stringResource(R.string.detail_location),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(16.dp))

                val locale = LocalConfiguration.current.locales[0]
                val coordText = if (meal.latitude != null && meal.longitude != null) {
                    stringResource(
                        R.string.detail_geotagged,
                        String.format(locale, "%.6f", meal.latitude),
                        String.format(locale, "%.6f", meal.longitude)
                    )
                } else {
                    stringResource(R.string.detail_no_coordinates)
                }
                DetailRow(stringResource(R.string.detail_coordinates), coordText)

                Spacer(modifier = Modifier.height(40.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            showEditDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.detail_edit), fontWeight = FontWeight.Black)
                    }

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            showDeleteConfirmDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RectangleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.detail_delete), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (showEditDialog) {
            EditMealDialog(
                meal = meal,
                onDismiss = { showEditDialog = false },
                onSave = { updated ->
                    viewModel.updateMealEntry(updated)
                }
            )
        }

        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = {
                    Text(
                        stringResource(R.string.delete_meal_title),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text(
                        stringResource(R.string.delete_meal_body, meal.foodName, meal.calories),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            coroutineScope.launch {
                                viewModel.deleteMealEntry(meal.id)
                                showDeleteConfirmDialog = false
                                onBack()
                            }
                        },
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(stringResource(R.string.delete_meal_confirm_detail), fontWeight = FontWeight.Black)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = false },
                        shape = RectangleShape
                    ) {
                        Text(stringResource(R.string.action_cancel), color = Color.Gray)
                    }
                },
                shape = RectangleShape,
                containerColor = Color(0xFF181818)
            )
        }
    }
}

@Composable
fun EditMealDialog(
    meal: MealEntry,
    onDismiss: () -> Unit,
    onSave: suspend (MealEntry) -> SaveResult
) {
    var foodName by rememberSaveable { mutableStateOf(meal.foodName) }
    var caloriesStr by rememberSaveable { mutableStateOf(meal.calories.toString()) }
    var proteinStr by rememberSaveable { mutableStateOf(formatGramsValue(meal.proteinGrams)) }
    var carbsStr by rememberSaveable { mutableStateOf(formatGramsValue(meal.carbsGrams)) }
    var fatStr by rememberSaveable { mutableStateOf(formatGramsValue(meal.fatGrams)) }
    var isLiquid by rememberSaveable { mutableStateOf(meal.isLiquid) }

    var isSaving by rememberSaveable { mutableStateOf(false) }
    var errorMessageRes by rememberSaveable { mutableStateOf<Int?>(null) }

    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val isValid = isMealEntryValid(foodName, caloriesStr) && !isSaving

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        title = {
            Text(
                text = stringResource(R.string.edit_meal_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                errorMessageRes?.let { errRes ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(errRes),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                MealEntryFields(
                    foodName = foodName,
                    onFoodNameChange = { if (!isSaving) foodName = it },
                    caloriesStr = caloriesStr,
                    onCaloriesChange = { if (!isSaving) caloriesStr = it },
                    proteinStr = proteinStr,
                    onProteinChange = { if (!isSaving) proteinStr = it },
                    carbsStr = carbsStr,
                    onCarbsChange = { if (!isSaving) carbsStr = it },
                    fatStr = fatStr,
                    onFatChange = { if (!isSaving) fatStr = it },
                    isLiquid = isLiquid,
                    onLiquidChange = { if (!isSaving) isLiquid = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isSaving = true
                    errorMessageRes = null
                    coroutineScope.launch {
                        val updated = meal.copy(
                            foodName = foodName,
                            calories = (parseCalories(caloriesStr) ?: meal.calories).coerceAtLeast(0),
                            proteinGrams = parseGrams(proteinStr).coerceAtLeast(0f),
                            carbsGrams = parseGrams(carbsStr).coerceAtLeast(0f),
                            fatGrams = parseGrams(fatStr).coerceAtLeast(0f),
                            isLiquid = isLiquid
                        )
                        val result = onSave(updated)
                        isSaving = false
                        when (result) {
                            is SaveResult.Success -> onDismiss()
                            is SaveResult.Failure -> errorMessageRes = result.messageRes
                        }
                    }
                },
                enabled = isValid,
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_saving), fontWeight = FontWeight.Black)
                } else {
                    Text(stringResource(R.string.edit_meal_save), fontWeight = FontWeight.Black)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSaving,
                shape = RectangleShape
            ) {
                Text(stringResource(R.string.action_cancel), color = Color.Gray)
            }
        },
        shape = RectangleShape,
        containerColor = Color(0xFF141414)
    )
}

/** A small tag over a corner of the meal photo — a fact, not a warning wash. */
@Composable
private fun PhotoBadge(text: String, background: Color) {
    Surface(color = background, shape = RectangleShape) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    accentColor: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
    }
}
