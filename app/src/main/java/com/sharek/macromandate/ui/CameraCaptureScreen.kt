package com.sharek.macromandate.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.sharek.macromandate.ui.components.TerminalCard
import com.sharek.macromandate.ui.components.TerminalPrimaryButton
import com.sharek.macromandate.ui.components.TerminalSecondaryButton
import com.sharek.macromandate.ui.theme.TerminalDesign
import com.sharek.macromandate.util.EvidenceStore
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCaptureScreen(
    onImageCaptured: (Uri, String) -> Unit,
    onBarcodeScanned: (String) -> Unit,
    onManualEntry: () -> Unit,
    onBack: () -> Unit,
    crtEffectsEnabled: Boolean = true
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tokens = TerminalDesign.tokens

    val previewView = remember { PreviewView(context) }
    val imageCapture: ImageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    var isScanningEnabled by remember { mutableStateOf(true) }
    var takingPhoto by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    val hasFlashUnit = boundCamera?.cameraInfo?.hasFlashUnit() == true

    val active = remember { AtomicBoolean(true) }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        BarcodeScanning.getClient(options)
    }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val primaryColor = tokens.primaryAccent
    val onPrimaryColor = tokens.background

    val infiniteTransition = rememberInfiniteTransition(label = "CameraHUD")
    val scanningY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ScannerLine"
    )
    val pulsingColor by infiniteTransition.animateColor(
        initialValue = primaryColor,
        targetValue = if (crtEffectsEnabled) primaryColor.copy(alpha = 0.4f) else primaryColor,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FramePulse"
    )

    DisposableEffect(lifecycleOwner) {
        active.set(true)
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            if (!active.get()) return@addListener
            try {
                val cameraProvider = cameraProviderFuture.get()
                provider = cameraProvider

                val selector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> null
                }

                if (selector == null) {
                    cameraError = "No camera hardware was detected on this device."
                    cameraReady = false
                    return@addListener
                }

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    if (!active.get() || !isScanningEnabled || takingPhoto) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                if (barcodes.isNotEmpty()) {
                                    val code = barcodes.first().rawValue
                                    if (!code.isNullOrBlank() && active.get() && isScanningEnabled && !takingPhoto) {
                                        isScanningEnabled = false
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onBarcodeScanned(code)
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
                boundCamera = camera
                cameraError = null
                cameraReady = true
            } catch (e: Exception) {
                Log.w("CameraCaptureScreen", "Camera setup failed", e)
                cameraReady = false
                cameraError = "Could not initialize the camera. Check permissions or log your meal manually."
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            active.set(false)
            imageAnalysis.clearAnalyzer()
            runCatching { boundCamera?.cameraControl?.enableTorch(false) }
            provider?.unbind(preview, imageCapture, imageAnalysis)
        }
    }

    DisposableEffect(barcodeScanner) {
        onDispose { barcodeScanner.close() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
    ) {
        if (cameraError != null) {
            // Recovery UI when camera hardware or initialization fails — never strands user on a blank screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                TerminalCard(
                    elevated = true,
                    withHudCorners = true,
                    modifier = Modifier.widthIn(max = 420.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = tokens.warning,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "CAMERA UNAVAILABLE",
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.primaryText,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = cameraError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.secondaryText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        TerminalPrimaryButton(
                            text = "LOG MEAL MANUALLY",
                            icon = Icons.Default.EditNote,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onManualEntry()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TerminalSecondaryButton(
                            text = "OPEN APP SETTINGS",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                runCatching { context.startActivity(intent) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.defaultMinSize(minHeight = tokens.minTouchTarget)
                        ) {
                            Text("Back to dashboard", color = tokens.secondaryText)
                        }
                    }
                }
            }
        } else {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .hudFraming(pulsingColor, length = 32.dp, thickness = 3.dp)
            )

            if (crtEffectsEnabled && cameraReady) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    val y = size.height * scanningY
                    drawLine(
                        color = pulsingColor.copy(alpha = 0.65f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }
        }

        // Top Bar with Back and Torch toggle
        TopAppBar(
            modifier = Modifier.statusBarsPadding(),
            title = {
                Column {
                    Text(
                        text = "OPTICAL & BARCODE SCANNER",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Frame meal or align product barcode",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onBack()
                    },
                    modifier = Modifier.size(tokens.minTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                if (hasFlashUnit && cameraError == null) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            val next = !torchEnabled
                            torchEnabled = next
                            runCatching { boundCamera?.cameraControl?.enableTorch(next) }
                        },
                        modifier = Modifier.size(tokens.minTouchTarget)
                    ) {
                        Icon(
                            imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = if (torchEnabled) "Turn flash off" else "Turn flash on",
                            tint = if (torchEnabled) tokens.warning else tokens.primaryText
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = tokens.background.copy(alpha = 0.78f),
                titleContentColor = tokens.primaryText,
                navigationIconContentColor = tokens.primaryText
            )
        )

        if (cameraError == null) {
            // Static, screen-reader-friendly instruction banner + controls at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = tokens.background.copy(alpha = 0.82f),
                    shape = tokens.controlShape,
                    border = BorderStroke(1.dp, tokens.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (takingPhoto) {
                            "Capturing frame…"
                        } else if (!cameraReady) {
                            "Initializing optical sensor…"
                        } else {
                            "Tap shutter to analyze a meal photo, or point at a barcode for automatic lookup."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.primaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                IconButton(
                    enabled = cameraReady && !takingPhoto,
                    onClick = {
                        takingPhoto = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        takePhoto(
                            context = context,
                            imageCapture = imageCapture,
                            executor = ContextCompat.getMainExecutor(context),
                            onImageCaptured = { uri, id ->
                                if (active.get()) {
                                    onImageCaptured(uri, id)
                                } else {
                                    EvidenceStore.discardStaging(context, uri)
                                    EvidenceStore.releaseStaging(uri)
                                }
                            },
                            onCaptureError = { message ->
                                takingPhoto = false
                                scope.launch {
                                    snackbarHostState.showSnackbar("Couldn't capture photo: $message")
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .size(76.dp)
                        .semantics { contentDescription = "Take meal photo" },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = primaryColor,
                        contentColor = onPrimaryColor,
                        disabledContainerColor = tokens.elevatedSurface,
                        disabledContentColor = tokens.disabledText
                    )
                ) {
                    if (takingPhoto) {
                        CircularProgressIndicator(
                            color = onPrimaryColor,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TerminalSecondaryButton(
                    text = "LOG MANUALLY INSTEAD",
                    icon = Icons.Default.EditNote,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onManualEntry()
                    }
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

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onImageCaptured: (Uri, String) -> Unit,
    onCaptureError: (String) -> Unit
) {
    val stagingId = UUID.randomUUID().toString()
    val file = EvidenceStore.newStagingFile(context, stagingId)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraCaptureScreen", "Photo capture failed: ${exception.message}", exception)
                file.delete()
                EvidenceStore.releaseStaging(Uri.fromFile(file))
                onCaptureError(exception.message ?: "Capture failed")
            }

            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onImageCaptured(Uri.fromFile(file), stagingId)
            }
        }
    )
}
