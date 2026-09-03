package com.sharek.macromandate.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sharek.macromandate.util.ParsedNutrition
import com.sharek.macromandate.viewmodel.PendingAnalysis
import androidx.compose.ui.res.stringResource
import com.sharek.macromandate.R

/**
 * The confirmation step between a model's answer and the user's meal log.
 *
 * Analysis results used to be written straight to the database, so the first
 * time anyone saw an estimate it was already counted in the day's totals. This
 * sheet exists so the numbers are *proposed* rather than *recorded*: every field
 * is editable in place, the origin of the figures is stated plainly, and
 * discarding costs one tap and leaves nothing behind.
 *
 * The dystopian voice deliberately stays out of this screen. Everywhere else the
 * chrome can be theatrical; here the user is deciding what is true about what
 * they ate, and the copy has to get out of the way.
 */
@Composable
fun AnalysisReviewSheet(
    pending: PendingAnalysis,
    onConfirm: (ParsedNutrition) -> Unit,
    onDiscard: () -> Unit
) {
    // rememberSaveable so a rotation mid-review does not throw away corrections
    // the user has already typed — and, with them, the analysis itself.
    var foodName by rememberSaveable(pending.capturedAt) { mutableStateOf(pending.nutrition.foodName) }
    var caloriesStr by rememberSaveable(pending.capturedAt) { mutableStateOf(pending.nutrition.calories.toString()) }
    var proteinStr by rememberSaveable(pending.capturedAt) { mutableStateOf(formatGramsValue(pending.nutrition.proteinGrams)) }
    var carbsStr by rememberSaveable(pending.capturedAt) { mutableStateOf(formatGramsValue(pending.nutrition.carbsGrams)) }
    var fatStr by rememberSaveable(pending.capturedAt) { mutableStateOf(formatGramsValue(pending.nutrition.fatGrams)) }
    var isLiquid by rememberSaveable(pending.capturedAt) { mutableStateOf(pending.nutrition.isLiquid) }

    // Same requirement as manual entry and edit: a name and a calorie figure,
    // typed explicitly. This previously fell back to the model's original
    // estimate on a blank field rather than defaulting to zero, which was
    // safe, but it meant clearing a field and tapping Save silently kept the
    // AI's number with no sign the correction hadn't taken effect.
    val isValid = isMealEntryValid(foodName, caloriesStr)

    AlertDialog(
        // Not dismissible by an outside tap: the result is not saved anywhere yet,
        // so a stray touch would silently lose the analysis the user just paid for.
        onDismissRequest = {},
        title = {
            Column {
                Text(
                    text = stringResource(R.string.analysis_review_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.analysis_review_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = pending.sourceImage,
                    // The photo is context for the numbers beside it, not
                    // information on its own; TalkBack should skip it.
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentScale = ContentScale.Crop
                )

                pending.caveatRes?.let { caveatRes ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(caveatRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                MealEntryFields(
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
                    onLiquidChange = { isLiquid = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        pending.nutrition.copy(
                            foodName = foodName,
                            calories = parseCalories(caloriesStr) ?: pending.nutrition.calories,
                            proteinGrams = parseGrams(proteinStr),
                            carbsGrams = parseGrams(carbsStr),
                            fatGrams = parseGrams(fatStr),
                            isLiquid = isLiquid
                        )
                    )
                },
                enabled = isValid,
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(R.string.analysis_save), fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDiscard, shape = RectangleShape) {
                Text(stringResource(R.string.analysis_discard), color = Color.Gray)
            }
        },
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}
