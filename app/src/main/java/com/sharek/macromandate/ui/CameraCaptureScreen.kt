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
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.Executor
import androidx.compose.ui.res.stringResource
import com.sharek.macromandate.R

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
    // Resolved in composition: the click handler is not a composable, and the
    // failure text is fixed anyway — the CameraX exception detail goes to the log,
    // not to the user.
    val captureFailedMessage = stringResource(R.string.camera_capture_failed)

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

    LaunchedEffect(Unit) {
        // Initial tactical readiness pulse on viewfinder activation
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // DisposableEffect, not LaunchedEffect: use cases are bound to the *activity*
    // lifecycle, so without an explicit unbind on the way out the camera stayed
    // open — and the indicator lit — for as long as the app was foregrounded
    // after a single visit to this screen.
    DisposableEffect(lifecycleOwner) {
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

        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
                .onFailure { Log.w("CameraCaptureScreen", "Could not release the camera", it) }
        }
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
            title = { Text(stringResource(R.string.camera_title), fontWeight = FontWeight.Black) },
            navigationIcon = {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onBack()
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back)
                    )
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
            // Static. This was a marquee that rotated the string by one
            // character every 100ms: unreadable, permanently recomposing, and a
            // screen reader would re-announce it ten times a second.
            Text(
                text = stringResource(R.string.camera_instruction),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)
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
                    onCaptureError = {
                        scope.launch { snackbarHostState.showSnackbar(captureFailedMessage) }
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
                contentDescription = stringResource(R.string.content_description_shutter),
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
    onCaptureError: () -> Unit
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
                // The detail is a CameraX internal message; it belongs in logcat.
                // Uppercasing it and putting it in a snackbar told the user
                // nothing they could act on.
                Log.w("CameraCaptureScreen", "Photo capture failed", exception)
                file.delete()
                onCaptureError()
            }

            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onImageCaptured(Uri.fromFile(file))
            }
        }
    )
}
