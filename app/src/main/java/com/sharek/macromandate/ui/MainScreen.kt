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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.stringResource
import com.sharek.macromandate.R
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.LoadState
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
    val photoReview by viewModel.photoReview.collectAsState()
    val isSavingPhoto by viewModel.isSavingPhoto.collectAsState()
    
    var screenState by remember { mutableStateOf(ScreenState.DASHBOARD) }
    var showManualEntryDialog by rememberSaveable { mutableStateOf(false) }
    var mealToDelete by remember { mutableStateOf<MealEntry?>(null) }
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val selectedFilter = MealFilter.entries[filterType]
    val sortType by viewModel.sortOrder.collectAsState()
    val sortOrder = MealSortOrder.entries[sortType]

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
                val mealEntries = viewModel.mealEntries.collectAsLazyPagingItems()
                
    val todayMeals by viewModel.todayMeals.collectAsState()
    val barcodeResult by viewModel.barcodeResult.collectAsState()

                // The card is labelled TOTAL DAILY CONSUMPTION, so it must sum today's
                // meals — mealEntries is the full history and belongs to the log below.
                val totalCalories = todayMeals.sumOf { it.calories }
                val todayProtein = todayMeals.sumOf { it.proteinGrams.toDouble() }.toInt()
                val todayCarbs = todayMeals.sumOf { it.carbsGrams.toDouble() }.toInt()
                val todayFat = todayMeals.sumOf { it.fatGrams.toDouble() }.toInt()
                val lastMealWasRestricted = todayMeals.firstOrNull()?.let {
                    it.isRestricted && System.currentTimeMillis() - it.timestamp < 60 * 60 * 1000L
                } ?: false


        
        if (barcodeResult != null) {
            val entry = barcodeResult!!
            AlertDialog(
                onDismissRequest = { viewModel.clearBarcodeResult() },
                title = {
                    Text(
                        text = "BARCODE SCANNED",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = {
                    Column {
                        Text("Product: ${entry.foodName}", color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Calories: ${entry.calories} kcal", color = Color.White)
                        Text("P: ${entry.proteinGrams}g | C: ${entry.carbsGrams}g | F: ${entry.fatGrams}g", color = Color.White)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.addMealEntry(entry)
                            viewModel.clearBarcodeResult()
                        },
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("SAVE RECORD", fontWeight = FontWeight.Black, color = Color.Black)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.clearBarcodeResult() },
                        shape = RectangleShape
                    ) {
                        Text("DISCARD", color = Color.Gray)
                    }
                },
                shape = RectangleShape,
                containerColor = Color(0xFF141414)
            )
        }

        if (showManualEntryDialog) {
                    ManualMealDialog(
                        onDismiss = { showManualEntryDialog = false },
                        onSave = { name, cal, p, c, f, isLiquid, complete ->
                            viewModel.logManualMeal(name, cal, p, c, f, isLiquid) { saved ->
                                complete(saved)
                                if (saved) showManualEntryDialog = false
                            }
                        }
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    DailyProgressHeader(
                        totalCalories = totalCalories,
                        target = target,
                        complianceStatus = complianceStatus,
                        modifier = Modifier.statusBarsPadding(),
                        isRestrictedViolation = lastMealWasRestricted
                    )
                    
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize()
                    ) {
                        MacroSummaryBar(
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
                        Text(
                            "Photo analysis sends a resized image to ${com.sharek.macromandate.network.ApiConfig.providerName}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
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
                                text = stringResource(R.string.recent_meals_header),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (mealEntries.itemCount > 0) {
                                Text(
                                    text = "${mealEntries.itemCount} LOGGED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        if (mealEntries.itemCount == 0 && searchQuery.isEmpty() && selectedFilter == MealFilter.ALL) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (hasApiKey) {
                                        stringResource(R.string.no_meals_api_key_prompt)
                                    } else {
                                        stringResource(R.string.no_meals_no_api_key_prompt)
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
                                onQueryChange = { viewModel.searchQuery.value = it }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            MealFilterChipRow(
                                selectedFilter = selectedFilter,
                                onSelectFilter = { viewModel.filterType.value = it.ordinal },
                                sortOrder = sortOrder,
                                onToggleSortOrder = { viewModel.sortOrder.value = sortOrder.next().ordinal }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (mealEntries.itemCount == 0) {
                                EmptySearchResultView(
                                    onReset = {
                                        viewModel.searchQuery.value = ""
                                        viewModel.filterType.value = MealFilter.ALL.ordinal
                                    }
                                )
                            } else {
                                HistoryList(
                                    mealEntries = mealEntries,
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
                                text = stringResource(R.string.delete_dossier_record_title),
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
                    }
                )
            }
        }

        photoReview?.let { review ->
            key(review.meal.id) {
                ManualMealDialog(
                    onDismiss = viewModel::discardPhotoReview,
                    onSave = viewModel::savePhotoReview,
                    estimate = review.meal,
                    savingEstimate = isSavingPhoto
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
fun TerminalTypewriterText(text: String, modifier: Modifier = Modifier) {
    var displayedText by remember(text) { mutableStateOf("") }
    var cursorVisible by remember { mutableStateOf(true) }

    LaunchedEffect(text) {
        text.forEachIndexed { index, _ ->
            displayedText = text.substring(0, index + 1)
            delay(30)
        }
        // Blink cursor 5 times then leave it solid to prevent infinite recomposition
        repeat(5) {
            cursorVisible = !cursorVisible
            delay(500)
        }
        cursorVisible = true
    }

    Text(
        text = displayedText + if (cursorVisible) "-^" else " ",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Black,
        color = Color.Black,
        modifier = modifier
    )
}


@Composable
fun DailyProgressHeader(
    totalCalories: Int,
    target: Int,
    complianceStatus: ComplianceStatus,
    modifier: Modifier = Modifier,
    isRestrictedViolation: Boolean = false
) {
    val progress = if (target > 0) totalCalories.toFloat() / target.toFloat() else 0f
    val percent = (progress * 100).toInt()
    val delta = totalCalories - target
    
    val statusColor = when {
        isRestrictedViolation -> Color.Red
        complianceStatus == ComplianceStatus.EXEMPLARY -> Color(0xFF00E5FF)
        complianceStatus == ComplianceStatus.ACCEPTABLE -> Color(0xFFFFEA00)
        complianceStatus == ComplianceStatus.SUBVERSIVE -> Color(0xFFFF1744)
        else -> Color.Gray
    }

    val statusText = when {
        isRestrictedViolation -> "RESTRICTED ZONE"
        complianceStatus == ComplianceStatus.EXEMPLARY -> "EXEMPLARY"
        complianceStatus == ComplianceStatus.ACCEPTABLE -> "ACCEPTABLE"
        complianceStatus == ComplianceStatus.SUBVERSIVE -> "SUBVERSIVE"
        else -> "CRISIS"
    }

    val (progressColor, messageText) = if (delta > 0) {
        Color.Red to "+$delta KCAL OVER"
    } else {
        statusColor to "${-delta} KCAL REMAINING"
    }

    Surface(
        color = Color(0xFF0A0A0A),
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "Daily progress: $totalCalories of $target calories, $percent percent. $messageText. Long term compliance is $statusText."
        },
        shape = RectangleShape,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "TODAY // $totalCalories / $target KCAL",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelMedium,
                    color = progressColor,
                    fontWeight = FontWeight.Black
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(Color(0xFF1A1A1A))
                    .border(1.dp, Color(0xFF333333))
            ) {
                // The filled portion
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(minOf(progress, 1f))
                        .background(progressColor)
                )
                
                // If over 100%, show an overflow indicator
                if (progress > 1f) {
                    val overflowWidth = minOf(progress - 1f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(overflowWidth)
                            .background(Color.Red.copy(alpha = 0.5f))
                            .align(Alignment.CenterEnd)
                    )
                }

                // Target marker
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(Color.White)
                        .align(Alignment.CenterEnd)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = messageText,
                    style = MaterialTheme.typography.labelSmall,
                    color = progressColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "COMPLIANCE: $statusText",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MacroSummaryBar(
    protein: Int,
    carbs: Int,
    fat: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hudFraming(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            .semantics(mergeDescendants = true) {
                contentDescription = "Macros today: $protein grams protein, $carbs grams carbs, $fat grams fat"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "P: ${protein}g",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = NutritionColors.Protein
            )
            Text(
                text = "C: ${carbs}g",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = NutritionColors.Carbs
            )
            Text(
                text = "F: ${fat}g",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = NutritionColors.Fat
            )
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
    onSave: (foodName: String, calories: Int, protein: Float, carbs: Float, fat: Float, isLiquid: Boolean, complete: (Boolean) -> Unit) -> Unit,
    estimate: MealEntry? = null,
    savingEstimate: Boolean = false
) {
    var savingManual by remember { mutableStateOf(false) }
    val saving = savingManual || savingEstimate
    var saveFailed by remember { mutableStateOf(false) }
    var foodName by rememberSaveable { mutableStateOf(estimate?.foodName.orEmpty()) }
    var caloriesStr by rememberSaveable { mutableStateOf(estimate?.calories?.toString().orEmpty()) }
    var proteinStr by rememberSaveable { mutableStateOf(estimate?.proteinGrams?.toString().orEmpty()) }
    var carbsStr by rememberSaveable { mutableStateOf(estimate?.carbsGrams?.toString().orEmpty()) }
    var fatStr by rememberSaveable { mutableStateOf(estimate?.fatGrams?.toString().orEmpty()) }
    var isLiquid by rememberSaveable { mutableStateOf(estimate?.isLiquid ?: false) }

    fun grams(value: String) = if (value.isBlank()) 0f else value.toFloatOrNull()?.takeIf(Float::isFinite)
    val calories = caloriesStr.toIntOrNull()
    val protein = grams(proteinStr)
    val carbs = grams(carbsStr)
    val fat = grams(fatStr)
    val canSaveEstimate = foodName.isNotBlank() && calories != null && calories in 0..20_000 &&
        listOf(protein, carbs, fat).all { it != null && it in 0f..1_000f }

    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(
                text = if (estimate == null) "MANUAL REFUELING ENTRY" else "Review nutrition estimate",
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
                if (estimate != null) {
                    Text("AI estimates can be inaccurate. Review or correct the values before saving.")
                }
                if (saveFailed) Text("Could not save this meal. You can retry.")
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
                enabled = !saving && (estimate == null || canSaveEstimate),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (estimate == null) savingManual = true
                    saveFailed = false
                    onSave(foodName.ifBlank { "MANUAL REFUELING" }, calories ?: 0, protein ?: 0f, carbs ?: 0f, fat ?: 0f, isLiquid) { saved ->
                        savingManual = false
                        saveFailed = !saved
                    }
                },
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    if (saving) "SAVING" else if (saveFailed) "RETRY SAVE" else if (estimate != null) "SAVE MEAL" else "LOG RECORD",
                    fontWeight = FontWeight.Black
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !saving, shape = RectangleShape) {
                Text("CANCEL", color = Color.Gray)
            }
        },
        shape = RectangleShape,
        containerColor = Color(0xFF141414)
    )
}

@Composable
fun HistoryList(
    mealEntries: LazyPagingItems<MealEntry>,
    onDeleteMeal: (MealEntry) -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    if (mealEntries.loadState.refresh is LoadState.Error || mealEntries.loadState.append is LoadState.Error) {
        TextButton(onClick = { mealEntries.retry() }) { Text("Could not load meals. Retry") }
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        // Clears the bottom navigation bar so the last entry is fully readable.
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        items(
            count = mealEntries.itemCount,
            key = mealEntries.itemKey { it.id }
        ) { index ->
            val entry = mealEntries[index] ?: return@items
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
                .defaultMinSize(minHeight = 48.dp)
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
                    .defaultMinSize(minHeight = 48.dp)
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
                text = stringResource(R.string.empty_search_result_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.empty_search_result_body),
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
                    text = stringResource(R.string.clear_filters_button),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
