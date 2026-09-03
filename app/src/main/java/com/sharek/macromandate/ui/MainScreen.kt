package com.sharek.macromandate.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sharek.macromandate.R
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.ui.theme.NutritionColors
import com.sharek.macromandate.viewmodel.ComplianceStatus
import com.sharek.macromandate.viewmodel.MainViewModel
import com.sharek.macromandate.viewmodel.UiState
import com.sharek.macromandate.ui.hudFraming
import java.text.SimpleDateFormat
import java.util.*
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource

enum class ScreenState {
    DASHBOARD, CAMERA
}

enum class MealFilter(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    TODAY(R.string.filter_today),
    HIGH_PROTEIN(R.string.filter_high_protein),
    HIGH_CAL(R.string.filter_high_calorie),
    LIQUID(R.string.filter_liquid),
    FLAGGED(R.string.filter_flagged)
}

enum class MealSortOrder(@StringRes val labelRes: Int) {
    NEWEST(R.string.sort_newest),
    HIGHEST_CAL(R.string.sort_calories),
    HIGHEST_PROTEIN(R.string.sort_protein)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToDetail: (String) -> Unit,
    openManualEntryOnLaunch: Boolean = false,
    onManualEntryLaunchConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()
    val complianceStatus by viewModel.complianceStatus.collectAsState()
    val locationTrackingEnabled by viewModel.locationTrackingEnabled.collectAsState()
    val hasApiKey by viewModel.hasApiKey.collectAsState()
    val target by viewModel.calorieTarget.collectAsState()
    val pendingAnalysis by viewModel.pendingAnalysis.collectAsState()

    var screenState by remember { mutableStateOf(ScreenState.DASHBOARD) }
    var showManualEntryDialog by remember { mutableStateOf(false) }
    var mealToDelete by remember { mutableStateOf<MealEntry?>(null) }

    // Seeding showManualEntryDialog's initial value directly from
    // openManualEntryOnLaunch re-opened the dialog every time this screen
    // re-entered composition — including an ordinary switch back to the Today
    // tab, long after the widget tap that set the flag. onManualEntryLaunchConsumed
    // clears the flag at its source (MainActivity) the moment it's acted on,
    // so a later recomposition of this screen sees it already consumed.
    LaunchedEffect(openManualEntryOnLaunch) {
        if (openManualEntryOnLaunch) {
            showManualEntryDialog = true
            onManualEntryLaunchConsumed()
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(MealFilter.ALL) }
    var sortOrder by remember { mutableStateOf(MealSortOrder.NEWEST) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                viewModel.processImageForMacros(it, context)
            }
        }
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
            if (cameraGranted) {
                screenState = ScreenState.CAMERA
            }
        }
    )

    // Resolved in composition, not inside the effect: a LaunchedEffect body is a
    // suspend lambda rather than a composable, so pulling a resource from inside
    // it has to go through LocalContext, which does not re-read on a
    // configuration change.
    val snackbarMessage = when (val state = uiState) {
        is UiState.Error -> stringResource(state.messageRes)
        is UiState.Success -> stringResource(R.string.analysis_logged, state.mealName)
        else -> null
    }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.resetUiState()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (screenState) {
            ScreenState.DASHBOARD -> {
                val mealEntries by viewModel.mealEntries.collectAsState()
                val todayMeals by viewModel.todayMeals.collectAsState()
                // The card is labelled TOTAL DAILY CONSUMPTION, so it must sum today's
                // meals — mealEntries is the full history and belongs to the log below.
                val totalCalories = todayMeals.sumOf { it.calories }
                val todayProtein = todayMeals.sumOf { it.proteinGrams.toDouble() }.toInt()
                val todayCarbs = todayMeals.sumOf { it.carbsGrams.toDouble() }.toInt()
                val todayFat = todayMeals.sumOf { it.fatGrams.toDouble() }.toInt()
                val filteredMealEntries = remember(mealEntries, searchQuery, selectedFilter, sortOrder) {
                    val query = searchQuery.trim().lowercase()
                    val todayMidnight = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    mealEntries
                        .filter { meal ->
                            val matchesQuery = query.isEmpty() ||
                                    meal.foodName.lowercase().contains(query) ||
                                    meal.id.lowercase().contains(query) ||
                                    (meal.assessment?.lowercase()?.contains(query) ?: false)

                            val matchesFilter = when (selectedFilter) {
                                MealFilter.ALL -> true
                                MealFilter.TODAY -> meal.timestamp >= todayMidnight
                                MealFilter.HIGH_PROTEIN -> meal.proteinGrams >= 25f
                                MealFilter.HIGH_CAL -> meal.calories >= 500
                                MealFilter.LIQUID -> meal.isLiquid
                                MealFilter.FLAGGED -> meal.isRestricted || meal.isNightRefueling
                            }

                            matchesQuery && matchesFilter
                        }
                        .let { list ->
                            when (sortOrder) {
                                MealSortOrder.NEWEST -> list.sortedByDescending { it.timestamp }
                                MealSortOrder.HIGHEST_CAL -> list.sortedByDescending { it.calories }
                                MealSortOrder.HIGHEST_PROTEIN -> list.sortedByDescending { it.proteinGrams }
                            }
                        }
                }

                if (showManualEntryDialog) {
                    ManualMealDialog(
                        onDismiss = { showManualEntryDialog = false },
                        onSave = { name, cal, p, c, f, isLiquid ->
                            showManualEntryDialog = false
                            viewModel.logManualMeal(name, cal, p, c, f, isLiquid)
                        }
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    StateStatusBanner(complianceStatus, Modifier.statusBarsPadding())

                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize()
                    ) {
                        SummaryCard(
                            totalCalories = totalCalories,
                            target = target,
                            protein = todayProtein,
                            carbs = todayCarbs,
                            fat = todayFat
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        ActionRow(
                            onCaptureMeal = {
                                // Light feedback, matching Choose photo/Log
                                // manually below: all three are frequent,
                                // everyday entry points into logging a meal,
                                // not a commit — LongPress is reserved for the
                                // action that actually writes or destroys data.
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                // Location is only requested once the user has opted in
                                // from Settings — bundling it with CAMERA gave no
                                // context for the request.
                                val permissions = buildList {
                                    add(Manifest.permission.CAMERA)
                                    if (locationTrackingEnabled) {
                                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    }
                                }.toTypedArray()
                                if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                                    screenState = ScreenState.CAMERA
                                } else {
                                    cameraPermissionLauncher.launch(permissions)
                                }
                            },
                            onImportImage = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onManualEntry = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                showManualEntryDialog = true
                            }
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        HorizontalDivider(
                            thickness = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.dashboard_recent_meals),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (mealEntries.isNotEmpty()) {
                                Text(
                                    text = stringResource(
                                        R.string.dashboard_logged_count,
                                        filteredMealEntries.size,
                                        mealEntries.size
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (mealEntries.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                // The no-key branch used to read "Add an API key in
                                // Settings to start logging meals", which is simply
                                // untrue: manual entry needs no key and no network.
                                // It told a user without a key that the app did not
                                // work, in the one place they had nothing else to
                                // read. An empty state has to say what happened, why,
                                // and what they can do next.
                                Text(
                                    text = stringResource(
                                        if (hasApiKey) R.string.empty_no_meals else R.string.empty_no_meals_no_key
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            MealSearchBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            MealFilterChipRow(
                                selectedFilter = selectedFilter,
                                onSelectFilter = { selectedFilter = it },
                                sortOrder = sortOrder,
                                onSelectSort = { sortOrder = it }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (filteredMealEntries.isEmpty()) {
                                EmptySearchResultView(
                                    onReset = {
                                        searchQuery = ""
                                        selectedFilter = MealFilter.ALL
                                    }
                                )
                            } else {
                                HistoryList(
                                    mealEntries = filteredMealEntries,
                                    onDeleteMeal = { mealToDelete = it },
                                    onNavigateToDetail = onNavigateToDetail
                                )
                            }
                        }
                    }
                }

                // Modal deletion confirmation dialog protecting against accidental card taps
                mealToDelete?.let { targetMeal ->
                    AlertDialog(
                        onDismissRequest = { mealToDelete = null },
                        title = {
                            Text(
                                text = stringResource(R.string.delete_meal_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(
                                    R.string.delete_meal_body,
                                    targetMeal.foodName,
                                    targetMeal.calories
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    // This button had no haptic at all — the one
                                    // place in the app that actually destroys
                                    // data gave less tactile confirmation than
                                    // an ordinary filter tap did.
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val id = targetMeal.id
                                    mealToDelete = null
                                    viewModel.deleteMealEntry(id)
                                },
                                shape = RectangleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text(stringResource(R.string.delete_meal_confirm), fontWeight = FontWeight.Black)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { mealToDelete = null },
                                shape = RectangleShape
                            ) {
                                Text(stringResource(R.string.action_cancel), color = Color.Gray)
                            }
                        },
                        shape = RectangleShape,
                        containerColor = Color(0xFF141414)
                    )
                }
            }
            ScreenState.CAMERA -> {
                BackHandler {
                    screenState = ScreenState.DASHBOARD
                }
                CameraCaptureScreen(
                    onImageCaptured = { uri ->
                        viewModel.processImageForMacros(uri, context)
                        screenState = ScreenState.DASHBOARD
                    },
                    onBack = {
                        screenState = ScreenState.DASHBOARD
                    }
                )
            }
        }

        if (uiState is UiState.Loading) {
            AnalysisLoadingOverlay(onCancel = { viewModel.cancelAnalysis() })
        }

        // Results are reviewed before they are recorded. Nothing has been written
        // to the log at this point.
        pendingAnalysis?.let { pending ->
            AnalysisReviewSheet(
                pending = pending,
                onConfirm = { corrected -> viewModel.confirmPendingAnalysis(corrected) },
                onDiscard = { viewModel.discardPendingAnalysis() }
            )
        }


        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

/**
 * The dashboard status line.
 *
 * This was a full-bleed saturated fill — the loudest element on the screen —
 * carrying whatever the status happened to be, including "Close to target. Room
 * to improve." on a bright yellow bar. Reserving the strongest signal for
 * ordinary information leaves nothing left to say when something is actually
 * wrong, so the fill is now a thin accent rule and a tinted surface, and the
 * text carries the meaning.
 *
 * The colours come from the active terminal theme rather than four hardcoded
 * literals, so switching theme no longer leaves this strip in the old palette.
 */
@Composable
fun StateStatusBanner(status: ComplianceStatus, modifier: Modifier = Modifier) {
    val accent = when (status) {
        ComplianceStatus.EXEMPLARY -> MaterialTheme.colorScheme.primary
        ComplianceStatus.ACCEPTABLE -> MaterialTheme.colorScheme.secondary
        ComplianceStatus.SUBVERSIVE, ComplianceStatus.CRISIS -> MaterialTheme.colorScheme.error
    }
    // Flavour lives in the status line, where it costs nothing to understand.
    // No status claims a feature is locked any more, because none is.
    val text = stringResource(
        when (status) {
            ComplianceStatus.EXEMPLARY -> R.string.status_on_target
            ComplianceStatus.ACCEPTABLE -> R.string.status_close_to_target
            ComplianceStatus.SUBVERSIVE -> R.string.status_off_target
            ComplianceStatus.CRISIS -> R.string.status_far_from_target
        }
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(accent)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
            )
        }
    }
}

@Composable
fun SummaryCard(
    totalCalories: Int,
    target: Int,
    protein: Int,
    carbs: Int,
    fat: Int
) {
    val delta = totalCalories - target
    val deltaText = if (delta > 0) {
        stringResource(R.string.dashboard_over_target, delta)
    } else {
        stringResource(R.string.dashboard_remaining, -delta)
    }
    val deltaColor = if (delta > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hudFraming(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dashboard_today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = deltaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = deltaColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$totalCalories",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = stringResource(R.string.dashboard_kcal_of_target, target),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))
            // The macros are primary data but were rendered at labelSmall (11sp),
            // the smallest style in the app — the same size as the forensic
            // metadata. They now read as a labelled column each, and the whole
            // row is announced as one sentence instead of "P colon zero g".
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        contentDescription = macroContentDescription(
                            protein.toFloat(), carbs.toFloat(), fat.toFloat()
                        )
                    },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MacroReadout(R.string.macro_protein, protein, NutritionColors.Protein)
                MacroReadout(R.string.macro_carbs, carbs, NutritionColors.Carbs)
                MacroReadout(R.string.macro_fat, fat, NutritionColors.Fat)
            }
        }
    }
}

@Composable
fun ActionRow(
    onCaptureMeal: () -> Unit,
    onImportImage: () -> Unit,
    onManualEntry: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCaptureMeal,
                modifier = Modifier.weight(1f),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.action_take_photo),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    fontSize = 12.sp
                )
            }
            OutlinedButton(
                onClick = onImportImage,
                modifier = Modifier.weight(1f),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.action_choose_photo),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    fontSize = 12.sp
                )
            }
        }

        // The network boundary, stated where the decision is made rather than
        // buried in Settings. A meal photo can incidentally contain faces, a
        // room, or documents, and the user is entitled to know it leaves the
        // device before they tap.
        Text(
            text = stringResource(R.string.capture_network_notice),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(top = 2.dp)
        )

        OutlinedButton(
            onClick = onManualEntry,
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        ) {
            Icon(
                imageVector = Icons.Default.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.action_log_manually),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun ManualMealDialog(
    onDismiss: () -> Unit,
    onSave: (foodName: String, calories: Int, protein: Float, carbs: Float, fat: Float, isLiquid: Boolean) -> Unit
) {
    // rememberSaveable: a rotation while the dialog is open used to discard
    // everything the user had typed.
    var foodName by rememberSaveable { mutableStateOf("") }
    var caloriesStr by rememberSaveable { mutableStateOf("") }
    var proteinStr by rememberSaveable { mutableStateOf("") }
    var carbsStr by rememberSaveable { mutableStateOf("") }
    var fatStr by rememberSaveable { mutableStateOf("") }
    var isLiquid by rememberSaveable { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    // A blank calorie field used to save as a real 0 with no indication that
    // anything had been defaulted — a meal with macros but no calorie figure
    // read as accurate. Zero itself is a legitimate value (black coffee,
    // water), so the requirement is that the user typed *something*, not that
    // the number is positive.
    val caloriesValue = parseCalories(caloriesStr)
    val isValid = foodName.isNotBlank() && caloriesStr.isNotBlank() && caloriesValue != null

    val caloriesFocus = remember { FocusRequester() }
    val proteinFocus = remember { FocusRequester() }
    val carbsFocus = remember { FocusRequester() }
    val fatFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = foodName,
                    onValueChange = { foodName = it },
                    label = { Text(stringResource(R.string.field_item_name)) },
                    singleLine = true,
                    shape = RectangleShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { caloriesFocus.requestFocus() }),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = caloriesStr,
                    onValueChange = { caloriesStr = it.filter { ch -> ch.isDigit() }.take(6) },
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
                    OutlinedTextField(
                        value = proteinStr,
                        onValueChange = { proteinStr = sanitizeDecimalInput(it) },
                        label = { Text(stringResource(R.string.field_protein)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { carbsFocus.requestFocus() }),
                        shape = RectangleShape,
                        modifier = Modifier.weight(1f).focusRequester(proteinFocus)
                    )
                    OutlinedTextField(
                        value = carbsStr,
                        onValueChange = { carbsStr = sanitizeDecimalInput(it) },
                        label = { Text(stringResource(R.string.field_carbs)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { fatFocus.requestFocus() }),
                        shape = RectangleShape,
                        modifier = Modifier.weight(1f).focusRequester(carbsFocus)
                    )
                    OutlinedTextField(
                        value = fatStr,
                        onValueChange = { fatStr = sanitizeDecimalInput(it) },
                        label = { Text(stringResource(R.string.field_fat)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        shape = RectangleShape,
                        modifier = Modifier.weight(1f).focusRequester(fatFocus)
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
                Text(
                    text = stringResource(R.string.manual_entry_required_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSave(foodName, caloriesValue ?: 0, parseGrams(proteinStr), parseGrams(carbsStr), parseGrams(fatStr), isLiquid)
                },
                enabled = isValid,
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(R.string.manual_entry_save), fontWeight = FontWeight.Black)
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
fun HistoryList(
    mealEntries: List<MealEntry>,
    onDeleteMeal: (MealEntry) -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        // Clears the bottom navigation bar so the last entry is fully readable.
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        items(mealEntries, key = { it.id }) { entry ->
            MealEntryItem(
                entry = entry,
                onDelete = { onDeleteMeal(entry) },
                onClick = { onNavigateToDetail(entry.id) }
            )
        }
    }
}

@Composable
fun MealEntryItem(
    entry: MealEntry,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    // Was "yyyy-MM-dd HH:mm:ss 'UTC'" with the default (device-local) timezone,
    // so every card labelled a local time as UTC. Show local time, and say so
    // with a real zone marker rather than a hardcoded literal.
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.getDefault()) }
    val haptic = LocalHapticFeedback.current
    val deleteDescription = stringResource(R.string.content_description_delete_meal, entry.foodName)
    val macroDescription = macroContentDescription(entry.proteinGrams, entry.carbsGrams, entry.fatGrams)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Light feedback: this navigates to the meal's detail screen,
                // it doesn't write or destroy anything.
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        // Was .uppercase(): all-caps is fine for system labels but
                        // slows reading of arbitrary-length content, and it
                        // mangles names the user typed themselves.
                        text = entry.foodName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${formatGramsValue(entry.proteinGrams)}P " +
                            "${formatGramsValue(entry.carbsGrams)}C " +
                            "${formatGramsValue(entry.fatGrams)}F",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = macroDescription
                        }
                    )
                }
                Text(
                    text = stringResource(R.string.detail_kcal, entry.calories).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDelete()
                    },
                    // Every row's delete button announced the bare word "DELETE",
                    // giving no way to tell which meal was about to go.
                    modifier = Modifier.semantics {
                        contentDescription = deleteDescription
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = Color.DarkGray.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(6.dp))

            // The card used to carry the full record UUID and a "COORDS: NOT
            // RECORDED" line on every row - level-4 forensic detail competing with
            // the name and calorie figure for the same glance. The full id and the
            // coordinates live on the detail screen; the card keeps the time, plus
            // a geotag marker only when there is actually a geotag.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dateFormat.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                if (entry.latitude != null && entry.longitude != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.trends_geotag_marker),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun MealSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        placeholder = {
            Text(
                stringResource(R.string.search_placeholder),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.content_description_search),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onQueryChange("")
                }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.content_description_clear_search),
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.DarkGray,
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
        ),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

@Composable
fun MealFilterChipRow(
    selectedFilter: MealFilter,
    onSelectFilter: (MealFilter) -> Unit,
    sortOrder: MealSortOrder,
    onSelectSort: (MealSortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // FlowRow, not a horizontalScroll Row: six filter chips plus the sort
    // control used to share one scrolling row with no scroll affordance, so
    // LIQUID and FLAGGED were routinely off-screen and undiscoverable.
    // Wrapping to a second row means every filter is visible without a
    // sideways gesture the user has no reason to expect is there.
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sort control: a menu listing all three orders with the active one
        // checked, not a button that cycles blind through them on tap. The
        // old version required tapping and reading repeatedly to find a
        // specific order, and gave no way to see the other options without
        // landing on them first.
        var sortMenuOpen by remember { mutableStateOf(false) }
        Box {
            Surface(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        sortMenuOpen = true
                    },
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.content_description_sort),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(sortOrder.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                MealSortOrder.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes)) },
                        leadingIcon = {
                            if (option == sortOrder) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onSelectSort(option)
                            sortMenuOpen = false
                        }
                    )
                }
            }
        }

        // Filter chips
        MealFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            Surface(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onSelectFilter(filter)
                    },
                shape = RectangleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black,
                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(filter.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun EmptySearchResultView(
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.empty_no_filter_matches),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.empty_no_filter_matches_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onReset()
                },
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = stringResource(R.string.empty_clear_filters),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


/**
 * One macro column in the Today card: a quiet label above a readable number.
 *
 * Kept deliberately flat rather than three progress rings — there is no
 * meaningful per-macro target configured in the app, so a ring would imply a
 * goal the user never set.
 */
@Composable
private fun MacroReadout(@StringRes labelRes: Int, grams: Int, accent: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.macro_grams_short, grams),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = accent
        )
    }
}

/**
 * The blocking overlay shown while an image is being analysed.
 *
 * Analysis can take the better part of a minute on a cold provider, so the
 * overlay says what is happening and offers a way out. Without the cancel
 * action a slow request left the user staring at a spinner with no route back
 * to manual entry.
 */
@Composable
private fun AnalysisLoadingOverlay(onCancel: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Surface(
        color = Color.Black.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.analysis_in_progress),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.analysis_may_take_a_minute),
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onCancel()
                    },
                    shape = RectangleShape,
                    border = BorderStroke(1.dp, Color.Gray)
                ) {
                    Text(
                        stringResource(R.string.action_cancel_operation),
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
