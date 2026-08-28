package com.sharek.macromandate.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharek.macromandate.data.local.AuditEntity
import com.sharek.macromandate.ui.theme.TerminalTheme
import com.sharek.macromandate.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ControlPanelScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val calorieTarget by viewModel.calorieTarget.collectAsState()
    val enforcementEnabled by viewModel.enforcementEnabled.collectAsState()
    val locationTrackingEnabled by viewModel.locationTrackingEnabled.collectAsState()
    val recentAudits by viewModel.recentAudits.collectAsState()
    val apiKeyHint by viewModel.apiKeyHint.collectAsState()
    val terminalTheme by viewModel.terminalTheme.collectAsState()

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let { target ->
                viewModel.exportDataTo(context, target) { succeeded ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (succeeded) "CSV dossier exported." else "Export failed."
                        )
                    }
                }
            }
        }
    )

    val createJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let { target ->
                viewModel.exportJsonBackupTo(context, target) { succeeded ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (succeeded) "JSON backup exported." else "JSON export failed."
                        )
                    }
                }
            }
        }
    )

    val openJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                pendingRestoreUri = it
            }
        }
    )

    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = {
                Text(
                    text = "RESTORE MEAL DATABASE?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text(
                    text = "This will parse the selected JSON backup and merge all records into your local database. Existing records with identical IDs will be updated.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingRestoreUri
                        pendingRestoreUri = null
                        if (uri != null) {
                            viewModel.importJsonBackupFrom(context, uri) { result ->
                                scope.launch {
                                    result.fold(
                                        onSuccess = { count ->
                                            snackbarHostState.showSnackbar("Restored $count records successfully.")
                                        },
                                        onFailure = { error ->
                                            snackbarHostState.showSnackbar("Restore failed: ${error.message}")
                                        }
                                    )
                                }
                            }
                        }
                    },
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("RESTORE", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingRestoreUri = null },
                    shape = RectangleShape
                ) {
                    Text("CANCEL", color = Color.Gray)
                }
            },
            shape = RectangleShape,
            containerColor = Color(0xFF141414)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Pinned Tactical Top Bar (Prevents content scrolling under status bar)
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
            ) {
                ApiKeyCard(
                    keyHint = apiKeyHint,
                    onSave = { key ->
                        viewModel.updateApiKey(key)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (key.isBlank()) "API key cleared." else "API key saved."
                            )
                        }
                    }
                )

            Spacer(modifier = Modifier.height(32.dp))

            TerminalThemeCard(
                currentTheme = terminalTheme,
                onSelectTheme = { viewModel.updateTerminalTheme(it) }
            )

            Spacer(modifier = Modifier.height(32.dp))

            SettingsCard(title = "Daily calorie target", icon = Icons.Default.Settings) {
                Column {
                    // Local value mirrors the persisted target; commit only when the
                    // user releases the slider to avoid DataStore/audit/widget spam.
                    var sliderValue by remember { mutableFloatStateOf(calorieTarget.toFloat()) }
                    LaunchedEffect(calorieTarget) { sliderValue = calorieTarget.toFloat() }
                    Text(
                        text = "${sliderValue.toInt()} kcal",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Black
                    )
                    Slider(
                        value = sliderValue,
                        // Was firing a haptic pulse on every onValueChange, i.e.
                        // continuously for the whole drag.
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            viewModel.updateCalorieTarget(sliderValue.toInt())
                        },
                        valueRange = 1200f..4000f,
                        steps = 28,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            SettingsCard(title = "Reminders", icon = Icons.Default.Notifications) {
                Column {
                    SettingRow(
                        label = "Remind me when a meal is overdue",
                        checked = enforcementEnabled,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleEnforcement(it)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Checks every 6 hours during the day and notifies you if " +
                            "nothing has been logged for a while.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            SettingsCard(title = "Location", icon = Icons.Default.LocationOn) {
                Column {
                    SettingRow(
                        label = "Tag meals with location",
                        checked = locationTrackingEnabled,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleLocationTracking(it)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Prominent disclosure: coordinates do not stay on the device.
                    Text(
                        text = "When on, your precise coordinates are saved with each meal, " +
                            "printed onto the photo, and that photo is sent to the analysis " +
                            "service. Off by default.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Your data",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(title = "Data portability & backup", icon = Icons.Default.Storage) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Export structured JSON archives for full database restoration, or CSV dossiers for external spreadsheet analysis.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )

                    // Export JSON Button
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            createJsonLauncher.launch("MacroMandate_Backup_${System.currentTimeMillis()}.json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Export JSON Backup", fontWeight = FontWeight.Black)
                    }

                    // Import JSON Button
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            openJsonLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Restore JSON Backup", fontWeight = FontWeight.Black)
                    }

                    // Export CSV Button
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            createCsvLauncher.launch("MacroMandate_Export_${System.currentTimeMillis()}.csv")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                        border = BorderStroke(1.dp, Color.Gray),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Export CSV Spreadsheet", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Activity log",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                if (recentAudits.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.clearActivityLog()
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text(
                            "CLEAR LOG",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.Gray
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            AuditBufferHud(recentAudits)

            // Clears the bottom navigation bar so the last card is fully reachable.
            Spacer(modifier = Modifier.height(96.dp))
        }
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
private fun SettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            // Without a weight the switch is pushed off-screen by a long label.
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun ApiKeyCard(
    keyHint: String,
    onSave: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    val hasKey = keyHint.isNotBlank()

    SettingsCard(title = "Analysis API key", icon = Icons.Default.Key) {
        Column {
            Text(
                text = if (hasKey) {
                    "A key is saved ($keyHint). Enter a new one to replace it."
                } else {
                    "Meal analysis needs a Hugging Face access token. Paste one here " +
                        "to turn it on — it is stored only on this device."
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("hf_...") },
                singleLine = true,
                shape = RectangleShape,
                visualTransformation = if (revealed) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false
                ),
                trailingIcon = {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            imageVector = if (revealed) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (revealed) "Hide key" else "Show key"
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSave(draft)
                        draft = ""
                        revealed = false
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RectangleShape
                ) {
                    Text("Save key", fontWeight = FontWeight.Bold)
                }
                if (hasKey) {
                    OutlinedButton(
                        onClick = {
                            onSave("")
                            draft = ""
                            revealed = false
                        },
                        modifier = Modifier.weight(1f),
                        shape = RectangleShape
                    ) {
                        Text("Clear", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, Color.DarkGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun AuditBufferHud(audits: List<AuditEntity>) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 220.dp),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
    ) {
        if (audits.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No activity yet",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.padding(8.dp),
                reverseLayout = true
            ) {
                items(audits.size) { index ->
                    val audit = audits[index]
                    Text(
                        text = "[${dateFormat.format(Date(audit.timestamp))}] ${audit.message}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp
                        ),
                        color = when (audit.category) {
                            "SECURITY", "SECURITY_JUDGMENT" -> Color.Red
                            "MANDATE_SHIFT", "CONFIG", "PRIVACY" -> Color.Cyan
                            else -> Color.Gray
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalThemeCard(
    currentTheme: TerminalTheme,
    onSelectTheme: (TerminalTheme) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    SettingsCard(title = "Terminal theme", icon = Icons.Default.Palette) {
        Column {
            Text(
                text = "Select tactical phosphor rendering and interface palette.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalTheme.entries.forEach { theme ->
                    val isSelected = theme == currentTheme
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectTheme(theme)
                            },
                        shape = RectangleShape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) theme.surfaceColor else Color.Black
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) theme.primaryColor else Color.DarkGray
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(theme.primaryColor, RectangleShape)
                                    .border(1.dp, Color.Black, RectangleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = theme.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) theme.primaryColor else Color.White
                                )
                                Text(
                                    text = theme.tagline,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Active",
                                    tint = theme.primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
