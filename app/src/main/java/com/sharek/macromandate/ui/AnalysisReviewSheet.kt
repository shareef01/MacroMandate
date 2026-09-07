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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sharek.macromandate.R
import com.sharek.macromandate.util.ParsedNutrition
import com.sharek.macromandate.viewmodel.AnalysisCommitState
import com.sharek.macromandate.viewmodel.PendingAnalysis

/**
 * The confirmation step between a model's answer and the user's meal log.
 *
 * Analysis results used to be written straight to the database, so the first
 * time anyone saw an estimate it was already counted in the day's totals. This
 * sheet exists so the numbers are *proposed* rather than *recorded*: every field
 * is editable in place, the origin of the figures is stated plainly, and
 * discarding costs one tap and leaves nothing behind.
 *
 * Now handles an explicit [commitState] state machine: while saving, buttons are
 * disabled to prevent duplicate writes and race conditions; if Room or disk
 * persistence fails, entered corrections remain visible and the error is surfaced
 * so the user can retry without losing their analysis.
 */
@Composable
fun AnalysisReviewSheet(
    pending: PendingAnalysis,
    commitState: AnalysisCommitState = AnalysisCommitState.Idle,
    onConfirm: (ParsedNutrition) -> Unit,
    onDiscard: () -> Unit
) {
    var foodName by rememberSaveable(pending.capturedAt) { mutableStateOf(pending.nutrition.foodName) }
    var caloriesStr by rememberSaveable(pending.capturedAt) { mutableStateOf(pending.nutrition.calories.toString()) }
    var proteinStr by rememberSaveable(pending.capturedAt) { mutableStateOf(formatGramsValue(pending.nutrition.proteinGrams)) }
    var carbsStr by rememberSaveable(pending.capturedAt) { mutableStateOf(formatGramsValue(pending.nutrition.carbsGrams)) }
    var fatStr by rememberSaveable(pending.capturedAt) { mutableStateOf(formatGramsValue(pending.nutrition.fatGrams)) }
    var isLiquid by rememberSaveable(pending.capturedAt) { mutableStateOf(pending.nutrition.isLiquid) }

    val isSaving = commitState is AnalysisCommitState.Saving
    val isValid = isMealEntryValid(foodName, caloriesStr) && !isSaving

    AlertDialog(
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
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentScale = ContentScale.Crop
                )

                if (commitState is AnalysisCommitState.Failed) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(commitState.messageRes),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

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
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_saving), fontWeight = FontWeight.Black)
                } else {
                    Text(stringResource(R.string.analysis_save), fontWeight = FontWeight.Black)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDiscard,
                enabled = !isSaving,
                shape = RectangleShape
            ) {
                Text(stringResource(R.string.analysis_discard), color = Color.Gray)
            }
        },
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}
