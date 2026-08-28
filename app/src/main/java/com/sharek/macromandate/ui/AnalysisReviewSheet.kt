package com.sharek.macromandate.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sharek.macromandate.ui.theme.NutritionColors
import com.sharek.macromandate.util.ParsedNutrition
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

    AlertDialog(
        // Not dismissible by an outside tap: the result is not saved anywhere yet,
        // so a stray touch would silently lose the analysis the user just paid for.
        onDismissRequest = {},
        title = {
            Column {
                Text(
                    text = "Check this estimate",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Estimated from your photo by AI. Correct anything that looks wrong before saving.",
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

                pending.caveat?.let { caveat ->
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
                                text = caveat,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = foodName,
                    onValueChange = { foodName = it },
                    label = { Text("Item name") },
                    singleLine = true,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = caloriesStr,
                    onValueChange = { caloriesStr = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text("Calories (kcal)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MacroField("Protein", proteinStr, NutritionColors.Protein, Modifier.weight(1f)) { proteinStr = it }
                    MacroField("Carbs", carbsStr, NutritionColors.Carbs, Modifier.weight(1f)) { carbsStr = it }
                    MacroField("Fat", fatStr, NutritionColors.Fat, Modifier.weight(1f)) { fatStr = it }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // One target with one role, rather than a checkbox and a
                        // separate tappable row that TalkBack announces twice.
                        .toggleableRow(isLiquid) { isLiquid = it }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isLiquid,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Drink rather than food",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        pending.nutrition.copy(
                            foodName = foodName,
                            calories = caloriesStr.toIntOrNull() ?: pending.nutrition.calories,
                            proteinGrams = parseGrams(proteinStr),
                            carbsGrams = parseGrams(carbsStr),
                            fatGrams = parseGrams(fatStr),
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
                Text("Save to log", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDiscard, shape = RectangleShape) {
                Text("Discard", color = Color.Gray)
            }
        },
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun MacroField(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(sanitizeDecimalInput(it)) },
        label = { Text(label, color = accent) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RectangleShape,
        suffix = { Text("g", style = MaterialTheme.typography.labelSmall) },
        modifier = modifier.semantics { contentDescription = "$label in grams" }
    )
}


/** A whole row that behaves — and is announced — as a single checkbox. */
private fun Modifier.toggleableRow(checked: Boolean, onToggle: (Boolean) -> Unit): Modifier =
    this
        .clickable(role = Role.Checkbox) { onToggle(!checked) }
