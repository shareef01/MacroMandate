package com.sharek.macromandate.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.ui.theme.NutritionColors
import com.sharek.macromandate.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.stringResource
import com.sharek.macromandate.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(
    viewModel: MainViewModel,
    mealId: String,
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val mealEntries by viewModel.mealEntries.collectAsState()
    val meal = mealEntries.find { it.id == mealId }
    // Local time, honestly labelled. The old format string appended a literal
    // 'UTC' to a device-local timestamp.
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
                // Edge-to-edge: the app bar would otherwise sit under the status bar.
                .statusBarsPadding()
        ) {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_meal), fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    .hudFraming(if (meal.isRestricted) Color.Red else MaterialTheme.colorScheme.primary, length = 40.dp, thickness = 4.dp)
            ) {
                AsyncImage(
                    model = meal.imageUri,
                    // The photo repeats what the fields below already state.
                    contentDescription = null,
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
                            stringResource(R.string.filter_flagged),
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
                            stringResource(R.string.detail_late_night),
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
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.detail_assessment),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                it,
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

                // .toInt() truncated: 12.7 g of protein displayed as "12 g", and
                // every macro read low by up to a gram everywhere in the app.
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

                // Read from the configuration, not Locale.getDefault(): the latter
                // is invisible to Compose, so a locale change would leave these
                // coordinates formatted for the previous one until something else
                // happened to recompose them.
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
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    showEditDialog = false
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
                        stringResource(R.string.delete_meal_body_detail),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.deleteMealEntry(meal.id)
                            onBack()
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
    onSave: (MealEntry) -> Unit
) {
    var foodName by rememberSaveable { mutableStateOf(meal.foodName) }
    var caloriesStr by rememberSaveable { mutableStateOf(meal.calories.toString()) }
    var proteinStr by rememberSaveable { mutableStateOf(formatGramsValue(meal.proteinGrams)) }
    var carbsStr by rememberSaveable { mutableStateOf(formatGramsValue(meal.carbsGrams)) }
    var fatStr by rememberSaveable { mutableStateOf(formatGramsValue(meal.fatGrams)) }
    var isLiquid by rememberSaveable { mutableStateOf(meal.isLiquid) }

    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = foodName,
                    onValueChange = { foodName = it },
                    label = { Text(stringResource(R.string.field_item_name)) },
                    singleLine = true,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = caloriesStr,
                    onValueChange = { caloriesStr = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.field_calories)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = proteinStr,
                        onValueChange = { proteinStr = sanitizeDecimalInput(it) },
                        label = { Text(stringResource(R.string.field_protein)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RectangleShape,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbsStr,
                        onValueChange = { carbsStr = sanitizeDecimalInput(it) },
                        label = { Text(stringResource(R.string.field_carbs)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RectangleShape,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fatStr,
                        onValueChange = { fatStr = sanitizeDecimalInput(it) },
                        label = { Text(stringResource(R.string.field_fat)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RectangleShape,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isLiquid = !isLiquid }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isLiquid,
                        onCheckedChange = { isLiquid = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.field_is_liquid),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val cal = parseCalories(caloriesStr) ?: meal.calories
                    val p = parseGrams(proteinStr)
                    val c = parseGrams(carbsStr)
                    val f = parseGrams(fatStr)
                    onSave(
                        meal.copy(
                            foodName = foodName.ifBlank { meal.foodName },
                            calories = cal.coerceAtLeast(0),
                            proteinGrams = p.coerceAtLeast(0f),
                            carbsGrams = c.coerceAtLeast(0f),
                            fatGrams = f.coerceAtLeast(0f),
                            isLiquid = isLiquid
                        )
                    )
                },
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(R.string.edit_meal_save), fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RectangleShape) {
                Text(stringResource(R.string.action_cancel), color = Color.Gray)
            }
        },
        shape = RectangleShape,
        containerColor = Color(0xFF141414)
    )
}

@Composable
fun DetailRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Label is prose, value is data: the value keeps the terminal face so the
        // numbers stay aligned down the column.
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, style = MaterialTheme.typography.labelLarge)
    }
}
