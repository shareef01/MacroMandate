package com.sharek.macromandate.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.sharek.macromandate.R
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.network.ApiConfig
import com.sharek.macromandate.ui.components.*
import com.sharek.macromandate.ui.theme.NutritionColors
import com.sharek.macromandate.ui.theme.TerminalDesign
import com.sharek.macromandate.util.NutritionFormat
import com.sharek.macromandate.util.ParsedNutrition
import com.sharek.macromandate.viewmodel.ComplianceStatus
import com.sharek.macromandate.viewmodel.MainViewModel
import com.sharek.macromandate.viewmodel.UiState
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

enum class ScreenState {
    DASHBOARD, CAMERA
}

enum class MealFilter(val label: String) {
    ALL("ALL"),
    TODAY("TODAY"),
    HIGH_PROTEIN("HIGH PROTEIN"),
    HIGH_CAL("HIGH CAL"),
    LIQUID("LIQUID"),
    FLAGGED("LATE / FLAGGED")
}

enum class MealSortOrder(val label: String) {
    NEWEST("NEWEST"),
    HIGHEST_CAL("CALORIES ↓"),
    HIGHEST_PROTEIN("PROTEIN ↓");

    fun next(): MealSortOrder = when (this) {
        NEWEST -> HIGHEST_CAL
        HIGHEST_CAL -> HIGHEST_PROTEIN
        HIGHEST_PROTEIN -> NEWEST
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToHistory: (() -> Unit)? = null,
    manualEntryRequested: Boolean = false,
    onManualEntryOpened: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val tokens = TerminalDesign.tokens
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val complianceStatus by viewModel.complianceStatus.collectAsStateWithLifecycle()
    val locationTrackingEnabled by viewModel.locationTrackingEnabled.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val target by viewModel.calorieTarget.collectAsStateWithLifecycle()
    val photoReview by viewModel.photoReview.collectAsStateWithLifecycle()
    val isSavingPhoto by viewModel.isSavingPhoto.collectAsStateWithLifecycle()
    val crtEffectsEnabled by viewModel.crtEffectsEnabled.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val selectedDayMeals by viewModel.selectedDayMeals.collectAsStateWithLifecycle()
    val barcodeResult by viewModel.barcodeResult.collectAsStateWithLifecycle()

    var screenState by rememberSaveable { mutableStateOf(ScreenState.DASHBOARD) }
    var showManualEntryDialog by rememberSaveable { mutableStateOf(false) }
    var mealToDelete by remember { mutableStateOf<MealEntry?>(null) }

    LaunchedEffect(manualEntryRequested) {
        if (manualEntryRequested) {
            showManualEntryDialog = true
            onManualEntryOpened()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                viewModel.processImageForMacros(MainViewModel.ImageSource.Imported(it), context)
            }
        }
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
            if (cameraGranted) {
                screenState = ScreenState.CAMERA
            } else {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Camera permission is required to take photos or scan barcodes.",
                        actionLabel = "Settings",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        runCatching { context.startActivity(intent) }
                    }
                }
            }
        }
    )

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is UiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetUiState()
            }
            is UiState.Success -> {
                snackbarHostState.showSnackbar("Logged: ${state.mealName}")
                viewModel.resetUiState()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
    ) {
        when (screenState) {
            ScreenState.DASHBOARD -> {
                val today = remember { LocalDate.now() }
                val isViewingToday = selectedDate == today
                val dayCalories = selectedDayMeals.sumOf { it.calories }
                val dayProtein = selectedDayMeals.sumOf { it.proteinGrams.toDouble() }.toFloat()
                val dayCarbs = selectedDayMeals.sumOf { it.carbsGrams.toDouble() }.toFloat()
                val dayFat = selectedDayMeals.sumOf { it.fatGrams.toDouble() }.toFloat()

                Column(modifier = Modifier.fillMaxSize()) {
                    TerminalTopBar(
                        title = "MACROMANDATE",
                        subtitle = if (isViewingToday) "DAILY INTAKE TERMINAL" else "HISTORICAL DAY VIEW",
                        actions = {
                            if (!isViewingToday) {
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        viewModel.selectToday()
                                    },
                                    shape = tokens.controlShape,
                                    border = BorderStroke(1.dp, tokens.primaryAccent),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Today,
                                        contentDescription = null,
                                        tint = tokens.primaryAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "TODAY",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.primaryAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    showManualEntryDialog = true
                                },
                                modifier = Modifier.size(tokens.minTouchTarget)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Log meal manually",
                                    tint = tokens.primaryAccent
                                )
                            }
                        }
                    )

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // Unified LazyColumn prevents fixed headers from crowding out meal history on small screens
                        LazyColumn(
                            modifier = Modifier
                                .widthIn(max = tokens.maxContentWidth)
                                .fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 12.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item(key = "date_navigator") {
                                DateNavigationBar(
                                    selectedDate = selectedDate,
                                    today = today,
                                    onPreviousDay = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        viewModel.selectPreviousDay()
                                    },
                                    onNextDay = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        viewModel.selectNextDay()
                                    },
                                    onReturnToToday = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        viewModel.selectToday()
                                    }
                                )
                            }

                            item(key = "daily_summary") {
                                DashboardIntakeCard(
                                    totalCalories = dayCalories,
                                    target = target,
                                    proteinGrams = dayProtein,
                                    carbsGrams = dayCarbs,
                                    fatGrams = dayFat,
                                    isViewingToday = isViewingToday,
                                    selectedDate = selectedDate
                                )
                            }

                            item(key = "logging_actions") {
                                MealLoggingActionsCard(
                                    hasApiKey = hasApiKey,
                                    onManualEntry = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        showManualEntryDialog = true
                                    },
                                    onCaptureMeal = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        val permissions = buildList {
                                            add(Manifest.permission.CAMERA)
                                            if (locationTrackingEnabled) {
                                                add(Manifest.permission.ACCESS_FINE_LOCATION)
                                                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                            }
                                        }.toTypedArray()
                                        if (permissions.all {
                                                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                                            }
                                        ) {
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
                                    }
                                )
                            }

                            item(key = "meals_section_header") {
                                TerminalSectionHeader(
                                    title = if (isViewingToday) "Today's Meals" else "Meals on ${NutritionFormat.formatDateHeader(selectedDate, today)}",
                                    badge = "${selectedDayMeals.size} LOGGED",
                                    trailing = if (onNavigateToHistory != null) {
                                        {
                                            TextButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                    onNavigateToHistory()
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.History,
                                                    contentDescription = null,
                                                    tint = tokens.primaryAccent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "ALL HISTORY",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = tokens.primaryAccent,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else null
                                )
                            }

                            if (selectedDayMeals.isEmpty()) {
                                item(key = "empty_day_state") {
                                    TerminalEmptyState(
                                        title = if (isViewingToday) "NO MEALS LOGGED TODAY" else "NO MEALS RECORDED ON THIS DATE",
                                        message = if (hasApiKey) {
                                            "Log a meal manually, capture a photo for AI estimation, or scan a product barcode."
                                        } else {
                                            "Log a meal manually or scan a product barcode. To enable AI photo estimates, add an API key in Settings."
                                        },
                                        icon = Icons.Default.Restaurant,
                                        actionLabel = "LOG MEAL MANUALLY",
                                        onAction = { showManualEntryDialog = true }
                                    )
                                }
                            } else {
                                items(
                                    items = selectedDayMeals,
                                    key = { it.id }
                                ) { entry ->
                                    TerminalMealRow(
                                        entry = entry,
                                        showFullDate = false,
                                        onClick = { onNavigateToDetail(entry.id) },
                                        onDelete = { mealToDelete = entry }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ScreenState.CAMERA -> {
                BackHandler {
                    screenState = ScreenState.DASHBOARD
                }
                CameraCaptureScreen(
                    onImageCaptured = { uri, stagingId ->
                        viewModel.processImageForMacros(MainViewModel.ImageSource.LiveCamera(uri, stagingId), context)
                        screenState = ScreenState.DASHBOARD
                    },
                    onBarcodeScanned = { barcode ->
                        viewModel.lookupBarcode(barcode, context)
                        screenState = ScreenState.DASHBOARD
                    },
                    onManualEntry = {
                        screenState = ScreenState.DASHBOARD
                        showManualEntryDialog = true
                    },
                    onBack = {
                        screenState = ScreenState.DASHBOARD
                    },
                    crtEffectsEnabled = crtEffectsEnabled
                )
            }
        }

        // Editable Barcode Scan Review Dialog
        barcodeResult?.let { scannedEntry ->
            key(scannedEntry.id) {
                BarcodeReviewDialog(
                    initialEntry = scannedEntry,
                    onDismiss = viewModel::clearBarcodeResult,
                    onSave = { finalEntry ->
                        viewModel.addMealEntry(finalEntry)
                        viewModel.clearBarcodeResult()
                    }
                )
            }
        }

        // Manual Meal Logging Dialog
        if (showManualEntryDialog) {
            ManualMealDialog(
                onDismiss = { showManualEntryDialog = false },
                onSave = { name, cal, p, c, f, isLiquid, notes, complete ->
                    viewModel.logManualMeal(
                        foodName = name,
                        calories = cal,
                        protein = p,
                        carbs = c,
                        fat = f,
                        isLiquid = isLiquid,
                        notes = notes
                    ) { saved ->
                        complete(saved)
                        if (saved) showManualEntryDialog = false
                    }
                }
            )
        }

        // AI Photo Estimate Review Dialog with image preview and caveats
        photoReview?.let { review ->
            key(review.meal.id) {
                PhotoReviewDialog(
                    review = review,
                    isSaving = isSavingPhoto,
                    onDismiss = viewModel::discardPhotoReview,
                    onSave = { name, cal, p, c, f, isLiquid, notes, complete ->
                        viewModel.savePhotoReviewWithNotes(name, cal, p, c, f, isLiquid, notes, complete)
                    }
                )
            }
        }

        // Delete confirmation dialog
        mealToDelete?.let { targetMeal ->
            DeleteMealConfirmDialog(
                meal = targetMeal,
                onConfirm = {
                    val id = targetMeal.id
                    mealToDelete = null
                    viewModel.deleteMealEntry(id)
                },
                onDismiss = { mealToDelete = null }
            )
        }

        if (uiState is UiState.Loading) {
            TerminalLoadingOverlay(
                title = "ANALYZING NUTRITION DATA",
                subtitle = "Contacting provider. This can take up to a minute.",
                onCancel = viewModel::cancelAnalysis
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

/**
 * Dedicated History Screen (2nd tab in bottom navigation).
 * Turns meal history into a practical, searchable, date-grouped nutrition journal.
 */
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onNavigateToDetail: (String) -> Unit
) {
    val tokens = TerminalDesign.tokens
    val snackbarHostState = remember { SnackbarHostState() }

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterType by viewModel.filterType.collectAsStateWithLifecycle()
    val selectedFilter = MealFilter.entries.getOrElse(filterType) { MealFilter.ALL }
    val sortType by viewModel.sortOrder.collectAsStateWithLifecycle()
    val sortOrder = MealSortOrder.entries.getOrElse(sortType) { MealSortOrder.NEWEST }
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val mealEntries = viewModel.mealEntries.collectAsLazyPagingItems()
    var mealToDelete by remember { mutableStateOf<MealEntry?>(null) }
    var showManualEntryDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is UiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetUiState()
            }
            is UiState.Success -> {
                snackbarHostState.showSnackbar("Logged: ${state.mealName}")
                viewModel.resetUiState()
            }
            else -> {}
        }
    }

    val isFiltered = searchQuery.isNotBlank() || selectedFilter != MealFilter.ALL

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TerminalTopBar(
                title = "NUTRITION JOURNAL",
                subtitle = "SEARCHABLE MEAL ARCHIVE",
                actions = {
                    if (isFiltered) {
                        TextButton(
                            onClick = {
                                viewModel.searchQuery.value = ""
                                viewModel.filterType.value = MealFilter.ALL.ordinal
                            }
                        ) {
                            Text(
                                text = "RESET",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.primaryAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(
                        onClick = { showManualEntryDialog = true },
                        modifier = Modifier.size(tokens.minTouchTarget)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Log meal manually",
                            tint = tokens.primaryAccent
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
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    MealSearchBar(
                        query = searchQuery,
                        onQueryChange = { viewModel.searchQuery.value = it }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    MealFilterChipRow(
                        selectedFilter = selectedFilter,
                        onSelectFilter = { viewModel.filterType.value = it.ordinal },
                        sortOrder = sortOrder,
                        onSelectSort = { viewModel.sortOrder.value = it.ordinal },
                        onToggleSortOrder = { viewModel.sortOrder.value = sortOrder.next().ordinal }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (mealEntries.itemCount == 0 && mealEntries.loadState.refresh !is LoadState.Loading) {
                        if (!isFiltered) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                TerminalEmptyState(
                                    title = "YOUR JOURNAL IS EMPTY",
                                    message = if (hasApiKey) {
                                        stringResource(R.string.no_meals_api_key_prompt)
                                    } else {
                                        stringResource(R.string.no_meals_no_api_key_prompt)
                                    },
                                    icon = Icons.Default.History,
                                    actionLabel = "LOG FIRST MEAL",
                                    onAction = { showManualEntryDialog = true }
                                )
                            }
                        } else {
                            EmptySearchResultView(
                                onReset = {
                                    viewModel.searchQuery.value = ""
                                    viewModel.filterType.value = MealFilter.ALL.ordinal
                                }
                            )
                        }
                    } else {
                        HistoryList(
                            mealEntries = mealEntries,
                            onDeleteMeal = { mealToDelete = it },
                            onNavigateToDetail = onNavigateToDetail,
                            groupByDate = sortOrder == MealSortOrder.NEWEST
                        )
                    }
                }
            }
        }

        if (showManualEntryDialog) {
            ManualMealDialog(
                onDismiss = { showManualEntryDialog = false },
                onSave = { name, cal, p, c, f, isLiquid, notes, complete ->
                    viewModel.logManualMeal(
                        foodName = name,
                        calories = cal,
                        protein = p,
                        carbs = c,
                        fat = f,
                        isLiquid = isLiquid,
                        notes = notes
                    ) { saved ->
                        complete(saved)
                        if (saved) showManualEntryDialog = false
                    }
                }
            )
        }

        mealToDelete?.let { targetMeal ->
            DeleteMealConfirmDialog(
                meal = targetMeal,
                onConfirm = {
                    val id = targetMeal.id
                    mealToDelete = null
                    viewModel.deleteMealEntry(id)
                },
                onDismiss = { mealToDelete = null }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

@Composable
fun DateNavigationBar(
    selectedDate: LocalDate,
    today: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onReturnToToday: () -> Unit
) {
    val tokens = TerminalDesign.tokens
    val isToday = selectedDate == today
    val dateLabel = remember(selectedDate, today) {
        NutritionFormat.formatDateHeader(selectedDate, today)
    }

    Surface(
        color = tokens.surface,
        shape = tokens.cardShape,
        border = BorderStroke(1.dp, tokens.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPreviousDay,
                modifier = Modifier.size(tokens.minTouchTarget)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous day",
                    tint = tokens.primaryAccent
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isToday, onClick = onReturnToToday)
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.primaryText,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (!isToday) {
                    Text(
                        text = "Tap to return to Today",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.primaryAccent
                    )
                }
            }

            IconButton(
                onClick = onNextDay,
                enabled = !isToday,
                modifier = Modifier.size(tokens.minTouchTarget)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next day",
                    tint = if (!isToday) tokens.primaryAccent else tokens.disabledText
                )
            }
        }
    }
}

@Composable
fun DashboardIntakeCard(
    totalCalories: Int,
    target: Int,
    proteinGrams: Float,
    carbsGrams: Float,
    fatGrams: Float,
    isViewingToday: Boolean,
    selectedDate: LocalDate
) {
    val tokens = TerminalDesign.tokens
    val safeTarget = target.coerceAtLeast(0)
    val progress = if (safeTarget > 0) (totalCalories.toFloat() / safeTarget.toFloat()).coerceIn(0f, 1f) else 0f
    val percent = if (safeTarget > 0) ((totalCalories.toFloat() / safeTarget.toFloat()) * 100f).roundToInt() else 0
    val delta = totalCalories - safeTarget
    val isOverTarget = safeTarget > 0 && delta > 0

    val statusColor = when {
        safeTarget <= 0 -> tokens.primaryAccent
        isOverTarget -> tokens.warning
        else -> tokens.primaryAccent
    }
    val deltaLabel = when {
        safeTarget <= 0 -> "NO TARGET SET"
        isOverTarget -> "+$delta KCAL OVER TARGET"
        else -> "${-delta} KCAL REMAINING"
    }

    val pText = NutritionFormat.formatGramsValue(proteinGrams)
    val cText = NutritionFormat.formatGramsValue(carbsGrams)
    val fText = NutritionFormat.formatGramsValue(fatGrams)

    val pCal = proteinGrams * 4f
    val cCal = carbsGrams * 4f
    val fCal = fatGrams * 9f
    val totalMacroCal = pCal + cCal + fCal
    val pPct = if (totalMacroCal > 0f) ((pCal / totalMacroCal) * 100f).roundToInt() else 0
    val cPct = if (totalMacroCal > 0f) ((cCal / totalMacroCal) * 100f).roundToInt() else 0
    val fPct = if (totalMacroCal > 0f) ((fCal / totalMacroCal) * 100f).roundToInt() else 0

    val summarySemantics = buildString {
        append(if (isViewingToday) "Today's intake: " else "Intake for $selectedDate: ")
        append("$totalCalories of $safeTarget calories ($percent percent). $deltaLabel. ")
        append(NutritionFormat.macroContentDescription(proteinGrams, carbsGrams, fatGrams))
    }

    TerminalCard(
        withHudCorners = true,
        accentBorder = statusColor.copy(alpha = 0.45f),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = summarySemantics
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isViewingToday) "DAILY CALORIE INTAKE" else "RECORDED DAY INTAKE",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.secondaryText,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color = tokens.elevatedSurface,
                shape = tokens.controlShape,
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = deltaLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Consumed vs Target Hero Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$totalCalories",
                    style = MaterialTheme.typography.displayLarge,
                    color = tokens.primaryText,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (safeTarget > 0) "/ $safeTarget kcal" else "kcal",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            if (safeTarget > 0) {
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleLarge,
                    color = statusColor,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(tokens.controlShape)
                .background(tokens.elevatedSurface)
        ) {
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(statusColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = tokens.divider)
        Spacer(modifier = Modifier.height(12.dp))

        // Compact Protein / Carbs / Fat Summary Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardMacroColumn(
                label = "PROTEIN",
                gramsText = "${pText}g",
                shareText = "$pPct% cal",
                accent = NutritionColors.Protein,
                modifier = Modifier.weight(1f)
            )
            DashboardMacroColumn(
                label = "CARBS",
                gramsText = "${cText}g",
                shareText = "$cPct% cal",
                accent = NutritionColors.Carbs,
                modifier = Modifier.weight(1f)
            )
            DashboardMacroColumn(
                label = "FAT",
                gramsText = "${fText}g",
                shareText = "$fPct% cal",
                accent = NutritionColors.Fat,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DashboardMacroColumn(
    label: String,
    gramsText: String,
    shareText: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val tokens = TerminalDesign.tokens
    Surface(
        color = tokens.elevatedSurface,
        shape = tokens.controlShape,
        border = BorderStroke(1.dp, tokens.border),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = gramsText,
                style = MaterialTheme.typography.titleLarge,
                color = tokens.primaryText,
                fontWeight = FontWeight.Black
            )
            Text(
                text = shareText,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText
            )
        }
    }
}

/**
 * Makes Manual Entry, Camera Capture/Barcode Scan, and Gallery Import equally discoverable.
 */
@Composable
fun MealLoggingActionsCard(
    hasApiKey: Boolean,
    onManualEntry: () -> Unit,
    onCaptureMeal: () -> Unit,
    onImportImage: () -> Unit
) {
    val tokens = TerminalDesign.tokens
    TerminalCard(contentPadding = PaddingValues(14.dp)) {
        Text(
            text = "LOG A MEAL",
            style = MaterialTheme.typography.labelMedium,
            color = tokens.primaryAccent,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Primary immediate action: Manual Entry works 100% offline with zero permissions or API key
        TerminalPrimaryButton(
            text = "LOG MEAL MANUALLY",
            icon = Icons.Default.EditNote,
            onClick = onManualEntry,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TerminalSecondaryButton(
                text = "CAMERA / SCAN",
                icon = Icons.Default.CameraAlt,
                onClick = onCaptureMeal,
                modifier = Modifier.weight(1f)
            )
            TerminalSecondaryButton(
                text = "GALLERY",
                icon = Icons.Default.PhotoLibrary,
                onClick = onImportImage,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Manual logging stays on this device. Photo analysis sends a resized image to ${ApiConfig.providerName}.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.secondaryText
        )
    }
}

@Composable
fun ManualMealDialog(
    onDismiss: () -> Unit,
    onSave: (foodName: String, calories: Int, protein: Float, carbs: Float, fat: Float, isLiquid: Boolean, complete: (Boolean) -> Unit) -> Unit,
    estimate: MealEntry? = null,
    savingEstimate: Boolean = false
) {
    ManualMealDialog(
        onDismiss = onDismiss,
        onSave = { name, cal, p, c, f, isLiquid, _, complete ->
            onSave(name, cal, p, c, f, isLiquid, complete)
        },
        estimate = estimate,
        savingEstimate = savingEstimate
    )
}

@Composable
fun ManualMealDialog(
    onDismiss: () -> Unit,
    onSave: (foodName: String, calories: Int, protein: Float, carbs: Float, fat: Float, isLiquid: Boolean, notes: String?, complete: (Boolean) -> Unit) -> Unit,
    estimate: MealEntry? = null,
    savingEstimate: Boolean = false
) {
    val tokens = TerminalDesign.tokens
    val haptic = LocalHapticFeedback.current

    var savingManual by remember { mutableStateOf(false) }
    val saving = savingManual || savingEstimate
    var saveFailed by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    var foodName by rememberSaveable { mutableStateOf(estimate?.foodName.orEmpty()) }
    var caloriesStr by rememberSaveable { mutableStateOf(estimate?.calories?.toString().orEmpty()) }
    var proteinStr by rememberSaveable {
        mutableStateOf(estimate?.proteinGrams?.let { NutritionFormat.formatGramsValue(it) }.orEmpty())
    }
    var carbsStr by rememberSaveable {
        mutableStateOf(estimate?.carbsGrams?.let { NutritionFormat.formatGramsValue(it) }.orEmpty())
    }
    var fatStr by rememberSaveable {
        mutableStateOf(estimate?.fatGrams?.let { NutritionFormat.formatGramsValue(it) }.orEmpty())
    }
    var isLiquid by rememberSaveable { mutableStateOf(estimate?.isLiquid ?: false) }
    var notes by rememberSaveable { mutableStateOf(estimate?.assessment.orEmpty()) }

    val hasUnsavedDraft = foodName.isNotBlank() || caloriesStr.isNotBlank() ||
        proteinStr.isNotBlank() || carbsStr.isNotBlank() || fatStr.isNotBlank() || notes.isNotBlank()
    val isValid = NutritionFormat.isMealFormValid(foodName, caloriesStr, proteinStr, carbsStr, fatStr)

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = {
                Text(
                    text = "DISCARD UNSAVED ENTRY?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.warning
                )
            },
            text = {
                Text(
                    text = "You have entered meal information that hasn't been saved yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.primaryText
                )
            },
            confirmButton = {
                TerminalPrimaryButton(
                    text = "DISCARD",
                    isDestructive = true,
                    onClick = {
                        showDiscardConfirm = false
                        onDismiss()
                    }
                )
            },
            dismissButton = {
                TerminalSecondaryButton(
                    text = "KEEP EDITING",
                    onClick = { showDiscardConfirm = false }
                )
            },
            shape = tokens.cardShape,
            containerColor = tokens.surface
        )
    }

    AlertDialog(
        onDismissRequest = {
            if (!saving) {
                if (hasUnsavedDraft) showDiscardConfirm = true else onDismiss()
            }
        },
        title = {
            Text(
                text = if (estimate == null) "LOG MEAL MANUALLY" else "REVIEW NUTRITION ESTIMATE",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.primaryAccent
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (estimate != null) {
                    Text(
                        text = "AI estimates are approximate. Review or adjust any values before saving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText
                    )
                }
                if (saveFailed) {
                    Surface(
                        color = tokens.error.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, tokens.error),
                        shape = tokens.controlShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Could not save this meal. Check your inputs and try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.error,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

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
                    onNotesChange = { notes = it },
                    enabled = !saving
                )
            }
        },
        confirmButton = {
            TerminalPrimaryButton(
                text = when {
                    saving -> "SAVING…"
                    saveFailed -> "RETRY SAVE"
                    else -> "SAVE MEAL"
                },
                enabled = !saving && isValid,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (estimate == null) savingManual = true
                    saveFailed = false
                    val cal = NutritionFormat.parseCaloriesOrNull(caloriesStr) ?: 0
                    val p = NutritionFormat.parseGrams(proteinStr)
                    val c = NutritionFormat.parseGrams(carbsStr)
                    val f = NutritionFormat.parseGrams(fatStr)
                    onSave(foodName.trim(), cal, p, c, f, isLiquid, notes.trim().ifBlank { null }) { saved ->
                        savingManual = false
                        saveFailed = !saved
                    }
                }
            )
        },
        dismissButton = {
            TerminalSecondaryButton(
                text = "CANCEL",
                enabled = !saving,
                onClick = onDismiss
            )
        },
        shape = tokens.cardShape,
        containerColor = tokens.surface
    )
}

/**
 * Dedicated AI Photo Estimate Review Dialog with photograph preview, caveat banners,
 * and editable nutrition fields.
 */
@Composable
fun PhotoReviewDialog(
    review: MainViewModel.PhotoReview,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (foodName: String, calories: Int, protein: Float, carbs: Float, fat: Float, isLiquid: Boolean, notes: String?, complete: (Boolean) -> Unit) -> Unit
) {
    val tokens = TerminalDesign.tokens
    val haptic = LocalHapticFeedback.current
    val meal = review.meal
    val parsed = review.parsed

    var saveFailed by remember { mutableStateOf(false) }
    var foodName by rememberSaveable(meal.id) { mutableStateOf(meal.foodName) }
    var caloriesStr by rememberSaveable(meal.id) { mutableStateOf(meal.calories.toString()) }
    var proteinStr by rememberSaveable(meal.id) { mutableStateOf(NutritionFormat.formatGramsValue(meal.proteinGrams)) }
    var carbsStr by rememberSaveable(meal.id) { mutableStateOf(NutritionFormat.formatGramsValue(meal.carbsGrams)) }
    var fatStr by rememberSaveable(meal.id) { mutableStateOf(NutritionFormat.formatGramsValue(meal.fatGrams)) }
    var isLiquid by rememberSaveable(meal.id) { mutableStateOf(meal.isLiquid) }
    var notes by rememberSaveable(meal.id) { mutableStateOf(meal.assessment.orEmpty()) }

    val isValid = NutritionFormat.isMealFormValid(foodName, caloriesStr, proteinStr, carbsStr, fatStr)

    val caveatMessage = remember(parsed) {
        when {
            parsed?.hasMacroCalorieMismatch == true ->
                "The estimated calories differ from the macro total (4P + 4C + 9F). Verify before saving."
            parsed?.isCaloriesDerived == true ->
                "No explicit calorie value was returned; calories were calculated from the estimated macros."
            parsed?.wasClamped == true ->
                "Some returned values exceeded normal meal bounds and were clamped."
            else -> null
        }
    }

    AlertDialog(
        onDismissRequest = { /* Deliberately requires explicit Save or Discard so estimates aren't lost on stray tap */ },
        title = {
            Column {
                Text(
                    text = "REVIEW AI NUTRITION ESTIMATE",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.primaryAccent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Generated estimate — review and adjust values before saving to your log.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText
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
                    model = review.source.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 180.dp)
                        .clip(tokens.controlShape)
                        .background(tokens.background)
                )

                if (caveatMessage != null) {
                    Surface(
                        color = tokens.warning.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, tokens.warning),
                        shape = tokens.controlShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = tokens.warning,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = caveatMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.primaryText
                            )
                        }
                    }
                }

                if (saveFailed) {
                    Surface(
                        color = tokens.error.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, tokens.error),
                        shape = tokens.controlShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Could not save this meal photo. You can retry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.error,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

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
                    onNotesChange = { notes = it },
                    enabled = !isSaving
                )
            }
        },
        confirmButton = {
            TerminalPrimaryButton(
                text = when {
                    isSaving -> "SAVING…"
                    saveFailed -> "RETRY SAVE"
                    else -> "SAVE TO LOG"
                },
                enabled = !isSaving && isValid,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    saveFailed = false
                    val cal = NutritionFormat.parseCaloriesOrNull(caloriesStr) ?: meal.calories
                    val p = NutritionFormat.parseGrams(proteinStr)
                    val c = NutritionFormat.parseGrams(carbsStr)
                    val f = NutritionFormat.parseGrams(fatStr)
                    onSave(foodName.trim(), cal, p, c, f, isLiquid, notes.trim().ifBlank { null }) { saved ->
                        saveFailed = !saved
                    }
                }
            )
        },
        dismissButton = {
            TerminalSecondaryButton(
                text = "DISCARD",
                enabled = !isSaving,
                onClick = onDismiss
            )
        },
        shape = tokens.cardShape,
        containerColor = tokens.surface
    )
}

/**
 * Editable Barcode Scan Review Dialog with quick serving multipliers (0.5x, 1x, 1.5x, 2x).
 */
@Composable
fun BarcodeReviewDialog(
    initialEntry: MealEntry,
    onDismiss: () -> Unit,
    onSave: (MealEntry) -> Unit
) {
    val tokens = TerminalDesign.tokens
    val haptic = LocalHapticFeedback.current

    var foodName by rememberSaveable(initialEntry.id) { mutableStateOf(initialEntry.foodName) }
    var caloriesStr by rememberSaveable(initialEntry.id) { mutableStateOf(initialEntry.calories.toString()) }
    var proteinStr by rememberSaveable(initialEntry.id) {
        mutableStateOf(NutritionFormat.formatGramsValue(initialEntry.proteinGrams))
    }
    var carbsStr by rememberSaveable(initialEntry.id) {
        mutableStateOf(NutritionFormat.formatGramsValue(initialEntry.carbsGrams))
    }
    var fatStr by rememberSaveable(initialEntry.id) {
        mutableStateOf(NutritionFormat.formatGramsValue(initialEntry.fatGrams))
    }
    var isLiquid by rememberSaveable(initialEntry.id) { mutableStateOf(initialEntry.isLiquid) }
    var notes by rememberSaveable(initialEntry.id) { mutableStateOf(initialEntry.assessment.orEmpty()) }
    var selectedMultiplier by rememberSaveable(initialEntry.id) { mutableFloatStateOf(1f) }

    fun applyMultiplier(mult: Float) {
        selectedMultiplier = mult
        caloriesStr = (initialEntry.calories * mult).roundToInt().toString()
        proteinStr = NutritionFormat.formatGramsValue(initialEntry.proteinGrams * mult)
        carbsStr = NutritionFormat.formatGramsValue(initialEntry.carbsGrams * mult)
        fatStr = NutritionFormat.formatGramsValue(initialEntry.fatGrams * mult)
    }

    val isValid = NutritionFormat.isMealFormValid(foodName, caloriesStr, proteinStr, carbsStr, fatStr)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = tokens.primaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BARCODE PRODUCT FOUND",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.primaryAccent
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Adjust serving size or edit values before saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText
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
                // Serving Multiplier Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SERVING:",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText,
                        fontWeight = FontWeight.Bold
                    )
                    listOf(0.5f to "0.5×", 1f to "1×", 1.5f to "1.5×", 2f to "2×").forEach { (mult, label) ->
                        val active = selectedMultiplier == mult
                        Surface(
                            color = if (active) tokens.primaryAccent else tokens.elevatedSurface,
                            shape = tokens.controlShape,
                            border = BorderStroke(1.dp, if (active) tokens.primaryAccent else tokens.border),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 36.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    applyMultiplier(mult)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) tokens.background else tokens.primaryText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

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
                text = "SAVE PRODUCT",
                enabled = isValid,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val cal = NutritionFormat.parseCaloriesOrNull(caloriesStr) ?: initialEntry.calories
                    val p = NutritionFormat.parseGrams(proteinStr)
                    val c = NutritionFormat.parseGrams(carbsStr)
                    val f = NutritionFormat.parseGrams(fatStr)
                    onSave(
                        initialEntry.copy(
                            foodName = foodName.trim().take(120).ifBlank { initialEntry.foodName },
                            calories = cal.coerceIn(0, NutritionFormat.MAX_MEAL_CALORIES),
                            proteinGrams = p.coerceIn(0f, NutritionFormat.MAX_MEAL_MACRO_GRAMS),
                            carbsGrams = c.coerceIn(0f, NutritionFormat.MAX_MEAL_MACRO_GRAMS),
                            fatGrams = f.coerceIn(0f, NutritionFormat.MAX_MEAL_MACRO_GRAMS),
                            isLiquid = isLiquid,
                            assessment = notes.trim().take(500).ifBlank { initialEntry.assessment }
                        )
                    )
                }
            )
        },
        dismissButton = {
            TerminalSecondaryButton(
                text = "DISCARD",
                onClick = onDismiss
            )
        },
        shape = tokens.cardShape,
        containerColor = tokens.surface
    )
}

@Composable
private fun DeleteMealConfirmDialog(
    meal: MealEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = TerminalDesign.tokens
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.delete_dossier_record_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.error
            )
        },
        text = {
            Text(
                text = "Delete '${meal.foodName}' (${meal.calories} kcal)? This updates daily and weekly totals.",
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
                    onConfirm()
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
fun HistoryList(
    mealEntries: LazyPagingItems<MealEntry>,
    onDeleteMeal: (MealEntry) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    groupByDate: Boolean = true
) {
    val tokens = TerminalDesign.tokens
    val today = remember { LocalDate.now() }
    val zoneId = remember { ZoneId.systemDefault() }

    if (mealEntries.loadState.refresh is LoadState.Error || mealEntries.loadState.append is LoadState.Error) {
        Surface(
            color = tokens.error.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, tokens.error),
            shape = tokens.controlShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Could not load meal history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.error
                )
                TextButton(onClick = { mealEntries.retry() }) {
                    Text("RETRY", color = tokens.primaryAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        items(
            count = mealEntries.itemCount,
            key = mealEntries.itemKey { it.id }
        ) { index ->
            val entry = mealEntries[index] ?: return@items
            val entryDate = Instant.ofEpochMilli(entry.timestamp).atZone(zoneId).toLocalDate()
            val prevDate = if (groupByDate && index > 0) {
                mealEntries.peek(index - 1)?.let { prev ->
                    Instant.ofEpochMilli(prev.timestamp).atZone(zoneId).toLocalDate()
                }
            } else null

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (groupByDate && (index == 0 || entryDate != prevDate)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TerminalSectionHeader(
                        title = NutritionFormat.formatDateHeader(entryDate, today)
                    )
                }
                TerminalMealRow(
                    entry = entry,
                    showFullDate = !groupByDate,
                    onDelete = { onDeleteMeal(entry) },
                    onClick = { onNavigateToDetail(entry.id) }
                )
            }
        }
    }
}

@Composable
fun MealEntryItem(
    entry: MealEntry,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    TerminalMealRow(
        entry = entry,
        onClick = onClick,
        onDelete = onDelete
    )
}

@Composable
fun MealSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val tokens = TerminalDesign.tokens

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = tokens.minTouchTarget),
        placeholder = {
            Text(
                text = "Search meals or notes (e.g. Salmon, Shake)…",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search meals",
                tint = tokens.primaryAccent,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onQueryChange("")
                    },
                    modifier = Modifier.size(tokens.minTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = tokens.secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = tokens.controlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = tokens.primaryText,
            unfocusedTextColor = tokens.primaryText,
            focusedBorderColor = tokens.primaryAccent,
            unfocusedBorderColor = tokens.border,
            focusedContainerColor = tokens.surface,
            unfocusedContainerColor = tokens.surface
        ),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealFilterChipRow(
    selectedFilter: MealFilter,
    onSelectFilter: (MealFilter) -> Unit,
    sortOrder: MealSortOrder,
    modifier: Modifier = Modifier,
    onSelectSort: ((MealSortOrder) -> Unit)? = null,
    onToggleSortOrder: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val tokens = TerminalDesign.tokens
    var sortMenuOpen by remember { mutableStateOf(false) }

    // Wrapping FlowRow so all filter chips and the sort dropdown remain visible without blind horizontal scrolling
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            Surface(
                modifier = Modifier
                    .defaultMinSize(minHeight = tokens.minTouchTarget)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        if (onSelectSort != null) {
                            sortMenuOpen = true
                        } else {
                            onToggleSortOrder()
                        }
                    },
                shape = tokens.controlShape,
                color = tokens.elevatedSurface,
                border = BorderStroke(1.dp, tokens.primaryAccent.copy(alpha = 0.65f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort order",
                        tint = tokens.primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = sortOrder.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.primaryAccent
                    )
                }
            }

            if (onSelectSort != null) {
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false },
                    containerColor = tokens.elevatedSurface
                ) {
                    MealSortOrder.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (option == sortOrder) tokens.primaryAccent else tokens.primaryText,
                                    fontWeight = if (option == sortOrder) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                if (option == sortOrder) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = tokens.primaryAccent
                                    )
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
        }

        MealFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            Surface(
                modifier = Modifier
                    .defaultMinSize(minHeight = tokens.minTouchTarget)
                    .semantics {
                        role = Role.Tab
                        selected = isSelected
                    }
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onSelectFilter(filter)
                    },
                shape = tokens.controlShape,
                color = if (isSelected) tokens.primaryAccent else tokens.surface,
                border = BorderStroke(1.dp, if (isSelected) tokens.primaryAccent else tokens.border)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) tokens.background else tokens.secondaryText
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
    TerminalEmptyState(
        title = stringResource(R.string.empty_search_result_title),
        message = stringResource(R.string.empty_search_result_body),
        actionLabel = stringResource(R.string.clear_filters_button),
        onAction = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onReset()
        },
        modifier = modifier.padding(vertical = 16.dp)
    )
}
