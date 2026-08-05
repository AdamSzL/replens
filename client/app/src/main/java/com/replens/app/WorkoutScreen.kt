package com.replens.app

import androidx.annotation.OptIn
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.awaitCancellation

/** One analyzed frame: the detected pose plus the image size its landmarks are expressed in. */
data class PoseFrame(val pose: Pose, val imageWidth: Int, val imageHeight: Int)

@Composable
fun WorkoutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var poseFrame by remember { mutableStateOf<PoseFrame?>(null) }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val detector = PoseDetection.getClient(
            AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build()
        )
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            analyzeFrame(imageProxy, detector) { poseFrame = it }
        }
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            preview,
            analysis,
        )
        try {
            awaitCancellation()
        } finally {
            provider.unbindAll()
            detector.close()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize(),
            )
        }
        poseFrame?.let { frame ->
            // Front camera: the viewfinder mirrors the preview, analysis frames are not
            // mirrored, so the overlay must mirror to line up.
            PoseOverlay(
                frame = frame,
                mirrored = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun analyzeFrame(
    imageProxy: ImageProxy,
    detector: PoseDetector,
    onPose: (PoseFrame) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val rotation = imageProxy.imageInfo.rotationDegrees
    // Landmarks come back in the rotated (upright) image's coordinate space.
    val upright = rotation % 180 == 0
    val width = if (upright) imageProxy.width else imageProxy.height
    val height = if (upright) imageProxy.height else imageProxy.width
    detector.process(InputImage.fromMediaImage(mediaImage, rotation))
        .addOnSuccessListener { pose -> onPose(PoseFrame(pose, width, height)) }
        .addOnCompleteListener { imageProxy.close() }
}
