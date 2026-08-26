package com.etrisad.zenith.ui.components.qr

import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalGetImage::class)
@Composable
fun QrScanner(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    permissionGranted: Boolean = true,
    permissionMessage: String? = null,
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestCallback by rememberUpdatedState(onQrDetected)
    val latestActive by rememberUpdatedState(active)

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val lastReportedCode = remember { AtomicReference("") }
    val lastReportedTime = remember { AtomicReference(0L) }
    var bindError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner, permissionGranted) {
        if (permissionGranted) {
            val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            var bound = false

            fun tryBind() {
                if (bound) return
                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
                if (!cameraProviderFuture.isDone) return
                bound = true
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(android.util.Size(640, 480))
                        .build()
                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        try {
                            if (!latestActive) {
                                return@setAnalyzer
                            }
                            val text = tryDecode(imageProxy)
                            if (text != null) {
                                val now = System.currentTimeMillis()
                                if (text != lastReportedCode.get() || now - lastReportedTime.get() > 2500) {
                                    lastReportedCode.set(text)
                                    lastReportedTime.set(now)
                                    latestCallback(text)
                                }
                            }
                        } finally {
                            imageProxy.close()
                        }
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    bindError = null
                } catch (e: Exception) {
                    bound = false
                    bindError = e.message ?: e.javaClass.simpleName
                }
            }

            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> tryBind()
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                        if (bound) {
                            try {
                                cameraProviderFuture.get().unbindAll()
                            } catch (_: Exception) {
                            }
                            bound = false
                        }
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            cameraProviderFuture.addListener({ tryBind() }, ContextCompat.getMainExecutor(context))

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                try {
                    cameraProviderFuture.get().unbindAll()
                } catch (_: Exception) {
                }
                analysisExecutor.shutdown()
            }
        } else {
            onDispose {}
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (permissionGranted) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            if (bindError != null) {
                Text(
                    text = "Camera error: $bindError",
                    color = MaterialTheme.colorScheme.error,
                    style = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.error)
                )
            }
        } else {
            Text(
                text = permissionMessage ?: "Camera permission is required to scan QR codes",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun tryDecode(imageProxy: ImageProxy): String? {
    val image = imageProxy.image ?: return null
    val plane = image.planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val width = imageProxy.width
    val height = imageProxy.height

    val yData = ByteArray(buffer.remaining())
    buffer.get(yData)

    val rotation = imageProxy.imageInfo.rotationDegrees

    val candidates = buildList {
        add(YuvCandidate(yData, rowStride, height, 0, 0, width, height))
        if (rotation == 90 || rotation == 270) {
            val rotated = rotate90(yData, rowStride, height, width)
            add(YuvCandidate(rotated, height, width, 0, 0, height, width))
        }
        if (rotation == 180) {
            val rotated = rotate180(yData, rowStride, height, width)
            add(YuvCandidate(rotated, width, height, 0, 0, width, height))
        }
    }

    val hints = mapOf<DecodeHintType, Any>(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true
    )

    for (candidate in candidates) {
        val source = PlanarYUVLuminanceSource(
            candidate.data,
            candidate.dataWidth,
            candidate.dataHeight,
            candidate.left,
            candidate.top,
            candidate.width,
            candidate.height,
            false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        try {
            val result = QRCodeReader().decode(binaryBitmap, hints)
            return result.text
        } catch (_: Exception) {
        }
    }
    return null
}

private class YuvCandidate(
    val data: ByteArray,
    val dataWidth: Int,
    val dataHeight: Int,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

private fun rotate90(data: ByteArray, rowStride: Int, height: Int, width: Int): ByteArray {
    val newWidth = height
    val newHeight = width
    val rotated = ByteArray(newWidth * newHeight)
    for (y in 0 until height) {
        for (x in 0 until width) {
            rotated[(height - 1 - y) + newWidth * x] = data[y * rowStride + x]
        }
    }
    return rotated
}

private fun rotate180(data: ByteArray, rowStride: Int, height: Int, width: Int): ByteArray {
    val rotated = ByteArray(height * width)
    for (y in 0 until height) {
        for (x in 0 until width) {
            rotated[(height - 1 - y) * width + (width - 1 - x)] = data[y * rowStride + x]
        }
    }
    return rotated
}