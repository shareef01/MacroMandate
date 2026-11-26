package com.sharek.macromandate.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.ui.hudFraming
import com.sharek.macromandate.ui.theme.NutritionColors
import com.sharek.macromandate.ui.theme.TerminalDesign
import com.sharek.macromandate.util.EvidenceStore
import com.sharek.macromandate.util.NutritionFormat

/**
 * Compact, cohesive terminal top bar used across top-level and detail screens.
 */
@Composable
fun TerminalTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val tokens = TerminalDesign.tokens
    val haptic = LocalHapticFeedback.current

    Surface(
        color = tokens.background,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = tokens.spacingLg, vertical = tokens.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onBack()
                        },
                        modifier = Modifier.size(tokens.minTouchTarget)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = tokens.primaryText
                        )
                    }
                    Spacer(modifier = Modifier.width(tokens.spacingSm))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = tokens.primaryText,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.secondaryText
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm),
                    content = actions
                )
            }
            HorizontalDivider(thickness = 1.dp, color = tokens.border)
        }
    }
}

/**
 * Reusable terminal section header with optional right-aligned badge or action.
 */
@Composable
fun TerminalSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val tokens = TerminalDesign.tokens
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .background(tokens.primaryAccent)
            )
            Spacer(modifier = Modifier.width(tokens.spacingSm))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = tokens.primaryAccent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
        }
        if (trailing != null) {
            trailing()
        } else if (!badge.isNullOrBlank()) {
            Surface(
                color = tokens.elevatedSurface,
                shape = tokens.controlShape,
                border = BorderStroke(1.dp, tokens.border)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Reusable terminal card container with semantic surface and border tokens.
 */
@Composable
fun TerminalCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    accentBorder: Color? = null,
    withHudCorners: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = TerminalDesign.tokens
    val containerColor = if (elevated) tokens.elevatedSurface else tokens.surface
    val borderColor = accentBorder ?: tokens.border
    val baseModifier = modifier
        .fillMaxWidth()
        .then(if (withHudCorners) Modifier.hudFraming(tokens.primaryAccent.copy(alpha = 0.6f)) else Modifier)

    Card(
        modifier = if (onClick != null) {
            baseModifier.clickable(onClick = onClick)
        } else {
            baseModifier
        },
        shape = tokens.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = tokens.primaryText
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Primary action button adhering to 48dp minimum touch target and terminal styling.
 */
@Composable
fun TerminalPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isDestructive: Boolean = false
) {
    val tokens = TerminalDesign.tokens
    val container = if (isDestructive) tokens.error else tokens.primaryAccent
    val contentColor = tokens.background

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = tokens.minTouchTarget),
        shape = tokens.controlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = contentColor,
            disabledContainerColor = tokens.elevatedSurface,
            disabledContentColor = tokens.disabledText
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Secondary outlined button adhering to 48dp minimum touch target and terminal styling.
 */
@Composable
fun TerminalSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accentColor: Color? = null
) {
    val tokens = TerminalDesign.tokens
    val activeColor = accentColor ?: tokens.primaryAccent
    val borderColor = if (enabled) activeColor.copy(alpha = 0.75f) else tokens.border

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = tokens.minTouchTarget),
        shape = tokens.controlShape,
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = tokens.surface,
            contentColor = activeColor,
            disabledContainerColor = tokens.disabledSurface,
            disabledContentColor = tokens.disabledText
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Empty state component with clear title, explanation, and optional primary action.
 */
@Composable
fun TerminalEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val tokens = TerminalDesign.tokens
    TerminalCard(
        modifier = modifier,
        contentPadding = PaddingValues(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tokens.primaryAccent,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = tokens.primaryText,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.secondaryText,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TerminalSecondaryButton(
                    text = actionLabel,
                    onClick = onAction
                )
            }
        }
    }
}

/**
 * Full-screen or modal loading overlay with genuine indeterminate progress and a Cancel button.
 */
@Composable
fun TerminalLoadingOverlay(
    title: String,
    subtitle: String? = null,
    onCancel: (() -> Unit)? = null
) {
    val tokens = TerminalDesign.tokens
    val haptic = LocalHapticFeedback.current

    Surface(
        color = tokens.background.copy(alpha = 0.88f),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            TerminalCard(
                elevated = true,
                withHudCorners = true,
                accentBorder = tokens.primaryAccent.copy(alpha = 0.5f),
                modifier = Modifier.widthIn(max = 360.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        color = tokens.primaryAccent,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.primaryText,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.secondaryText,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (onCancel != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        TerminalSecondaryButton(
                            text = "CANCEL",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onCancel()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cohesive meal row displaying thumbnail (when present), meal name, local time,
 * calories, fractional/rounded macros, category icon, and accessible delete action.
 */
@Composable
fun TerminalMealRow(
    entry: MealEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    showFullDate: Boolean = true
) {
    val tokens = TerminalDesign.tokens
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val verifiedImageUri = remember(entry.imageUri) {
        entry.imageUri?.takeIf { raw ->
            runCatching { EvidenceStore.ownedFile(context, raw.toUri())?.isFile == true }
                .getOrDefault(false)
        }
    }

    val timeLabel = remember(entry.timestamp, showFullDate) {
        if (showFullDate) {
            NutritionFormat.formatLocalTimestamp(entry.timestamp)
        } else {
            NutritionFormat.formatLocalTime(entry.timestamp)
        }
    }
    val pText = NutritionFormat.formatGramsValue(entry.proteinGrams)
    val cText = NutritionFormat.formatGramsValue(entry.carbsGrams)
    val fText = NutritionFormat.formatGramsValue(entry.fatGrams)
    val rowSemantics = "${entry.foodName}, ${entry.calories} calories. " +
        NutritionFormat.macroContentDescription(entry.proteinGrams, entry.carbsGrams, entry.fatGrams) +
        " Logged at $timeLabel."

    TerminalCard(
        modifier = modifier,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (verifiedImageUri != null) {
                AsyncImage(
                    model = verifiedImageUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(tokens.controlShape)
                        .border(1.dp, tokens.border, tokens.controlShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Surface(
                    color = tokens.elevatedSurface,
                    shape = tokens.controlShape,
                    border = BorderStroke(1.dp, tokens.border),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (entry.isLiquid) Icons.Default.LocalDrink else Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = tokens.primaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = rowSemantics }
            ) {
                Text(
                    text = entry.foodName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.primaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MacroPill(label = "P", value = "${pText}g", color = NutritionColors.Protein)
                    MacroPill(label = "C", value = "${cText}g", color = NutritionColors.Carbs)
                    MacroPill(label = "F", value = "${fText}g", color = NutritionColors.Fat)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText
                    )
                    if (entry.isLiquid) {
                        Text(
                            text = "· LIQUID",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.secondaryText
                        )
                    }
                    if (entry.latitude != null && entry.longitude != null) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location tagged",
                            tint = tokens.primaryAccent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${entry.calories}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = tokens.primaryAccent
                )
                Text(
                    text = "KCAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText
                )
            }

            if (onDelete != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDelete()
                    },
                    modifier = Modifier
                        .size(tokens.minTouchTarget)
                        .semantics {
                            contentDescription = "Delete ${entry.foodName}"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = tokens.secondaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MacroPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = TerminalDesign.tokens.primaryText,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Shared meal-entry form fields used by Manual Entry, AI Estimate Review,
 * Barcode Review, and Meal Editing.
 *
 * Features:
 * - Decimal input sanitization supporting both `.` and `,` separators.
 * - Full IME focus chaining (Name -> Calories -> Protein -> Carbs -> Fat -> Notes -> Done).
 * - One-tap "Calculate from macros" helper when macros are entered.
 * - Accessible unit descriptions and scalable typography (never clips at 200% font scale).
 * - Single-role toggleable row for Liquid / Beverage classification.
 */
@Composable
fun SharedMealFormFields(
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
    modifier: Modifier = Modifier,
    notes: String = "",
    onNotesChange: ((String) -> Unit)? = null,
    enabled: Boolean = true
) {
    val tokens = TerminalDesign.tokens
    val focusManager = LocalFocusManager.current
    val caloriesFocus = remember { FocusRequester() }
    val proteinFocus = remember { FocusRequester() }
    val carbsFocus = remember { FocusRequester() }
    val fatFocus = remember { FocusRequester() }
    val notesFocus = remember { FocusRequester() }

    val parsedP = NutritionFormat.parseGrams(proteinStr)
    val parsedC = NutritionFormat.parseGrams(carbsStr)
    val parsedF = NutritionFormat.parseGrams(fatStr)
    val estimatedKcal = NutritionFormat.estimateCaloriesFromMacros(parsedP, parsedC, parsedF)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = foodName,
            onValueChange = onFoodNameChange,
            enabled = enabled,
            label = { Text("Meal or item name *") },
            placeholder = { Text("e.g. Grilled Salmon & Rice") },
            singleLine = true,
            shape = tokens.controlShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { caloriesFocus.requestFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Meal or item name" }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = caloriesStr,
                onValueChange = { onCaloriesChange(NutritionFormat.sanitizeIntegerInput(it)) },
                enabled = enabled,
                label = { Text("Calories (kcal) *") },
                suffix = { Text("kcal", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                shape = tokens.controlShape,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { proteinFocus.requestFocus() }),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(caloriesFocus)
                    .semantics { contentDescription = "Calories in kilocalories" }
            )

            if (estimatedKcal > 0 && caloriesStr != estimatedKcal.toString()) {
                OutlinedButton(
                    onClick = { onCaloriesChange(estimatedKcal.toString()) },
                    enabled = enabled,
                    shape = tokens.controlShape,
                    border = BorderStroke(1.dp, tokens.primaryAccent.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = tokens.minTouchTarget)
                        .semantics {
                            contentDescription = "Set calories from macros: $estimatedKcal calories"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = null,
                        tint = tokens.primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$estimatedKcal",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.primaryAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Responsive macro fields: 3 columns on normal font scales, adaptable without clipping labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MacroDecimalField(
                label = "Protein",
                value = proteinStr,
                accent = NutritionColors.Protein,
                enabled = enabled,
                imeAction = ImeAction.Next,
                onImeAction = { carbsFocus.requestFocus() },
                onValueChange = onProteinChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(proteinFocus)
            )
            MacroDecimalField(
                label = "Carbs",
                value = carbsStr,
                accent = NutritionColors.Carbs,
                enabled = enabled,
                imeAction = ImeAction.Next,
                onImeAction = { fatFocus.requestFocus() },
                onValueChange = onCarbsChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(carbsFocus)
            )
            MacroDecimalField(
                label = "Fat",
                value = fatStr,
                accent = NutritionColors.Fat,
                enabled = enabled,
                imeAction = if (onNotesChange != null) ImeAction.Next else ImeAction.Done,
                onImeAction = {
                    if (onNotesChange != null) notesFocus.requestFocus() else focusManager.clearFocus()
                },
                onValueChange = onFatChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(fatFocus)
            )
        }

        if (onNotesChange != null) {
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                enabled = enabled,
                label = { Text("Notes / portion details (optional)") },
                maxLines = 3,
                shape = tokens.controlShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(notesFocus)
            )
        }

        // Accessible single-role checkbox row with >= 48dp touch height
        Surface(
            color = tokens.elevatedSurface.copy(alpha = 0.5f),
            shape = tokens.controlShape,
            border = BorderStroke(1.dp, tokens.border),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = tokens.minTouchTarget)
                .clickable(
                    enabled = enabled,
                    role = Role.Checkbox,
                    onClick = { onLiquidChange(!isLiquid) }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isLiquid,
                    onCheckedChange = null,
                    enabled = enabled,
                    colors = CheckboxDefaults.colors(
                        checkedColor = tokens.primaryAccent,
                        checkmarkColor = tokens.background
                    )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Liquid consumption (beverage or shake)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.primaryText,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Text(
            text = "* Item name and calories are required. Fractional grams (e.g. 12.5) are supported.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.secondaryText
        )
    }
}

@Composable
private fun MacroDecimalField(
    label: String,
    value: String,
    accent: Color,
    enabled: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = TerminalDesign.tokens
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(NutritionFormat.sanitizeDecimalInput(it)) },
        enabled = enabled,
        label = {
            Text(
                text = label,
                color = accent,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        },
        suffix = {
            Text(
                text = "g",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText
            )
        },
        singleLine = true,
        shape = tokens.controlShape,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() }
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = modifier.semantics {
            contentDescription = "$label in grams"
        }
    )
}
