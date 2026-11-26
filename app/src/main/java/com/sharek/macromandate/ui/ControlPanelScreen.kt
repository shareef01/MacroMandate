package com.sharek.macromandate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharek.macromandate.BuildConfig
import com.sharek.macromandate.data.local.AuditEntity
import com.sharek.macromandate.network.ApiConfig
import com.sharek.macromandate.ui.components.*
import com.sharek.macromandate.ui.theme.TerminalDesign
import com.sharek.macromandate.ui.theme.TerminalTheme
import com.sharek.macromandate.util.NutritionFormat
import com.sharek.macromandate.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ControlPanelScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tokens = TerminalDesign.tokens

    val calorieTarget by viewModel.calorieTarget.collectAsStateWithLifecycle()
    val enforcementEnabled by viewModel.enforcementEnabled.collectAsStateWithLifecycle()
    val locationTrackingEnabled by viewModel.locationTrackingEnabled.collectAsStateWithLifecycle()
    val shareLocationWithAnalysis by viewModel.shareLocationWithAnalysis.collectAsStateWithLifecycle()
    val recentAudits by viewModel.recentAudits.collectAsStateWithLifecycle()
    val apiKeyHint by viewModel.apiKeyHint.collectAsStateWithLifecycle()
    val terminalTheme by viewModel.terminalTheme.collectAsStateWithLifecycle()
    val crtEffectsEnabled by viewModel.crtEffectsEnabled.collectAsStateWithLifecycle()

    var notificationsBlocked by remember { mutableStateOf(!canPostNotifications(context)) }
    var locationPermissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showEraseConfirm by rememberSaveable { mutableStateOf(false) }
    var showClearLocationsConfirm by rememberSaveable { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val reminderPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsBlocked = !canPostNotifications(context)
        if (granted) {
            viewModel.toggleEnforcement(true)
        } else {
            scope.launch { snackbarHostState.showSnackbar("Notifications are turned off for MacroMandate.") }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        locationPermissionGranted = hasLocationPermission(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsBlocked = !canPostNotifications(context)
                locationPermissionGranted = hasLocationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    val createZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri ->
            uri?.let { target ->
                viewModel.exportZipBackupTo(context, target) { succeeded ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (succeeded) "ZIP backup exported." else "ZIP export failed."
                        )
                    }
                }
            }
        }
    )

    val openZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { pendingRestoreUri = it }
        }
    )

    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = {
                Text(
                    text = "RESTORE BACKUP ARCHIVE?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.primaryAccent
                )
            },
            text = {
                Text(
                    text = "Imports meal records and any included photos. If an archive contains a meal ID that already exists in your log, the restore stops without overwriting your existing records. Older JSON backups restore meal records only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.primaryText
                )
            },
            confirmButton = {
                TerminalPrimaryButton(
                    text = "RESTORE",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val uri = pendingRestoreUri
                        pendingRestoreUri = null
                        if (uri != null) {
                            viewModel.importZipBackupFrom(context, uri) { result ->
                                scope.launch {
                                    result.fold(
                                        onSuccess = { count ->
                                            snackbarHostState.showSnackbar("Restored $count meal record(s).")
                                        },
                                        onFailure = { error ->
                                            snackbarHostState.showSnackbar("Restore failed: ${error.message}")
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            },
            dismissButton = {
                TerminalSecondaryButton(
                    text = "CANCEL",
                    onClick = { pendingRestoreUri = null }
                )
            },
            shape = tokens.cardShape,
            containerColor = tokens.surface
        )
    }

    if (showClearLocationsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearLocationsConfirm = false },
            title = {
                Text(
                    text = "REMOVE STORED LOCATION COORDINATES?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.warning
                )
            },
            text = {
                Text(
                    text = "Removes saved GPS coordinates from all existing meal records on this device. Your meals, nutrition values, and photos will be kept.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.primaryText
                )
            },
            confirmButton = {
                TerminalPrimaryButton(
                    text = "REMOVE LOCATIONS",
                    isDestructive = true,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showClearLocationsConfirm = false
                        viewModel.clearStoredLocations { count ->
                            scope.launch {
                                val msg = if (count >= 0) {
                                    "Removed coordinates from $count meal record(s)."
                                } else {
                                    "Could not clear stored location coordinates."
                                }
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    }
                )
            },
            dismissButton = {
                TerminalSecondaryButton(
                    text = "CANCEL",
                    onClick = { showClearLocationsConfirm = false }
                )
            },
            shape = tokens.cardShape,
            containerColor = tokens.surface
        )
    }

    if (showEraseConfirm) {
        AlertDialog(
            onDismissRequest = { showEraseConfirm = false },
            title = {
                Text(
                    text = "ERASE ALL MEAL DATA?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.error
                )
            },
            text = {
                Text(
                    text = "Permanently deletes every meal record, saved photograph, and activity log entry from this device. Your settings and API key are preserved. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.primaryText
                )
            },
            confirmButton = {
                TerminalPrimaryButton(
                    text = "DELETE EVERYTHING",
                    isDestructive = true,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showEraseConfirm = false
                        viewModel.deleteAllData { succeeded ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (succeeded) "All meal records and photos deleted."
                                    else "Records cleared, though some files could not be removed."
                                )
                            }
                        }
                    }
                )
            },
            dismissButton = {
                TerminalSecondaryButton(
                    text = "CANCEL",
                    onClick = { showEraseConfirm = false }
                )
            },
            shape = tokens.cardShape,
            containerColor = tokens.surface
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TerminalTopBar(
                title = "SETTINGS",
                subtitle = "SYSTEM CONFIGURATION & DATA SOVEREIGNTY"
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = tokens.maxContentWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. NUTRITION GOALS
                    TerminalSectionHeader(title = "1. Nutrition Goals", badge = "$calorieTarget KCAL")
                    SettingsCard(title = "Daily Calorie Target", icon = Icons.Default.TrackChanges) {
                        CalorieTargetControl(
                            target = calorieTarget,
                            onTargetChange = { viewModel.updateCalorieTarget(it) }
                        )
                    }

                    // 2. APPEARANCE & ACCESSIBILITY
                    TerminalSectionHeader(title = "2. Appearance & Accessibility", badge = terminalTheme.displayName)
                    TerminalThemeCard(
                        currentTheme = terminalTheme,
                        onSelectTheme = { viewModel.updateTerminalTheme(it) }
                    )
                    SettingsCard(title = "CRT Display Effects", icon = Icons.Default.Palette) {
                        SettingRow(
                            label = "Enable CRT scanlines & vignette",
                            description = "Applies subtle retro scanlines and screen vignette. Turn off for maximum text contrast and reduced visual noise.",
                            checked = crtEffectsEnabled,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                viewModel.updateCrtEffectsEnabled(it)
                            }
                        )
                    }

                    // 3. AI ANALYSIS
                    TerminalSectionHeader(
                        title = "3. AI Analysis",
                        badge = if (apiKeyHint.isNotBlank()) "KEY SAVED" else "LOCAL ONLY"
                    )
                    ApiKeyCard(
                        keyHint = apiKeyHint,
                        isTestingConnection = isTestingConnection,
                        connectionTestMessage = connectionTestMessage,
                        onSave = { key ->
                            connectionTestMessage = null
                            viewModel.updateApiKey(key) { saved ->
                                scope.launch {
                                    val message = when {
                                        !saved -> "Could not save the API key."
                                        key.isBlank() -> "API key cleared."
                                        else -> "API key encrypted and saved."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        },
                        onTestConnection = {
                            isTestingConnection = true
                            connectionTestMessage = null
                            viewModel.testApiConnection { result ->
                                isTestingConnection = false
                                connectionTestMessage = result.fold(
                                    onSuccess = { true to it },
                                    onFailure = { false to (it.message ?: "Connection test failed.") }
                                )
                            }
                        }
                    )

                    // 4. NOTIFICATIONS & REMINDERS
                    TerminalSectionHeader(
                        title = "4. Notifications & Reminders",
                        badge = if (enforcementEnabled && !notificationsBlocked) "ON" else "OFF"
                    )
                    SettingsCard(title = "Meal Logging Reminders", icon = Icons.Default.Notifications) {
                        Column {
                            SettingRow(
                                label = "Remind me when a meal is overdue",
                                description = "Checks every 6 hours between 08:00 and 22:00 and posts a reminder if no meal has been logged recently.",
                                checked = enforcementEnabled,
                                onCheckedChange = { enabled ->
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    if (enabled && needsNotificationPermission(context)) {
                                        reminderPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.toggleEnforcement(enabled)
                                    }
                                }
                            )
                            if (enforcementEnabled && notificationsBlocked) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = tokens.error.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, tokens.error),
                                    shape = tokens.controlShape,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Notifications are disabled for MacroMandate in Android system settings, so reminders cannot be shown.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.error,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 5. PRIVACY & PERMISSIONS
                    TerminalSectionHeader(
                        title = "5. Privacy & Permissions",
                        badge = if (locationTrackingEnabled) "GEOTAG ON" else "GEOTAG OFF"
                    )
                    SettingsCard(title = "Location Metadata", icon = Icons.Default.LocationOn) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingRow(
                                label = "Tag camera meals with location",
                                description = "Attaches GPS coordinates locally to meals captured with the camera. Turning this off stops future collection without deleting existing history.",
                                checked = locationTrackingEnabled,
                                onCheckedChange = { enabled ->
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    viewModel.toggleLocationTracking(enabled)
                                    if (enabled && !locationPermissionGranted) {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                }
                            )

                            if (locationTrackingEnabled && !locationPermissionGranted) {
                                Surface(
                                    color = tokens.warning.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, tokens.warning),
                                    shape = tokens.controlShape,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Android location permission is not granted.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.primaryText,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = {
                                                locationPermissionLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                                    )
                                                )
                                            }
                                        ) {
                                            Text("GRANT", color = tokens.primaryAccent, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            if (locationTrackingEnabled) {
                                HorizontalDivider(color = tokens.divider)
                                SettingRow(
                                    label = "Include coordinates in AI analysis image",
                                    description = "When enabled, prints coordinates onto the image copy sent to ${ApiConfig.providerName}. Off by default.",
                                    checked = shareLocationWithAnalysis,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        viewModel.toggleShareLocationWithAnalysis(it)
                                    }
                                )
                            }

                            HorizontalDivider(color = tokens.divider)

                            TerminalSecondaryButton(
                                text = "REMOVE STORED LOCATION DATA",
                                icon = Icons.Default.LocationOff,
                                accentColor = tokens.warning,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    showClearLocationsConfirm = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 6. BACKUP, RESTORATION & EXPORTS
                    TerminalSectionHeader(title = "6. Backup, Restoration & Exports")
                    SettingsCard(title = "Data Portability", icon = Icons.Default.Storage) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Export a complete ZIP archive (meal records + photos), restore from a ZIP or legacy JSON backup, or export a spreadsheet-safe CSV.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.secondaryText
                            )

                            val stamp = remember { SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) }

                            TerminalPrimaryButton(
                                text = "EXPORT ZIP BACKUP (MEALS + PHOTOS)",
                                icon = Icons.Default.FileDownload,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    createZipLauncher.launch("MacroMandate_Backup_$stamp.zip")
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            TerminalSecondaryButton(
                                text = "RESTORE FROM BACKUP (ZIP / JSON)",
                                icon = Icons.Default.FileUpload,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    openZipLauncher.launch(
                                        arrayOf("application/zip", "application/x-zip-compressed", "application/json", "*/*")
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            TerminalSecondaryButton(
                                text = "EXPORT CSV SPREADSHEET",
                                icon = Icons.Default.Description,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    createCsvLauncher.launch("MacroMandate_Export_$stamp.csv")
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    SettingsCard(title = "Destructive Reset", icon = Icons.Default.DeleteForever) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Permanently deletes all logged meals, stored meal photos, and activity logs from this device. Export a backup first if you want to keep your records.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.secondaryText
                            )
                            TerminalSecondaryButton(
                                text = "ERASE ALL MEAL DATA",
                                icon = Icons.Default.DeleteForever,
                                accentColor = tokens.error,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    showEraseConfirm = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 7. ABOUT & APPLICATION INFORMATION
                    TerminalSectionHeader(
                        title = "7. About & System Telemetry",
                        badge = "v${BuildConfig.VERSION_NAME}"
                    )
                    SettingsCard(title = "Application Information", icon = Icons.Default.Info) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailRow(label = "Version", value = "${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})")
                            DetailRow(label = "Storage Engine", value = "Local Room SQLite v8 + KeyStore AES-256-GCM")
                            DetailRow(label = "AI Endpoint", value = "${ApiConfig.providerName} (${ApiConfig.model})")
                            DetailRow(label = "Telemetry & Cloud Sync", value = "None (100% Local-First)")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LOCAL ACTIVITY LOG",
                            style = MaterialTheme.typography.labelLarge,
                            color = tokens.primaryAccent,
                            fontWeight = FontWeight.Bold
                        )
                        if (recentAudits.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.clearActivityLog()
                                },
                                modifier = Modifier.defaultMinSize(minHeight = tokens.minTouchTarget)
                            ) {
                                Text(
                                    text = "CLEAR LOG",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.secondaryText
                                )
                            }
                        }
                    }

                    AuditBufferHud(recentAudits)

                    Spacer(modifier = Modifier.height(80.dp))
                }
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
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null
) {
    val tokens = TerminalDesign.tokens
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = tokens.minTouchTarget)
                .clickable(
                    role = Role.Switch,
                    onClick = { onCheckedChange(!checked) }
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.primaryText,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = tokens.background,
                    checkedTrackColor = tokens.primaryAccent,
                    uncheckedThumbColor = tokens.secondaryText,
                    uncheckedTrackColor = tokens.elevatedSurface
                )
            )
        }
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.secondaryText
            )
        }
    }
}

@Composable
private fun CalorieTargetControl(
    target: Int,
    onTargetChange: (Int) -> Unit
) {
    val tokens = TerminalDesign.tokens
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    var draft by remember(target) { mutableIntStateOf(target) }
    var typed by remember(target) { mutableStateOf(target.toString()) }

    fun commit(value: Int) {
        val clamped = value.coerceIn(
            NutritionFormat.MIN_CALORIE_TARGET,
            NutritionFormat.MAX_CALORIE_TARGET
        )
        draft = clamped
        typed = clamped.toString()
        if (clamped != target) {
            onTargetChange(clamped)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = typed,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(5)
                    typed = digits
                    digits.toIntOrNull()?.let { draft = it }
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focus ->
                        if (!focus.isFocused) commit(typed.toIntOrNull() ?: target)
                    },
                label = { Text("Daily Target") },
                suffix = { Text("kcal", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                shape = tokens.controlShape,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        commit(typed.toIntOrNull() ?: target)
                        focusManager.clearFocus()
                    }
                ),
                textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace)
            )

            TargetStepperButton(
                description = "Decrease target by 50 calories",
                glyph = "−50",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    commit(draft - 50)
                }
            )
            TargetStepperButton(
                description = "Increase target by 50 calories",
                glyph = "+50",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    commit(draft + 50)
                }
            )
        }

        Slider(
            value = draft.coerceIn(1200, 4500).toFloat(),
            onValueChange = { value ->
                draft = (value.toInt() / 25) * 25
                typed = draft.toString()
            },
            onValueChangeFinished = { commit(draft) },
            valueRange = 1200f..4500f,
            modifier = Modifier.semantics {
                contentDescription = "Calorie target slider. Current target is $draft calories."
            },
            colors = SliderDefaults.colors(
                thumbColor = tokens.primaryAccent,
                activeTrackColor = tokens.primaryAccent,
                inactiveTrackColor = tokens.elevatedSurface
            )
        )

        Text(
            text = "Slider adjusts between 1,200 and 4,500 kcal. Type directly above for any target from ${NutritionFormat.MIN_CALORIE_TARGET} to ${NutritionFormat.MAX_CALORIE_TARGET} kcal.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.secondaryText
        )
    }
}

@Composable
private fun TargetStepperButton(
    description: String,
    glyph: String,
    onClick: () -> Unit
) {
    val tokens = TerminalDesign.tokens
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .defaultMinSize(minWidth = 52.dp, minHeight = tokens.minTouchTarget)
            .semantics { contentDescription = description },
        shape = tokens.controlShape,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        border = BorderStroke(1.dp, tokens.primaryAccent)
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.labelMedium,
            color = tokens.primaryAccent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ApiKeyCard(
    keyHint: String,
    isTestingConnection: Boolean,
    connectionTestMessage: Pair<Boolean, String>?,
    onSave: (String) -> Unit,
    onTestConnection: () -> Unit
) {
    val tokens = TerminalDesign.tokens
    val haptic = LocalHapticFeedback.current

    var draft by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }
    val hasKey = keyHint.isNotBlank()
    val provider = ApiConfig.providerName
    val wrongProvider = draft.isNotBlank() && !ApiConfig.acceptsKey(draft)

    var confirmingClear by remember { mutableStateOf(false) }
    LaunchedEffect(confirmingClear) {
        if (confirmingClear) {
            delay(3500)
            confirmingClear = false
        }
    }

    SettingsCard(title = "$provider Vision & Summary Key", icon = Icons.Default.Key) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (hasKey) {
                    "A credential is encrypted in Android KeyStore ($keyHint). Enter a new key below to replace it."
                } else {
                    "Optional: Enter an API key from $provider (model: ${ApiConfig.model}) to enable AI photo analysis and daily summaries. Manual logging and barcode lookup work without a key."
                },
                style = MaterialTheme.typography.bodySmall,
                color = tokens.secondaryText
            )

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("$provider API Key") },
                placeholder = { Text(if (provider == "OpenRouter") "sk-or-v1-…" else "Enter API key…") },
                isError = wrongProvider,
                supportingText = if (wrongProvider) {
                    { Text("Enter a valid $provider key beginning with 'sk-or-' (no spaces).") }
                } else null,
                singleLine = true,
                shape = tokens.controlShape,
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
                    IconButton(
                        onClick = { revealed = !revealed },
                        modifier = Modifier.size(tokens.minTouchTarget)
                    ) {
                        Icon(
                            imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (revealed) "Hide API key" else "Show API key"
                        )
                    }
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalPrimaryButton(
                    text = "SAVE KEY",
                    enabled = draft.isNotBlank() && !wrongProvider,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSave(draft)
                        draft = ""
                        revealed = false
                    },
                    modifier = Modifier.weight(1f)
                )

                if (hasKey) {
                    TerminalSecondaryButton(
                        text = if (confirmingClear) "CONFIRM CLEAR?" else "CLEAR KEY",
                        accentColor = if (confirmingClear) tokens.error else tokens.secondaryText,
                        onClick = {
                            if (confirmingClear) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSave("")
                                draft = ""
                                revealed = false
                                confirmingClear = false
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                confirmingClear = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (hasKey) {
                TerminalSecondaryButton(
                    text = if (isTestingConnection) "TESTING CONNECTION…" else "TEST PROVIDER CONNECTION",
                    icon = Icons.Default.NetworkCheck,
                    enabled = !isTestingConnection,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onTestConnection()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            connectionTestMessage?.let { (ok, msg) ->
                val accent = if (ok) tokens.primaryAccent else tokens.error
                Surface(
                    color = accent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accent),
                    shape = tokens.controlShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.primaryText,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    val tokens = TerminalDesign.tokens
    TerminalCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.primaryAccent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.primaryText
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        content()
    }
}

@Composable
fun AuditBufferHud(audits: List<AuditEntity>) {
    val tokens = TerminalDesign.tokens
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 220.dp),
        shape = tokens.cardShape,
        colors = CardDefaults.cardColors(containerColor = tokens.surface),
        border = BorderStroke(1.dp, tokens.border)
    ) {
        if (audits.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No activity recorded yet",
                    color = tokens.secondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(10.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(audits.size, key = { index -> audits[index].id }) { index ->
                    val audit = audits[index]
                    Text(
                        text = "[${dateFormat.format(Date(audit.timestamp))}] ${audit.message}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = when (audit.category) {
                            "SECURITY", "SECURITY_JUDGMENT" -> tokens.error
                            "MANDATE_SHIFT", "CONFIG", "PRIVACY" -> tokens.primaryAccent
                            else -> tokens.secondaryText
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
    val tokens = TerminalDesign.tokens

    SettingsCard(title = "Terminal Color Palette", icon = Icons.Default.Palette) {
        Column {
            Text(
                text = "Select phosphor rendering and semantic interface palette.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.secondaryText
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalTheme.entries.forEach { theme ->
                    val isSelected = theme == currentTheme
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = tokens.minTouchTarget)
                            .semantics {
                                role = Role.RadioButton
                                selected = isSelected
                            }
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onSelectTheme(theme)
                            },
                        shape = tokens.controlShape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) theme.elevatedSurfaceColor else theme.surfaceColor
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) theme.primaryColor else tokens.border
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
                                    .background(theme.primaryColor, tokens.controlShape)
                                    .border(1.dp, Color.Black, tokens.controlShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = theme.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) theme.primaryColor else theme.textPrimaryColor
                                )
                                Text(
                                    text = theme.tagline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.textSecondaryColor
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected theme",
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

private fun needsNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED

private fun canPostNotifications(context: android.content.Context): Boolean =
    !needsNotificationPermission(context) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun hasLocationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
