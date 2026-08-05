package com.replens.core.pose

import android.content.Context
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.replens.core.model.PoseFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Streams live poses from the front camera. Collecting [poseFrames] starts the
 * camera and ML Kit detector; cancelling the collection releases both. The
 * camera preview surface is published via [surfaceRequests] for the UI to
 * render alongside.
 */
class PoseCameraDataSource(private val context: Context) {

    private val _surfaceRequests = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequests: StateFlow<SurfaceRequest?> = _surfaceRequests.asStateFlow()

    fun poseFrames(lifecycleOwner: LifecycleOwner): Flow<PoseFrame> = callbackFlow {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val detector = PoseDetection.getClient(
            AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build()
        )
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request -> _surfaceRequests.value = request }
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            analyzeFrame(imageProxy, detector) { frame -> trySend(frame) }
        }
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            preview,
            analysis,
        )
        awaitClose {
            provider.unbindAll()
            detector.close()
        }
    }.flowOn(Dispatchers.Main)

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeFrame(
        imageProxy: ImageProxy,
        detector: PoseDetector,
        onFrame: (PoseFrame) -> Unit,
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
        val timestampMillis = imageProxy.imageInfo.timestamp / 1_000_000
        detector.process(InputImage.fromMediaImage(mediaImage, rotation))
            .addOnSuccessListener { pose ->
                onFrame(
                    PoseFrame(
                        pose = pose.toBodyPose(),
                        sourceWidth = width,
                        sourceHeight = height,
                        timestampMillis = timestampMillis,
                    )
                )
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
