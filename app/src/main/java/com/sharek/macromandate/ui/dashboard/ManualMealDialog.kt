package com.sharek.macromandate.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sharek.macromandate.R
import com.sharek.macromandate.ui.MealEntryFields
import com.sharek.macromandate.ui.isMealEntryValid
import com.sharek.macromandate.ui.parseCalories
import com.sharek.macromandate.ui.parseGrams
import com.sharek.macromandate.viewmodel.SaveResult
import kotlinx.coroutines.launch

/**
 * Dialog for manually entering a meal into the log.
 *
 * Truthful mutation contract: the dialog does NOT dismiss until the write actually
 * succeeds. If persistence fails, the entered values remain intact and a localized
 * error message is displayed, allowing the user to retry without losing input.
 */
@Composable
fun ManualMealDialog(
    onDismiss: () -> Unit,
    onSave: suspend (foodName: String, calories: Int, protein: Float, carbs: Float, fat: Float, isLiquid: Boolean) -> SaveResult
) {
    var foodName by rememberSaveable { mutableStateOf("") }
    var caloriesStr by rememberSaveable { mutableStateOf("") }
    var proteinStr by rememberSaveable { mutableStateOf("") }
    var carbsStr by rememberSaveable { mutableStateOf("") }
    var fatStr by rememberSaveable { mutableStateOf("") }
    var isLiquid by rememberSaveable { mutableStateOf(false) }

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
                text = stringResource(R.string.manual_entry_title),
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
                        val result = onSave(
                            foodName,
                            parseCalories(caloriesStr) ?: 0,
                            parseGrams(proteinStr),
                            parseGrams(carbsStr),
                            parseGrams(fatStr),
                            isLiquid
                        )
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
                    Text(stringResource(R.string.manual_entry_save), fontWeight = FontWeight.Black)
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
