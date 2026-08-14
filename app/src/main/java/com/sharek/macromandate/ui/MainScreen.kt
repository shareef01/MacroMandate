package com.sharek.macromandate.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sharek.macromandate.model.MealEntry
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
    
    var screenState by remember { mutableStateOf(ScreenState.DASHBOARD) }

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
                val lastMealWasRestricted = mealEntries.firstOrNull()?.let { it.isRestricted && (System.currentTimeMillis() - it.timestamp) < 60 * 60 * 1000 } ?: false

                Column(modifier = Modifier.fillMaxSize()) {
                    StateStatusBanner(complianceStatus, Modifier.statusBarsPadding(), lastMealWasRestricted)
                    
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize()
                    ) {
                        SummaryCard(totalCalories)
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
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
                            importEnabled = complianceStatus != ComplianceStatus.SUBVERSIVE
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        HorizontalDivider(
                            thickness = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Recent meals",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // An indeterminate LinearProgressIndicator used to sit here as
                        // decoration; Material3 now draws those with a gap and a stop
                        // indicator, so it rendered as two disconnected dashes.
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
                            HistoryList(
                                mealEntries = mealEntries,
                                onDeleteMeal = { viewModel.deleteMealEntry(it.id) },
                                onNavigateToDetail = onNavigateToDetail
                            )
                        }
                    }
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
        shape = RectangleShape
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
fun SummaryCard(totalCalories: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hudFraming(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "TODAY",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$totalCalories kcal",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ActionRow(
    onCaptureMeal: () -> Unit,
    onImportImage: () -> Unit,
    importEnabled: Boolean = true
) {
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
            Text("Take photo", fontWeight = FontWeight.Bold, maxLines = 1)
        }
        OutlinedButton(
            onClick = onImportImage,
            modifier = Modifier.weight(1f),
            enabled = importEnabled,
            shape = RectangleShape,
            border = BorderStroke(1.dp, if (importEnabled) MaterialTheme.colorScheme.primary else Color.DarkGray)
        ) {
            if (importEnabled) {
                Text(
                    "Choose photo",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
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
                    Text("Locked", color = Color.DarkGray, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
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
        Column(modifier = Modifier.padding(12.dp)) {
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
            HorizontalDivider(thickness = 0.5.dp, color = Color.DarkGray.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "ID: ${entry.id.uppercase()}",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp),
                color = Color.Gray
            )
            val coordText = if (entry.latitude != null && entry.longitude != null) {
                "COORDS: ${"%.4f".format(entry.latitude)}, ${"%.4f".format(entry.longitude)}"
            } else {
                "COORDS: UNKNOWN (SIGNAL JAMMED)"
            }
            Text(
                text = coordText,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp),
                color = Color.Gray
            )
            Text(
                text = "TIMESTAMP: ${dateFormat.format(Date(entry.timestamp)).uppercase()}",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp),
                color = Color.Gray
            )
        }
    }
}
