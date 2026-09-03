package com.sharek.macromandate.ui

import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sharek.macromandate.data.local.AuditEntity
import com.sharek.macromandate.ui.theme.TerminalTheme
import com.sharek.macromandate.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.stringResource
import com.sharek.macromandate.R
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource

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
    var showEraseConfirm by remember { mutableStateOf(false) }

    // Resolved here, in composition, because the launcher and click callbacks
    // below are not composables.
    val csvExported = stringResource(R.string.export_csv_success)
    val csvFailed = stringResource(R.string.export_csv_failed)
    val jsonExported = stringResource(R.string.export_json_success)
    val jsonFailed = stringResource(R.string.export_json_failed)
    val eraseDone = stringResource(R.string.settings_erase_done)
    val eraseFailed = stringResource(R.string.settings_erase_failed)
    val keySaved = stringResource(R.string.settings_api_key_saved)
    val keyCleared = stringResource(R.string.settings_api_key_cleared)
    val restoreFallbackError = stringResource(R.string.restore_error_unreadable)

    // Reflects whether reminders can actually be delivered, so the toggle cannot
    // sit there claiming to be on while the OS silently drops every notification.
    var notificationsBlocked by remember { mutableStateOf(!canPostNotifications(context)) }
    // LocalResources rather than LocalContext.resources: it is the observable
    // one, so a configuration change re-reads it.
    val resources = LocalResources.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsBlocked = !granted }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Permission can be revoked from system settings while the app is
        // backgrounded; re-check on the way back rather than trusting a cached value.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsBlocked = !canPostNotifications(context)
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
                            if (succeeded) csvExported else csvFailed
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
                            if (succeeded) jsonExported else jsonFailed
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
                    text = stringResource(R.string.restore_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.restore_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // This button writes every record in the file into the
                        // live database — a real commit — but had no haptic at
                        // all, less tactile confirmation than an ordinary
                        // filter chip.
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val uri = pendingRestoreUri
                        pendingRestoreUri = null
                        if (uri != null) {
                            viewModel.importJsonBackupFrom(context, uri) { result ->
                                scope.launch {
                                    result.fold(
                                        onSuccess = { count ->
                                            snackbarHostState.showSnackbar(
                                                resources.getQuantityString(R.plurals.restore_success, count, count)
                                            )
                                        },
                                        onFailure = { error ->
                                            // The reason is a resource id on RestoreFailure;
                                                // anything else stays generic rather than
                                                // surfacing an exception string.
                                                val message = (error as? MainViewModel.RestoreFailure)
                                                    ?.let { resources.getString(it.messageRes) }
                                                    ?: restoreFallbackError
                                                snackbarHostState.showSnackbar(message)
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
                    Text(stringResource(R.string.restore_confirm), fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingRestoreUri = null },
                    shape = RectangleShape
                ) {
                    Text(stringResource(R.string.action_cancel), color = Color.Gray)
                }
            },
            shape = RectangleShape,
            containerColor = Color(0xFF141414)
        )
    }

    if (showEraseConfirm) {
        AlertDialog(
            onDismissRequest = { showEraseConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_erase_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_erase_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // The single most destructive action in the app had no
                        // haptic confirmation at all.
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showEraseConfirm = false
                        viewModel.deleteAllData { succeeded ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (succeeded) eraseDone else eraseFailed
                                )
                            }
                        }
                    },
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.settings_erase_confirm_button), fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                // Cancel first in reading order and visually plainer than confirm:
                // the safe choice should be the easy one on a destructive dialog.
                OutlinedButton(onClick = { showEraseConfirm = false }, shape = RectangleShape) {
                    Text(stringResource(R.string.settings_erase_cancel), color = Color.Gray)
                }
            },
            shape = RectangleShape,
            containerColor = MaterialTheme.colorScheme.surface
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
                        text = stringResource(R.string.settings_title),
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
                // Six cards used to sit in one undifferentiated column with only
                // "Your data" below marked as its own section — nothing signalled
                // why the order was what it was. Two more headers turn one long
                // scroll into three legible zones.
                SectionHeading(stringResource(R.string.settings_section_analysis))
                Spacer(modifier = Modifier.height(16.dp))

                ApiKeyCard(
                    keyHint = apiKeyHint,
                    onSave = { key ->
                        viewModel.updateApiKey(key)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (key.isBlank()) keyCleared else keySaved
                            )
                        }
                    }
                )

            Spacer(modifier = Modifier.height(32.dp))

            SectionHeading(stringResource(R.string.settings_section_appearance))
            Spacer(modifier = Modifier.height(16.dp))

            TerminalThemeCard(
                currentTheme = terminalTheme,
                onSelectTheme = { viewModel.updateTerminalTheme(it) }
            )

            Spacer(modifier = Modifier.height(32.dp))

            SettingsCard(title = stringResource(R.string.settings_calorie_target), icon = Icons.Default.Settings) {
                CalorieTargetControl(
                    target = calorieTarget,
                    onTargetChange = { viewModel.updateCalorieTarget(it) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            SettingsCard(title = stringResource(R.string.settings_reminders), icon = Icons.Default.Notifications) {
                Column {
                    SettingRow(
                        label = stringResource(R.string.settings_reminders_toggle),
                        checked = enforcementEnabled,
                        onCheckedChange = { enabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.toggleEnforcement(enabled)
                            // Asked for here, where the user has just said they
                            // want notifications — not on first launch before
                            // they know the feature exists.
                            if (enabled && needsNotificationPermission(context)) {
                                notificationPermissionLauncher.launch(
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_reminders_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    // Denied twice, Android stops showing the dialog entirely, so
                    // the only honest thing left to do is say where the switch is.
                    if (enforcementEnabled && notificationsBlocked) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_reminders_blocked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            SettingsCard(title = stringResource(R.string.settings_location), icon = Icons.Default.LocationOn) {
                Column {
                    SettingRow(
                        label = stringResource(R.string.settings_location_toggle),
                        checked = locationTrackingEnabled,
                        onCheckedChange = {
                            // Matches the Reminders switch above: same
                            // component, same gesture, same feedback. This one
                            // used to fire LongPress while Reminders fired
                            // ContextClick for an identical toggle tap.
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.toggleLocationTracking(it)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Prominent disclosure: coordinates do not stay on the device.
                    Text(
                        text = stringResource(R.string.settings_location_description),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            SectionHeading(stringResource(R.string.settings_your_data))
            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(title = stringResource(R.string.settings_data_portability), icon = Icons.Default.Storage) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_data_description),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )

                    // Export JSON Button
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            createJsonLauncher.launch("MacroMandate_Backup_${exportTimestamp()}.json")
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
                        Text(text = stringResource(R.string.settings_export_json), fontWeight = FontWeight.Black)
                    }

                    // Import JSON Button. Light feedback: this only opens the
                    // file picker and then a confirmation dialog — the actual
                    // restore commit is the RESTORE button in that dialog.
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
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
                        Text(text = stringResource(R.string.settings_restore_json), fontWeight = FontWeight.Black)
                    }

                    // Export CSV Button. Theme-derived, matching its two
                    // siblings above — this was the one button styled with fixed
                    // gray/white regardless of the active theme.
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            createCsvLauncher.launch("MacroMandate_Export_${exportTimestamp()}.csv")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.settings_export_csv), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(title = stringResource(R.string.settings_erase), icon = Icons.Default.DeleteForever) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_erase_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            showEraseConfirm = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_erase_button), fontWeight = FontWeight.Bold)
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
                    text = stringResource(R.string.settings_activity_log),
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
                            stringResource(R.string.settings_clear_log),
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
    val haptic = LocalHapticFeedback.current

    // Clear used to fire on a single tap, immediately, right beside Save —
    // one mistap wiped a working key with no confirmation, unlike every other
    // destructive control in this screen. A second tap within a few seconds
    // arms it, without the weight of a full modal dialog for something the
    // user can recover from just by re-entering the key.
    var confirmingClear by remember { mutableStateOf(false) }
    LaunchedEffect(confirmingClear) {
        if (confirmingClear) {
            delay(3000)
            confirmingClear = false
        }
    }

    SettingsCard(title = stringResource(R.string.settings_api_key), icon = Icons.Default.Key) {
        Column {
            Text(
                text = if (hasKey) {
                    stringResource(R.string.settings_api_key_present, keyHint)
                } else {
                    stringResource(R.string.settings_api_key_absent)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_api_key_hint)) },
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
                            contentDescription = stringResource(
                                if (revealed) R.string.content_description_hide_key
                                else R.string.content_description_show_key
                            )
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
                    Text(stringResource(R.string.settings_api_key_save), fontWeight = FontWeight.Bold)
                }
                if (hasKey) {
                    // One derived color and one derived label for the armed
                    // state, read by both the button's styling and its text,
                    // rather than three separate if/else branches that could
                    // drift out of sync with each other.
                    val clearColor = if (confirmingClear) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    val clearLabel = if (confirmingClear) {
                        stringResource(R.string.settings_api_key_clear_confirm)
                    } else {
                        stringResource(R.string.settings_api_key_clear)
                    }
                    OutlinedButton(
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
                        modifier = Modifier.weight(1f),
                        shape = RectangleShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = clearColor),
                        border = BorderStroke(1.dp, clearColor)
                    ) {
                        Text(clearLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** A section boundary inside the Settings scroll — groups related cards, doesn't title any one of them. */
@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black
    )
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
                    text = stringResource(R.string.empty_no_activity),
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
                        // Theme-derived rather than fixed red/cyan: this log
                        // used to show the same two hardcoded colors regardless
                        // of the active theme, so it never matched Phosphor
                        // Green, Amber CRT, or Stark Mono.
                        color = when (audit.category) {
                            "SECURITY", "SECURITY_JUDGMENT" -> MaterialTheme.colorScheme.error
                            "MANDATE_SHIFT", "CONFIG", "PRIVACY" -> MaterialTheme.colorScheme.primary
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

    SettingsCard(title = stringResource(R.string.settings_theme), icon = Icons.Default.Palette) {
        Column {
            Text(
                text = stringResource(R.string.settings_theme_description),
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
                                // A selection from a list, like a sort or
                                // filter choice — light feedback, not the
                                // heavier commit/destructive cue.
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
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
                                    style = MaterialTheme.typography.labelLarge,
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
                                    contentDescription = stringResource(R.string.content_description_theme_active),
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


/**
 * The daily calorie target: a number you can type, a stepper, and a slider.
 *
 * This was slider-only, with `valueRange = 1200f..4000f` and `steps = 28` — i.e.
 * exactly 29 reachable values, 100 kcal apart. Someone whose target was 1850, or
 * 4200, or 900 under medical supervision, simply could not enter it. A target is
 * the one number the whole app measures against, and it was the least precise
 * control in the app.
 *
 * The bounds here are storage sanity limits, deliberately far wider than any
 * recommendation. The app has no business having an opinion about what someone's
 * target should be; it only needs the value to be a number it can divide by.
 */
@Composable
private fun CalorieTargetControl(
    target: Int,
    onTargetChange: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Mirrors the persisted value while the user is interacting, so a drag does
    // not write to DataStore, the audit log and the widget on every frame.
    var draft by remember(target) { mutableIntStateOf(target) }
    var typed by remember(target) { mutableStateOf(target.toString()) }

    fun commit(value: Int) {
        val clamped = value.coerceIn(MIN_TARGET, MAX_TARGET)
        draft = clamped
        typed = clamped.toString()
        onTargetChange(clamped)
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = typed,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(5)
                    typed = digits
                    digits.toIntOrNull()?.let { draft = it }
                },
                // Commit on focus loss rather than per keystroke, so typing "2"
                // on the way to "2500" does not briefly set a 2 kcal target.
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focus ->
                        if (!focus.isFocused) commit(typed.toIntOrNull() ?: target)
                    },
                label = { Text(stringResource(R.string.settings_target_field)) },
                suffix = { Text(stringResource(R.string.settings_target_unit), style = MaterialTheme.typography.labelMedium) },
                singleLine = true,
                shape = RectangleShape,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { commit(typed.toIntOrNull() ?: target) }
                ),
                textStyle = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.width(8.dp))

            StepButton(
            pluralStringResource(R.plurals.content_description_decrease_target, TARGET_STEP, TARGET_STEP),
            "\u2212"
        ) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                commit(draft - TARGET_STEP)
            }
            Spacer(modifier = Modifier.width(4.dp))
            StepButton(
            pluralStringResource(R.plurals.content_description_increase_target, TARGET_STEP, TARGET_STEP),
            "+"
        ) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                commit(draft + TARGET_STEP)
            }
        }

        Slider(
            value = draft.coerceIn(SLIDER_MIN, SLIDER_MAX).toFloat(),
            // Was firing a haptic pulse on every onValueChange, i.e. continuously
            // for the whole drag.
            onValueChange = { value ->
                draft = value.toInt()
                typed = draft.toString()
            },
            onValueChangeFinished = { commit(draft) },
            valueRange = SLIDER_MIN.toFloat()..SLIDER_MAX.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        Text(
            text = stringResource(
                R.string.settings_target_range, SLIDER_MIN, SLIDER_MAX, MIN_TARGET, MAX_TARGET
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun StepButton(description: String, glyph: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        // 48dp: a stepper the user taps repeatedly is the last place to be stingy
        // with the touch target.
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description },
        shape = RectangleShape,
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Storage sanity bounds, not dietary advice.
 *
 * Wide enough to cover a medically supervised low intake at one end and an
 * endurance athlete at the other, so the app never has to tell someone their own
 * target is wrong.
 */
private const val MIN_TARGET = 500
private const val MAX_TARGET = 10_000

/** The comfortable range for dragging; typing reaches the rest. */
private const val SLIDER_MIN = 1_200
private const val SLIDER_MAX = 4_500

private const val TARGET_STEP = 50


/** True when POST_NOTIFICATIONS is required on this OS version and not yet granted. */
private fun needsNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED

/**
 * Whether a reminder would actually reach the user.
 *
 * Checks both the runtime permission and the app-level notification switch: the
 * permission can be granted while notifications are still disabled for the app,
 * and in that state a reminder is posted and silently discarded.
 */
private fun canPostNotifications(context: android.content.Context): Boolean =
    !needsNotificationPermission(context) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()
