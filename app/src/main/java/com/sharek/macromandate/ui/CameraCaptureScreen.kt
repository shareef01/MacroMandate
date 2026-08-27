package com.sharek.macromandate.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sharek.macromandate.util.EvidenceStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.Executor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCaptureScreen(
    onImageCaptured: (Uri) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val infiniteTransition = rememberInfiniteTransition(label = "HUD")
    
    val scanningY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Scanner"
    )

    val pulsingColor by infiniteTransition.animateColor(
        initialValue = primaryColor,
        targetValue = primaryColor.copy(alpha = 0.3f),
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    var tickerText by remember { mutableStateOf("") }
    val fullTicker = "Point the camera at your meal and tap the shutter.      "
    
    LaunchedEffect(Unit) {
        while(true) {
            fullTicker.indices.forEach { i ->
                tickerText = fullTicker.substring(i) + fullTicker.substring(0, i)
                delay(100L)
            }
        }
    }

    LaunchedEffect(Unit) {
        // Initial tactical readiness pulse on viewfinder activation
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraCaptureScreen", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .hudFraming(pulsingColor, length = 40.dp, thickness = 4.dp)
        )

        // Scanning Grid Overlay
        Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            val y = size.height * scanningY
            drawLine(
                color = pulsingColor.copy(alpha = 0.8f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2.dp.toPx()
            )
            drawRect(
                color = pulsingColor.copy(alpha = 0.1f),
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width, y)
            )
        }

        TopAppBar(
            modifier = Modifier.statusBarsPadding(),
            title = { Text("Take photo", fontWeight = FontWeight.Black) },
            navigationIcon = {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.6f),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        // Ticker Overlay
        Surface(
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 160.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RectangleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, pulsingColor.copy(alpha = 0.3f))
        ) {
            Text(
                text = tickerText,
                style = MaterialTheme.typography.labelSmall,
                color = pulsingColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
            )
        }

        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                takePhoto(
                    context = context,
                    imageCapture = imageCapture,
                    executor = ContextCompat.getMainExecutor(context),
                    onImageCaptured = onImageCaptured,
                    onCaptureError = { message ->
                        scope.launch {
                            snackbarHostState.showSnackbar("Couldn’t take the photo: $message")
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .size(80.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = primaryColor,
                contentColor = onPrimaryColor
            )
        ) {
            Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = "Take Photo",
                modifier = Modifier.size(48.dp)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit,
    onCaptureError: (String) -> Unit
) {
    // Written straight into EvidenceStore rather than cacheDir: the persisted meal
    // record points at this path, and the OS may evict anything under cacheDir.
    val file = EvidenceStore.newFile(context, UUID.randomUUID().toString())

    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraCaptureScreen", "Photo capture failed: ${exception.message}", exception)
                file.delete()
                onCaptureError(exception.message?.uppercase() ?: "UNKNOWN CAPTURE FAILURE")
            }

            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onImageCaptured(Uri.fromFile(file))
            }
        }
    )
}
