package com.sharek.macromandate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.viewmodel.ComplianceStatus
import com.sharek.macromandate.viewmodel.MainViewModel
import com.sharek.macromandate.viewmodel.UiState

enum class ScreenState {
    DASHBOARD, CAMERA
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()
    val complianceStatus by viewModel.complianceStatus.collectAsState()
    
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
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
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

                Column(modifier = Modifier.fillMaxSize()) {
                    // Fix Overlap: statusBarsPadding applied here
                    StateStatusBanner(complianceStatus, Modifier.statusBarsPadding())
                    
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize()
                    ) {
                        SummaryCard(totalCalories)
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        ActionRow(
                            onCaptureMeal = {
                                when (PackageManager.PERMISSION_GRANTED) {
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                                        screenState = ScreenState.CAMERA
                                    }
                                    else -> {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            },
                            onImportImage = {
                                if (complianceStatus != ComplianceStatus.SUBVERSIVE) {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            },
                            importEnabled = complianceStatus != ComplianceStatus.SUBVERSIVE
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
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
                                onDeleteMeal = { viewModel.deleteMealEntry(it.id) }
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
fun StateStatusBanner(status: ComplianceStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        ComplianceStatus.EXEMPLARY -> Color(0xFF00E5FF) // Icy Cyan
        ComplianceStatus.ACCEPTABLE -> Color(0xFFFFEA00) // Sharp Yellow
        ComplianceStatus.SUBVERSIVE -> Color(0xFFFF1744) // Sharp Red
    }
    val text = when (status) {
        ComplianceStatus.EXEMPLARY -> "STATUS: EXEMPLARY. THE STATE IS PLEASED."
        ComplianceStatus.ACCEPTABLE -> "STATUS: ACCEPTABLE. OPTIMIZATION REQUIRED."
        ComplianceStatus.SUBVERSIVE -> "STATUS: SUBVERSIVE. PRIVILEGES REVOKED."
    }
    val textColor = Color.Black

    Surface(
        color = color,
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = textColor,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
        )
    }
}

@Composable
fun SummaryCard(totalCalories: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
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
            Text("CAPTURE MEAL", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onImportImage,
            modifier = Modifier.weight(1f),
            enabled = importEnabled,
            shape = RectangleShape,
            border = BorderStroke(1.dp, if (importEnabled) MaterialTheme.colorScheme.primary else Color.DarkGray)
        ) {
            if (importEnabled) {
                Text("IMPORT IMAGE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
    onDeleteMeal: (MealEntry) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(mealEntries) { entry ->
            MealEntryItem(
                entry = entry,
                onDelete = { onDeleteMeal(entry) }
            )
        }
    }
}

@Composable
fun MealEntryItem(
    entry: MealEntry,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, Color.DarkGray)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    }
}
