package com.sharek.macromandate.ui

import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.ui.components.*
import com.sharek.macromandate.ui.theme.NutritionColors
import com.sharek.macromandate.ui.theme.TerminalDesign
import com.sharek.macromandate.util.EvidenceStore
import com.sharek.macromandate.util.NutritionFormat
import com.sharek.macromandate.viewmodel.MainViewModel
import com.sharek.macromandate.viewmodel.UiState
import kotlinx.coroutines.flow.catch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(
    viewModel: MainViewModel,
    mealId: String,
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val tokens = TerminalDesign.tokens

    val loadedMeal by produceState<Result<MealEntry?>?>(initialValue = null, mealId) {
        value = null
        viewModel.observeMeal(mealId)
            .catch { value = Result.failure(it) }
            .collect { value = Result.success(it) }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        val error = uiState as? UiState.Error ?: return@LaunchedEffect
        snackbar.showSnackbar(error.message)
        viewModel.resetUiState()
    }

    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmDialog by rememberSaveable { mutableStateOf(false) }

    val meal = loadedMeal?.getOrNull()
    if (meal == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.background)
        ) {
            TerminalTopBar(title = "MEAL RECORD", onBack = onBack)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    loadedMeal == null -> CircularProgressIndicator(color = tokens.primaryAccent)
                    loadedMeal?.isFailure == true -> TerminalEmptyState(
                        title = "COULD NOT LOAD MEAL",
                        message = "An error occurred while reading this meal record.",
                        actionLabel = "RETURN",
                        onAction = onBack
                    )
                    else -> TerminalEmptyState(
                        title = "MEAL NO LONGER AVAILABLE",
                        message = "This meal record has been deleted.",
                        actionLabel = "RETURN",
                        onAction = onBack
                    )
                }
            }
        }
        return
    }

    val verifiedImageUri = remember(meal.imageUri) {
        meal.imageUri?.takeIf { raw ->
            runCatching { EvidenceStore.ownedFile(context, raw.toUri())?.isFile == true }
                .getOrDefault(false)
        }
    }

    val pCal = meal.proteinGrams * 4f
    val cCal = meal.carbsGrams * 4f
    val fCal = meal.fatGrams * 9f
    val totalMacroCal = pCal + cCal + fCal
    val pShare = if (totalMacroCal > 0f) ((pCal / totalMacroCal) * 100).toInt() else 0
    val cShare = if (totalMacroCal > 0f) ((cCal / totalMacroCal) * 100).toInt() else 0
    val fShare = if (totalMacroCal > 0f) ((fCal / totalMacroCal) * 100).toInt() else 0

    val provenanceLabel = remember(meal.imageUri, meal.assessment) {
        when {
            meal.imageUri != null -> "Optical AI Estimate"
            meal.assessment?.contains("BARCODE", ignoreCase = true) == true -> "Barcode Lookup (OpenFoodFacts)"
            else -> "Manual Entry"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TerminalTopBar(
                title = "MEAL DOSSIER",
                subtitle = "ID: ${meal.id.take(8).uppercase()}",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            showEditDialog = true
                        },
                        modifier = Modifier.size(tokens.minTouchTarget)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit meal",
                            tint = tokens.primaryAccent
                        )
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            showDeleteConfirmDialog = true
                        },
                        modifier = Modifier.size(tokens.minTouchTarget)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete meal",
                            tint = tokens.error
                        )
                    }
                }
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
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Photograph section: collapsed completely when meal has no photograph
                    if (verifiedImageUri != null) {
                        TerminalCard(
                            withHudCorners = true,
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = verifiedImageUri,
                                    contentDescription = "Photograph of ${meal.foodName}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 180.dp, max = 320.dp)
                                        .clip(tokens.controlShape)
                                        .background(tokens.background)
                                )
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (meal.isRestricted) {
                                        DetailBadge(text = "RESTRICTED", background = tokens.error)
                                    }
                                    if (meal.isNightRefueling) {
                                        DetailBadge(text = "LATE REFUELING", background = tokens.warning)
                                    }
                                }
                            }
                        }
                    }

                    // Header Summary Card
                    TerminalCard(withHudCorners = verifiedImageUri == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = meal.foodName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.primaryText
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = NutritionFormat.formatLocalTimestamp(meal.timestamp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tokens.secondaryText
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                color = tokens.elevatedSurface,
                                shape = tokens.controlShape,
                                border = BorderStroke(1.dp, tokens.border)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (meal.isLiquid) Icons.Default.LocalDrink else Icons.Default.Restaurant,
                                        contentDescription = null,
                                        tint = tokens.primaryAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (meal.isLiquid) "LIQUID" else "SOLID",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.primaryText,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = tokens.divider)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Prominent Calorie Readout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "ENERGY INTAKE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.secondaryText
                                )
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "${meal.calories}",
                                        style = MaterialTheme.typography.displayMedium,
                                        color = tokens.primaryAccent,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "kcal",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = tokens.secondaryText,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                            }
                            if (meal.isNightRefueling && verifiedImageUri == null) {
                                DetailBadge(text = "LATE-NIGHT ENTRY", background = tokens.elevatedSurface, textColor = tokens.warning)
                            }
                        }
                    }

                    // Macronutrient Breakdown Card
                    TerminalCard {
                        TerminalSectionHeader(title = "Macronutrient Breakdown")
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MacroDetailTile(
                                label = "PROTEIN",
                                grams = NutritionFormat.formatGrams(meal.proteinGrams),
                                shareText = "$pShare% cal",
                                accent = NutritionColors.Protein,
                                modifier = Modifier.weight(1f)
                            )
                            MacroDetailTile(
                                label = "CARBS",
                                grams = NutritionFormat.formatGrams(meal.carbsGrams),
                                shareText = "$cShare% cal",
                                accent = NutritionColors.Carbs,
                                modifier = Modifier.weight(1f)
                            )
                            MacroDetailTile(
                                label = "FAT",
                                grams = NutritionFormat.formatGrams(meal.fatGrams),
                                shareText = "$fShare% cal",
                                accent = NutritionColors.Fat,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Notes & Provenance Card
                    TerminalCard {
                        TerminalSectionHeader(title = "Notes & Provenance")
                        Spacer(modifier = Modifier.height(12.dp))

                        DetailRow(label = "Source", value = provenanceLabel, valueColor = tokens.primaryText)
                        DetailRow(
                            label = "Category",
                            value = if (meal.isLiquid) "Liquid / Beverage" else "Solid Food",
                            valueColor = tokens.primaryText
                        )

                        if (!meal.assessment.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = tokens.elevatedSurface,
                                shape = tokens.controlShape,
                                border = BorderStroke(1.dp, tokens.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "ASSESSMENT / NOTES",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.primaryAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = meal.assessment,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tokens.primaryText
                                    )
                                }
                            }
                        }
                    }

                    // Geospatial Metadata Card
                    TerminalCard {
                        TerminalSectionHeader(title = "Location Metadata")
                        Spacer(modifier = Modifier.height(12.dp))

                        val locale = LocalConfiguration.current.locales[0]
                        val hasCoords = meal.latitude != null && meal.longitude != null
                        val coordText = if (hasCoords) {
                            String.format(locale, "%.5f, %.5f", meal.latitude, meal.longitude)
                        } else {
                            "Not recorded"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (hasCoords) Icons.Default.LocationOn else Icons.Default.LocationOff,
                                    contentDescription = null,
                                    tint = if (hasCoords) tokens.primaryAccent else tokens.secondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = coordText,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = if (hasCoords) tokens.primaryText else tokens.secondaryText
                                )
                            }
                            if (hasCoords) {
                                TextButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        viewModel.updateMealEntry(meal.copy(latitude = null, longitude = null))
                                    }
                                ) {
                                    Text("Remove tag", color = tokens.warning, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TerminalPrimaryButton(
                            text = "EDIT MEAL",
                            icon = Icons.Default.Edit,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                showEditDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalSecondaryButton(
                            text = "DELETE",
                            icon = Icons.Default.Delete,
                            accentColor = tokens.error,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                showDeleteConfirmDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
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
                        text = "DELETE MEAL RECORD?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.error
                    )
                },
                text = {
                    Text(
                        text = "Delete '${meal.foodName}' (${meal.calories} kcal)? This permanently removes the meal and any stored photo, and updates your daily and weekly totals.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.primaryText
                    )
                },
                confirmButton = {
                    TerminalPrimaryButton(
                        text = "DELETE",
                        isDestructive = true,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showDeleteConfirmDialog = false
                            viewModel.deleteMealEntry(meal.id, onBack)
                        }
                    )
                },
                dismissButton = {
                    TerminalSecondaryButton(
                        text = "CANCEL",
                        onClick = { showDeleteConfirmDialog = false }
                    )
                },
                shape = tokens.cardShape,
                containerColor = tokens.surface
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

@Composable
private fun MacroDetailTile(
    label: String,
    grams: String,
    shareText: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val tokens = TerminalDesign.tokens
    Surface(
        color = tokens.elevatedSurface,
        shape = tokens.controlShape,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = grams,
                style = MaterialTheme.typography.titleLarge,
                color = tokens.primaryText,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = shareText,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText
            )
        }
    }
}

@Composable
private fun DetailBadge(
    text: String,
    background: Color,
    textColor: Color = Color.Black
) {
    val tokens = TerminalDesign.tokens
    Surface(
        color = background,
        shape = tokens.controlShape,
        border = BorderStroke(1.dp, tokens.border)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EditMealDialog(
    meal: MealEntry,
    onDismiss: () -> Unit,
    onSave: (MealEntry) -> Unit
) {
    val tokens = TerminalDesign.tokens
    val haptic = LocalHapticFeedback.current

    var foodName by rememberSaveable(meal.id) { mutableStateOf(meal.foodName) }
    var caloriesStr by rememberSaveable(meal.id) { mutableStateOf(meal.calories.toString()) }
    var proteinStr by rememberSaveable(meal.id) { mutableStateOf(NutritionFormat.formatGramsValue(meal.proteinGrams)) }
    var carbsStr by rememberSaveable(meal.id) { mutableStateOf(NutritionFormat.formatGramsValue(meal.carbsGrams)) }
    var fatStr by rememberSaveable(meal.id) { mutableStateOf(NutritionFormat.formatGramsValue(meal.fatGrams)) }
    var isLiquid by rememberSaveable(meal.id) { mutableStateOf(meal.isLiquid) }
    var notes by rememberSaveable(meal.id) { mutableStateOf(meal.assessment.orEmpty()) }

    val isValid = NutritionFormat.isMealFormValid(foodName, caloriesStr, proteinStr, carbsStr, fatStr)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "EDIT MEAL RECORD",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.primaryAccent
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                SharedMealFormFields(
                    foodName = foodName,
                    onFoodNameChange = { foodName = it },
                    caloriesStr = caloriesStr,
                    onCaloriesChange = { caloriesStr = it },
                    proteinStr = proteinStr,
                    onProteinChange = { proteinStr = it },
                    carbsStr = carbsStr,
                    onCarbsChange = { carbsStr = it },
                    fatStr = fatStr,
                    onFatChange = { fatStr = it },
                    isLiquid = isLiquid,
                    onLiquidChange = { isLiquid = it },
                    notes = notes,
                    onNotesChange = { notes = it }
                )
            }
        },
        confirmButton = {
            TerminalPrimaryButton(
                text = "SAVE CHANGES",
                enabled = isValid,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val cal = NutritionFormat.parseCaloriesOrNull(caloriesStr) ?: meal.calories
                    val p = NutritionFormat.parseGramsOrNull(proteinStr) ?: meal.proteinGrams
                    val c = NutritionFormat.parseGramsOrNull(carbsStr) ?: meal.carbsGrams
                    val f = NutritionFormat.parseGramsOrNull(fatStr) ?: meal.fatGrams
                    onSave(
                        meal.copy(
                            foodName = foodName.trim().take(120).ifBlank { meal.foodName },
                            calories = cal.coerceIn(0, NutritionFormat.MAX_MEAL_CALORIES),
                            proteinGrams = p.coerceIn(0f, NutritionFormat.MAX_MEAL_MACRO_GRAMS),
                            carbsGrams = c.coerceIn(0f, NutritionFormat.MAX_MEAL_MACRO_GRAMS),
                            fatGrams = f.coerceIn(0f, NutritionFormat.MAX_MEAL_MACRO_GRAMS),
                            isLiquid = isLiquid,
                            assessment = notes.trim().take(500).ifBlank { null }
                        )
                    )
                }
            )
        },
        dismissButton = {
            TerminalSecondaryButton(
                text = "CANCEL",
                onClick = onDismiss
            )
        },
        shape = tokens.cardShape,
        containerColor = tokens.surface
    )
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified
) {
    val tokens = TerminalDesign.tokens
    val resolvedColor = if (valueColor == Color.Unspecified) tokens.primaryText else valueColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = tokens.secondaryText,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            color = resolvedColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
