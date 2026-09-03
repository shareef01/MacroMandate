package com.sharek.macromandate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.sharek.macromandate.R
import com.sharek.macromandate.ui.theme.NutritionColors

/**
 * The name/calories/macros/liquid form body shared by manual entry, edit, and
 * the AI review sheet.
 *
 * These were three independent copies of the same five fields. They had
 * already drifted once — two different labels for the liquid checkbox, one
 * dialog validating required fields and two silently falling back on blank
 * input — before the fix for that drift was to make all three call the same
 * component instead of matching the other two by hand a third time.
 *
 * Deliberately stateless: each caller owns its fields' `rememberSaveable`
 * state (with its own keying — the review sheet resets on a new capture,
 * the others don't) and passes values/callbacks down, so this component has
 * no opinion about where the values come from or what saving them means.
 */
@Composable
fun MealEntryFields(
    foodName: String,
    onFoodNameChange: (String) -> Unit,
    caloriesStr: String,
    onCaloriesChange: (String) -> Unit,
    proteinStr: String,
    onProteinChange: (String) -> Unit,
    carbsStr: String,
    onCarbsChange: (String) -> Unit,
    fatStr: String,
    onFatChange: (String) -> Unit,
    isLiquid: Boolean,
    onLiquidChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val caloriesFocus = remember { FocusRequester() }
    val proteinFocus = remember { FocusRequester() }
    val carbsFocus = remember { FocusRequester() }
    val fatFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = foodName,
            onValueChange = onFoodNameChange,
            label = { Text(stringResource(R.string.field_item_name)) },
            singleLine = true,
            shape = RectangleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { caloriesFocus.requestFocus() }),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = caloriesStr,
            onValueChange = { onCaloriesChange(it.filter { ch -> ch.isDigit() }.take(6)) },
            label = { Text(stringResource(R.string.field_calories)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { proteinFocus.requestFocus() }),
            shape = RectangleShape,
            modifier = Modifier.fillMaxWidth().focusRequester(caloriesFocus)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MacroField(
                R.string.field_protein, proteinStr, NutritionColors.Protein,
                Modifier.weight(1f).focusRequester(proteinFocus),
                ImeAction.Next, { carbsFocus.requestFocus() }, onProteinChange
            )
            MacroField(
                R.string.field_carbs, carbsStr, NutritionColors.Carbs,
                Modifier.weight(1f).focusRequester(carbsFocus),
                ImeAction.Next, { fatFocus.requestFocus() }, onCarbsChange
            )
            MacroField(
                R.string.field_fat, fatStr, NutritionColors.Fat,
                Modifier.weight(1f).focusRequester(fatFocus),
                ImeAction.Done, { focusManager.clearFocus() }, onFatChange
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // One target with one role, rather than a checkbox and a
                // separate tappable row that TalkBack announces twice.
                .toggleableRow(isLiquid, onLiquidChange)
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
                text = stringResource(R.string.field_is_liquid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = stringResource(R.string.manual_entry_required_hint),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
}

/** Whether a name and an explicitly-typed calorie figure are present — the one save-gate all three meal-entry forms share. */
fun isMealEntryValid(foodName: String, caloriesStr: String): Boolean =
    foodName.isNotBlank() && caloriesStr.isNotBlank() && parseCalories(caloriesStr) != null

@Composable
private fun MacroField(
    @StringRes labelRes: Int,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: () -> Unit = {},
    onValueChange: (String) -> Unit
) {
    val label = stringResource(labelRes)
    val fieldDescription = stringResource(R.string.field_grams_description, label)
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(sanitizeDecimalInput(it)) },
        label = { Text(label, color = accent) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() }
        ),
        shape = RectangleShape,
        suffix = { Text(stringResource(R.string.field_grams_suffix), style = MaterialTheme.typography.labelSmall) },
        // "Protein" alone reads as a heading; the unit belongs in the description
        // so a screen reader user knows what the field wants.
        modifier = modifier.semantics { contentDescription = fieldDescription }
    )
}

/** A whole row that behaves — and is announced — as a single checkbox. */
private fun Modifier.toggleableRow(checked: Boolean, onToggle: (Boolean) -> Unit): Modifier =
    this.clickable(role = Role.Checkbox) { onToggle(!checked) }
