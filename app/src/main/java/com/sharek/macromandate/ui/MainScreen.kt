package com.sharek.macromandate.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.ui.theme.NutritionColors
import com.sharek.macromandate.viewmodel.ComplianceStatus
import com.sharek.macromandate.viewmodel.MainViewModel
import com.sharek.macromandate.viewmodel.UiState
import com.sharek.macromandate.ui.hudFraming
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

enum class ScreenState {
    DASHBOARD, CAMERA
}

enum class MealFilter(val label: String) {
    ALL("ALL"),
    TODAY("TODAY"),
    HIGH_PROTEIN("HIGH PROTEIN"),
    HIGH_CAL("HIGH CAL"),
    LIQUID("LIQUID"),
    FLAGGED("FLAGGED")
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
fun MainScreen(viewModel: MainViewModel, onNavigateToDetail: (String) -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()
    val complianceStatus by viewModel.complianceStatus.collectAsState()
    val locationTrackingEnabled by viewModel.locationTrackingEnabled.collectAsState()
    val hasApiKey by viewModel.hasApiKey.collectAsState()
    val target by viewModel.calorieTarget.collectAsState()
    
    var screenState by remember { mutableStateOf(ScreenState.DASHBOARD) }
    var showManualEntryDialog by remember { mutableStateOf(false) }
    var mealToDelete by remember { mutableStateOf<MealEntry?>(null) }
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
                val lastMealWasRestricted = mealEntries.firstOrNull()?.let { it.isRestricted && (System.currentTimeMillis() - it.timestamp) < 60 * 60 * 1000 } ?: false

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
                    StateStatusBanner(complianceStatus, Modifier.statusBarsPadding(), lastMealWasRestricted)
                    
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
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (complianceStatus != ComplianceStatus.SUBVERSIVE) {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            },
                            onManualEntry = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showManualEntryDialog = true
                            },
                            importEnabled = complianceStatus != ComplianceStatus.SUBVERSIVE
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
                                text = "Recent meals",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (mealEntries.isNotEmpty()) {
                                Text(
                                    text = "${filteredMealEntries.size}/${mealEntries.size} LOGGED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        if (mealEntries.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (hasApiKey) {
                                        "No meals logged yet.\nTake or choose a photo to start."
                                    } else {
                                        "Add an API key in Settings to start logging meals."
                                    },
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
                                onToggleSortOrder = { sortOrder = sortOrder.next() }
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
                                text = "DELETE DOSSIER RECORD?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        text = {
                            Text(
                                text = "Are you sure you want to delete '${targetMeal.foodName.uppercase()}' (${targetMeal.calories} kcal)? This will update daily and weekly aggregates.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
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
                                Text("DELETE", fontWeight = FontWeight.Black)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { mealToDelete = null },
                                shape = RectangleShape
                            ) {
                                Text("CANCEL", color = Color.Gray)
                            }
                        },
                        shape = RectangleShape,
                        containerColor = Color(0xFF141414)
                    )
                }
            }
            ScreenState.CAMERA -> {
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
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Analyzing photo...",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

@Composable
fun StateStatusBanner(status: ComplianceStatus, modifier: Modifier = Modifier, isRestrictedViolation: Boolean = false) {
    val color = when {
        isRestrictedViolation -> Color.Red
        status == ComplianceStatus.EXEMPLARY -> Color(0xFF00E5FF) // Icy Cyan
        status == ComplianceStatus.ACCEPTABLE -> Color(0xFFFFEA00) // Sharp Yellow
        status == ComplianceStatus.SUBVERSIVE -> Color(0xFFFF1744) // Sharp Red
        else -> Color.Gray
    }
    // Flavour lives here, in the status line, where it costs nothing to understand.
    // Buttons and settings stay plain so the app is still operable.
    val text = when {
        isRestrictedViolation -> "Logged in a restricted zone."
        status == ComplianceStatus.EXEMPLARY -> "On target. The State is pleased."
        status == ComplianceStatus.ACCEPTABLE -> "Close to target. Room to improve."
        status == ComplianceStatus.SUBVERSIVE -> "Well off target. Some features locked."
        else -> ""
    }
    
    Surface(
        color = color,
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.25f))
    ) {
        TerminalTypewriterText(
            text = text,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
        )
    }
}

@Composable
fun TerminalTypewriterText(text: String, modifier: Modifier = Modifier) {
    var displayedText by remember(text) { mutableStateOf("") }
    var cursorVisible by remember { mutableStateOf(true) }

    LaunchedEffect(text) {
        text.forEachIndexed { index, _ ->
            displayedText = text.substring(0, index + 1)
            delay(30)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            cursorVisible = !cursorVisible
            delay(500)
        }
    }

    Text(
text = displayedText + if (cursorVisible) "█" else " ",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Black,
        color = Color.Black,
        modifier = modifier
    )
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
    val deltaText = if (delta > 0) "+$delta kcal over target" else "${-delta} kcal remaining"
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
                    text = "TODAY",
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
                    text = "/ $target kcal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "P: ${protein}g",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = NutritionColors.Protein
                )
                Text(
                    text = "C: ${carbs}g",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = NutritionColors.Carbs
                )
                Text(
                    text = "F: ${fat}g",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = NutritionColors.Fat
                )
            }
        }
    }
}

@Composable
fun ActionRow(
    onCaptureMeal: () -> Unit,
    onImportImage: () -> Unit,
    onManualEntry: () -> Unit,
    importEnabled: Boolean = true
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
                Text("Take photo", fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onImportImage,
                modifier = Modifier.weight(1f),
                enabled = importEnabled,
                shape = RectangleShape,
                border = BorderStroke(1.dp, if (importEnabled) MaterialTheme.colorScheme.primary else Color.DarkGray)
            ) {
                if (importEnabled) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Choose photo",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        fontSize = 12.sp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Locked", color = Color.DarkGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

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
                "Manual Refueling Log",
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
    var foodName by remember { mutableStateOf("") }
    var caloriesStr by remember { mutableStateOf("") }
    var proteinStr by remember { mutableStateOf("") }
    var carbsStr by remember { mutableStateOf("") }
    var fatStr by remember { mutableStateOf("") }
    var isLiquid by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "MANUAL REFUELING ENTRY",
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
                    label = { Text("Item Name") },
                    singleLine = true,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = caloriesStr,
                    onValueChange = { caloriesStr = it.filter { ch -> ch.isDigit() } },
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
                    OutlinedTextField(
                        value = proteinStr,
                        onValueChange = { proteinStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("P (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RectangleShape,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbsStr,
                        onValueChange = { carbsStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("C (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RectangleShape,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fatStr,
                        onValueChange = { fatStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("F (g)") },
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
                        text = "Liquid consumption (Beverage / Shake)",
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
                    val cal = caloriesStr.toIntOrNull() ?: 0
                    val p = proteinStr.toFloatOrNull() ?: 0f
                    val c = carbsStr.toFloatOrNull() ?: 0f
                    val f = fatStr.toFloatOrNull() ?: 0f
                    onSave(foodName.ifBlank { "MANUAL REFUELING" }, cal, p, c, f, isLiquid)
                },
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("LOG RECORD", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RectangleShape) {
                Text("CANCEL", color = Color.Gray)
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
        items(mealEntries) { entry ->
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
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US) }
    val haptic = LocalHapticFeedback.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        text = entry.foodName.uppercase(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${entry.proteinGrams.toInt()}P ${entry.carbsGrams.toInt()}C ${entry.fatGrams.toInt()}F",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${entry.calories} KCAL",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "DELETE",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = Color.DarkGray.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = "ID: ${entry.id.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            val coordText = if (entry.latitude != null && entry.longitude != null) {
                "COORDS: ${"%.4f".format(entry.latitude)}, ${"%.4f".format(entry.longitude)}"
            } else {
                "COORDS: NOT RECORDED"
            }
            Text(
                text = coordText,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Text(
                text = "TIMESTAMP: ${dateFormat.format(Date(entry.timestamp)).uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
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
                "Filter records (e.g. Steak, 40P, Night)",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onQueryChange("")
                }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
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
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace
        )
    )
}

@Composable
fun MealFilterChipRow(
    selectedFilter: MealFilter,
    onSelectFilter: (MealFilter) -> Unit,
    sortOrder: MealSortOrder,
    onToggleSortOrder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sort toggle button
        Surface(
            modifier = Modifier
                .defaultMinSize(minHeight = 44.dp)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSortOrder()
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
                    contentDescription = "Sort",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = sortOrder.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Filter chips
        MealFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            Surface(
                modifier = Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        text = filter.label,
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
                text = "NO DOSSIER ENTRIES MATCH FILTER",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Try adjusting your search terms or filter selection.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onReset()
                },
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "CLEAR FILTERS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
