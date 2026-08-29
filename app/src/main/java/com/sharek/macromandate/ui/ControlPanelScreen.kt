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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.sharek.macromandate.util.BiometricAuthenticator
import com.sharek.macromandate.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlPanelScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isUnlocked by remember { mutableStateOf(false) }

    val calorieTarget by viewModel.calorieTarget.collectAsState()
    val enforcementEnabled by viewModel.enforcementEnabled.collectAsState()

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
                        tint = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "ACCESS RESTRICTED",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "STATE CLEARANCE REQUIRED TO ALTER MANDATES.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                    Button(
                        onClick = {
                            val authenticator = BiometricAuthenticator(context as FragmentActivity)
                            authenticator.authenticate(
                                onSuccess = { isUnlocked = true },
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
                        Text(
                            text = "$calorieTarget KCAL", 
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Black
                        )
                        Slider(
                            value = calorieTarget.toFloat(),
                            onValueChange = { viewModel.updateCalorieTarget(it.toInt()) },
                            valueRange = 1200f..4000f,
                            steps = 28,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SettingsCard(
                    title = "ENFORCEMENT PROTOCOL",
                    icon = Icons.Default.Notifications
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "BACKGROUND SURVEILLANCE", fontWeight = FontWeight.Black)
                        Switch(
                            checked = enforcementEnabled,
                            onCheckedChange = { viewModel.toggleEnforcement(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary
                            )
                        )
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
                        createDocumentLauncher.launch("MacroMandate_Dossier_${System.currentTimeMillis()}.csv")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD500F9) // Harsh Neon Purple
                    )
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("EXFILTRATE DOSSIER (CSV)", fontWeight = FontWeight.Black)
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
            Spacer(modifier = Modifier.height(24.dp))
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
