package com.replens.core.pose

import android.content.Context
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.asFlow
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.replens.core.model.PoseFrame
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Streams live poses from the camera. Collecting [poseFrames] starts the
 * camera and ML Kit detector; cancelling the collection releases both. The
 * camera preview surface is published via [surfaceRequests] for the UI to
 * render alongside.
 *
 * Deliberately unscoped: the only retained state is [surfaceRequests] and
 * [zoomRange], neither of which must outlive the screen, and
 * `ProcessCameraProvider` is already a process singleton, so there is nothing
 * expensive to cache.
 */
class PoseCameraDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val surfaceRequests: StateFlow<SurfaceRequest?>
        field = MutableStateFlow<SurfaceRequest?>(null)

    /** Null until a camera is bound; only then does the device report its range. */
    val zoomRange: StateFlow<ZoomRange?>
        field = MutableStateFlow<ZoomRange?>(null)

    fun poseFrames(
        lifecycleOwner: LifecycleOwner,
        facings: Flow<CameraFacing>,
        zoomRatios: Flow<Float>,
    ): Flow<PoseFrame> = callbackFlow {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val detector = PoseDetection.getClient(
            AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build()
        )
        // Off the main thread so the pipeline never queues behind Compose: a late
        // imageProxy.close() silently drops frames under KEEP_ONLY_LATEST.
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequests.value = request }
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
            analyzeFrame(imageProxy, detector, analysisExecutor) { frame -> trySend(frame) }
        }
        // A lens change rebinds the same use cases rather than restarting the flow:
        // the detector and its loaded model are none of CameraX's business.
        // collectLatest cancels the previous camera's observers on the next facing.
        // Must run on the main thread — that is what the flowOn below is for.
        // Deduplicated here, not by the caller: rebinding is this function's cost,
        // so what counts as a change is its call. A repeat would tear the camera
        // down for nothing, silently.
        launch {
            facings.distinctUntilChanged().collectLatest { facing ->
                provider.unbind(preview, analysis)
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    facing.selector,
                    preview,
                    analysis,
                )
                coroutineScope {
                    // Observed rather than read once: bindToLifecycle returns before
                    // the camera has opened, so the range can arrive late.
                    launch {
                        camera.cameraInfo.zoomState.asFlow().collect { state ->
                            zoomRange.value = ZoomRange(state.minZoomRatio, state.maxZoomRatio)
                        }
                    }
                    // Re-collected per binding because a rebind resets zoom to 1x.
                    zoomRatios.distinctUntilChanged()
                        .collect { camera.cameraControl.setZoomRatio(it) }
                }
            }
        }
        awaitClose {
            // Not unbindAll(): the provider is a process singleton, so that would
            // also tear down any other camera use case bound elsewhere.
            provider.unbind(preview, analysis)
            detector.close()
            analysisExecutor.shutdown()
            surfaceRequests.value = null
            zoomRange.value = null
        }
    }.flowOn(Dispatchers.Main)

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeFrame(
        imageProxy: ImageProxy,
        detector: PoseDetector,
        callbackExecutor: Executor,
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
            .addOnSuccessListener(callbackExecutor) { pose ->
                onFrame(
                    PoseFrame(
                        pose = pose.toBodyPose(),
                        sourceWidth = width,
                        sourceHeight = height,
                        timestampMillis = timestampMillis,
                    )
                )
            }
            .addOnCompleteListener(callbackExecutor) { imageProxy.close() }
    }
}
