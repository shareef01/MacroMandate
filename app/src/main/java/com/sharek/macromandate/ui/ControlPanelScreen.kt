package com.sharek.macromandate.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.sharek.macromandate.util.BiometricAuthenticator
import com.sharek.macromandate.viewmodel.ComplianceStatus
import com.sharek.macromandate.viewmodel.MainViewModel
import com.sharek.macromandate.data.local.AuditEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlPanelScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isUnlocked by remember { mutableStateOf(false) }

    val calorieTarget by viewModel.calorieTarget.collectAsState()
    val enforcementEnabled by viewModel.enforcementEnabled.collectAsState()
    val complianceStatus by viewModel.complianceStatus.collectAsState()
    val recentAudits by viewModel.recentAudits.collectAsState()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let { saveCsvToUri(context, it, viewModel) }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            isUnlocked = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isUnlocked) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = if (complianceStatus == ComplianceStatus.SUBVERSIVE) Color.Red else Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = if (complianceStatus == ComplianceStatus.SUBVERSIVE) "ACCESS DENIED" else "ACCESS RESTRICTED",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = if (complianceStatus == ComplianceStatus.SUBVERSIVE) Color.Red else Color.Unspecified
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (complianceStatus == ComplianceStatus.SUBVERSIVE) 
                            "YOUR SUBVERSIVE STATUS HAS VOIDED ALL ACCESS PRIVILEGES." 
                            else "STATE CLEARANCE REQUIRED TO ALTER MANDATES.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                    if (complianceStatus != ComplianceStatus.SUBVERSIVE) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val authenticator = BiometricAuthenticator(context as FragmentActivity)
                                authenticator.authenticate(
                                    onSuccess = { 
                                    viewModel.logAudit("SECURITY", "BIOMETRIC CLEARANCE GRANTED.")
                                    isUnlocked = true 
                                },
                                    onError = { _, err ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar("ACCESS DENIED: ${err.toString().uppercase()}")
                                        }
                                    },
                                    onFailed = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("AUTHENTICATION FAILED")
                                        }
                                    }
                                )
                            },
                            shape = RectangleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("REQUEST CLEARANCE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = "MANDATE CONFIGURATION",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(24.dp))

                SettingsCard(
                    title = "CALORIE TARGET",
                    icon = Icons.Default.Settings
                ) {
                    Column {
                        // Local value mirrors the persisted target; commit only when the
                        // user releases the slider to avoid DataStore/audit/widget spam.
                        var sliderValue by remember { mutableFloatStateOf(calorieTarget.toFloat()) }
                        LaunchedEffect(calorieTarget) { sliderValue = calorieTarget.toFloat() }
                        Text(
                            text = "${sliderValue.toInt()} KCAL",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Black
                        )
                        Slider(
                            value = sliderValue,
                            onValueChange = {
                                sliderValue = it
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
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

                Spacer(modifier = Modifier.height(48.dp))

                SettingsCard(
                    title = "ENFORCEMENT PROTOCOL",
                    icon = Icons.Default.Notifications
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "BACKGROUND SURVEILLANCE", fontWeight = FontWeight.Black)
                            Switch(
                                checked = enforcementEnabled,
                                onCheckedChange = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleEnforcement(it) 
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "PERSISTENT SURVEILLANCE HUD", fontWeight = FontWeight.Black)
                            Text(text = "ACTIVE", color = Color.Green, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = "DATA EXFILTRATION",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        createDocumentLauncher.launch("MacroMandate_Dossier_${System.currentTimeMillis()}.csv")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = complianceStatus != ComplianceStatus.SUBVERSIVE,
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (complianceStatus == ComplianceStatus.SUBVERSIVE) Color.DarkGray else Color(0xFFD500F9), // Harsh Neon Purple
                        disabledContainerColor = Color(0xFF1A1A1A)
                    )
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (complianceStatus == ComplianceStatus.SUBVERSIVE) "[ EXFILTRATION FORBIDDEN ]" else "EXFILTRATE DOSSIER (CSV)",
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = "SYSTEM BUFFER",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                AuditBufferHud(recentAudits)
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
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
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

private fun saveCsvToUri(context: Context, uri: Uri, viewModel: MainViewModel) {
    viewModel.exportData { csv ->
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(csv.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun AuditBufferHud(audits: List<AuditEntity>) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (audits.size >= 45) {
                Text(
                    "!! BUFFER WARNING: CAPACITY NEAR LIMIT !!", 
                    color = Color.Yellow, 
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            androidx.compose.foundation.lazy.LazyColumn(reverseLayout = true) {
                items(audits.size) { index ->
                    val audit = audits[index]
                    Text(
                        text = "[${dateFormat.format(Date(audit.timestamp))}] ${audit.category}: ${audit.message}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 9.sp
                        ),
                        color = when(audit.category) {
                            "SECURITY" -> Color.Red
                            "MANDATE_SHIFT" -> Color.Cyan
                            else -> Color.Gray
                        }
                    )
                }
            }
        }
    }
}

