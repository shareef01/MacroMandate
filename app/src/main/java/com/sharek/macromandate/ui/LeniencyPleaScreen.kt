package com.sharek.macromandate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sharek.macromandate.viewmodel.MainViewModel
import com.sharek.macromandate.viewmodel.UiState

@Composable
fun LeniencyPleaScreen(viewModel: MainViewModel, onLeniencyGranted: () -> Unit) {
    var pleaText by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "!! TERMINAL CRISIS !!",
                color = Color.Red,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "CRITICAL MANDATE SUBVERSION DETECTED. ALL SYSTEM MODULES ARE OFFLINE. PLEAD FOR LENIENCY OR ACCEPT PERMANENT TERMINATION.",
                color = Color.Red,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            OutlinedTextField(
                value = pleaText,
                onValueChange = { pleaText = it },
                modifier = Modifier.fillMaxWidth().hudFraming(Color.Red),
                label = { Text("JUSTIFICATION FOR SUBVERSION", color = Color.Red) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Red,
                    unfocusedBorderColor = Color.Red.copy(alpha = 0.5f)
                ),
                shape = RectangleShape,
                textStyle = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.submitLeniencyPlea(pleaText)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                enabled = pleaText.isNotBlank() && uiState !is UiState.Loading
            ) {
                Text("SUBMIT PLEA", fontWeight = FontWeight.Black)
            }
        }

        if (uiState is UiState.Loading) {
            Surface(color = Color.Red.copy(alpha = 0.5f), modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) {
                    Text("SYSTEM INTERROGATING PLEA...", color = Color.White, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun PermanentLockdownScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Red),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "[ PROPERTY OF THE STATE ]",
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "BIOLOGICAL ASSET SEIZED DUE TO TERMINAL SUBVERSION.",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}
