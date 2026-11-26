package com.sharek.macromandate.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharek.macromandate.ui.components.TerminalCard
import com.sharek.macromandate.ui.components.TerminalLoadingOverlay
import com.sharek.macromandate.ui.components.TerminalPrimaryButton
import com.sharek.macromandate.ui.components.TerminalSecondaryButton
import com.sharek.macromandate.ui.theme.TerminalDesign
import com.sharek.macromandate.viewmodel.MainViewModel
import com.sharek.macromandate.viewmodel.UiState
import kotlinx.coroutines.launch

@Suppress("UNUSED_PARAMETER")
@Composable
fun LeniencyPleaScreen(viewModel: MainViewModel, onLeniencyGranted: () -> Unit = {}) {
    var pleaText by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val tokens = TerminalDesign.tokens
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val approvedReset by viewModel.approvedReset.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    approvedReset?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissApprovedReset,
            title = {
                Text(
                    text = "CLEAR MEAL HISTORY?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.error
                )
            },
            text = {
                Text(
                    text = "$message\n\nThis permanently deletes all meal records and their stored photos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.primaryText
                )
            },
            confirmButton = {
                TerminalPrimaryButton(
                    text = "CLEAR HISTORY",
                    isDestructive = true,
                    onClick = viewModel::confirmApprovedReset
                )
            },
            dismissButton = {
                TerminalSecondaryButton(
                    text = "KEEP HISTORY",
                    onClick = viewModel::dismissApprovedReset
                )
            },
            shape = tokens.cardShape,
            containerColor = tokens.surface
        )
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is UiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetUiState()
            }
            is UiState.Success -> {
                snackbarHostState.showSnackbar(state.mealName)
                viewModel.resetUiState()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = tokens.maxContentWidth)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TerminalCard(
                withHudCorners = true,
                accentBorder = tokens.warning
            ) {
                Text(
                    text = "LENIENCY REVIEW",
                    color = tokens.warning,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Submit a note to request an AI-reviewed log reset. Your records are never deleted without your explicit confirmation.",
                    color = tokens.primaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = pleaText,
                    onValueChange = { pleaText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Context or notes") },
                    shape = tokens.controlShape,
                    textStyle = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Submitting sends this text and your current compliance status to your analysis provider.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText
                )
                Spacer(modifier = Modifier.height(20.dp))

                TerminalPrimaryButton(
                    text = "SUBMIT REQUEST",
                    enabled = pleaText.isNotBlank() && uiState !is UiState.Loading,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.submitLeniencyPlea(pleaText)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (uiState is UiState.Loading) {
            TerminalLoadingOverlay(
                title = "REVIEWING REQUEST",
                subtitle = "Waiting for provider response…"
            )
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
fun PermanentLockdownScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val tokens = TerminalDesign.tokens
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let { target ->
                viewModel.exportDataTo(context, target) { succeeded ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (succeeded) "Data exported." else "Export failed."
                        )
                    }
                }
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = tokens.maxContentWidth)
                .padding(24.dp)
        ) {
            TerminalCard(
                withHudCorners = true,
                accentBorder = tokens.error
            ) {
                Text(
                    text = "TERMINAL PAUSED",
                    color = tokens.error,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "You can export your data or immediately unlock your log below.",
                    color = tokens.primaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                TerminalSecondaryButton(
                    text = "EXPORT CSV DATA",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        createDocumentLauncher.launch(
                            "MacroMandate_FinalDossier_${System.currentTimeMillis()}.csv"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                TerminalPrimaryButton(
                    text = "UNLOCK LOG",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.requestReinstatement()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
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
