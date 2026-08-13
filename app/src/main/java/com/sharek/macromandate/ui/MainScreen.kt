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
import androidx.compose.material.icons.filled.Fingerprint
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
import androidx.fragment.app.FragmentActivity
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.util.BiometricAuthenticator
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
    
    var screenState by remember { mutableStateOf(ScreenState.DASHBOARD) }

    val performIngestAction = { action: () -> Unit ->
        if (complianceStatus == ComplianceStatus.EXEMPLARY) {
            action()
        } else {
            val authenticator = BiometricAuthenticator(context as FragmentActivity)
            authenticator.authenticate(
                onSuccess = {
                    viewModel.logAudit("SECURITY", "INGEST VERIFIED: ${complianceStatus.name}")
                    action()
                },
                onError = { _, err ->
                    viewModel.logAudit("SECURITY", "INGEST FAILED: $err")
                },
                onFailed = {
                    viewModel.logAudit("SECURITY", "INGEST AUTH FAILED")
                }
            )
        }
    }

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
        when (uiState) {
            is UiState.Error -> {
                snackbarHostState.showSnackbar("ERROR: ${(uiState as UiState.Error).message.uppercase()}")
                viewModel.resetUiState()
            }
            is UiState.Success -> {
                snackbarHostState.showSnackbar("LOGGED: ${(uiState as UiState.Success).mealName.uppercase()}")
                viewModel.resetUiState()
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (screenState) {
            ScreenState.DASHBOARD -> {
                val mealEntries by viewModel.mealEntries.collectAsState()
                val totalCalories = mealEntries.sumOf { it.calories }
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
                                performIngestAction {
                                    val permissions = arrayOf(
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                    if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                                        screenState = ScreenState.CAMERA
                                    } else {
                                        cameraPermissionLauncher.launch(permissions)
                                    }
                                }
                            },
                            onImportImage = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (complianceStatus != ComplianceStatus.SUBVERSIVE) {
                                    performIngestAction {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                }
                            },
                            importEnabled = complianceStatus != ComplianceStatus.SUBVERSIVE,
                            requiresAuth = complianceStatus != ComplianceStatus.EXEMPLARY
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        HorizontalDivider(
                            thickness = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "> SURVEILLANCE LOG // RECENT",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Color.Black),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            trackColor = Color.Transparent
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (mealEntries.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "[ NO BIOLOGICAL DATA LOGGED TODAY ]",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
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
                            "ANALYZING BIOLOGICAL DATA...",
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
    val text = when {
        isRestrictedViolation -> "WARNING: RESTRICTED ZONE INTAKE DETECTED."
        status == ComplianceStatus.EXEMPLARY -> "STATUS: EXEMPLARY. THE STATE IS PLEASED."
        status == ComplianceStatus.ACCEPTABLE -> "STATUS: ACCEPTABLE. OPTIMIZATION REQUIRED."
        status == ComplianceStatus.SUBVERSIVE -> "STATUS: SUBVERSIVE. PRIVILEGES REVOKED."
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
                text = "TOTAL DAILY CONSUMPTION",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$totalCalories KCAL",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun ActionRow(
    onCaptureMeal: () -> Unit,
    onImportImage: () -> Unit,
    importEnabled: Boolean = true,
    requiresAuth: Boolean = false
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (requiresAuth) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text("CAPTURE MEAL", fontWeight = FontWeight.Bold)
            }
        }
        OutlinedButton(
            onClick = onImportImage,
            modifier = Modifier.weight(1f),
            enabled = importEnabled,
            shape = RectangleShape,
            border = BorderStroke(1.dp, if (importEnabled) MaterialTheme.colorScheme.primary else Color.DarkGray)
        ) {
            if (importEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (requiresAuth) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text("IMPORT IMAGE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("LOCKED", color = Color.DarkGray, fontWeight = FontWeight.Bold)
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
        contentPadding = PaddingValues(bottom = 16.dp)
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
