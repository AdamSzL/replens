package com.replens.core.pose

import android.content.Context
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Pinned, even though CameraX's default happens to match it today: fixtures and
 * every threshold tuned against them assume this exact frame, so a future default
 * must not move it silently. 4:3 because the preview is 4:3 and the overlay maps
 * analysis coordinates onto it — a different aspect would frame a different scene
 * and misplace the skeleton.
 */
private val AnalysisResolution = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(
        ResolutionStrategy(
            Size(640, 480),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
        )
    )
    .build()

/**
 * CameraX and ML Kit behind [PoseCameraDataSource] — the one place either library
 * is named.
 *
 * Deliberately unscoped: the only retained state is [surfaceRequests] and
 * [options], neither of which must outlive the screen, and
 * `ProcessCameraProvider` is already a process singleton, so there is nothing
 * expensive to cache.
 */
internal class CameraXPoseCameraDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : PoseCameraDataSource {

    override val surfaceRequests: StateFlow<SurfaceRequest?>
        field = MutableStateFlow(null)

    override val options: StateFlow<CameraOptions?>
        field = MutableStateFlow(null)

    override val availability: StateFlow<CameraAvailability>
        field = MutableStateFlow(CameraAvailability.Ready)

    override fun poseFrames(
        lifecycleOwners: Flow<LifecycleOwner?>,
        facings: Flow<CameraFacing>,
        zoomRatios: Flow<Float>,
    ): Flow<PoseFrame> = callbackFlow {
        val provider = ProcessCameraProvider.awaitInstance(context)
        // Published before anything is bound, so the caller can resolve a facing
        // that exists. Nothing below waits on it, so this is a handshake, not a
        // deadlock: we answer "what is here?" before asking "which one?".
        val availableFacings = provider.availableCameraInfos
            .mapNotNullTo(mutableSetOf()) { it.lensFacing.toCameraFacing() }
        options.value = CameraOptions(availableFacings)

        val detector = PoseDetection.getClient(
            AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build()
        )
        // Off the main thread so the pipeline never queues behind Compose: a late
        // imageProxy.close() silently drops frames under KEEP_ONLY_LATEST.
        //
        // Never shut down, which is why it is built by hand rather than with
        // Executors.newSingleThreadExecutor(). ML Kit is handed this executor for
        // its listeners, and delivers the cancellation from detector.close() on
        // its own schedule — so a terminated pool rejects that submission, and the
        // rejection is thrown on whichever thread posted it, which is main. Core
        // threads time out instead: the worker exits once idle, so the pool is
        // collected like any other object and nothing is parked for the app's life.
        val analysisExecutor = ThreadPoolExecutor(
            1,
            1,
            5L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(),
            ThreadFactory { runnable -> Thread(runnable, "replens-pose-analysis") },
        ).apply { allowCoreThreadTimeOut(true) }
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequests.value = request }
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(AnalysisResolution)
            .build()
        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
            analyzeFrame(imageProxy, detector, analysisExecutor) { frame -> trySend(frame) }
        }
        // What the camera should be bound to. One target rather than two inputs
        // because a rebind is a rebind either way: a returning screen brings a
        // different LifecycleOwner, and the one it replaced is destroyed, which
        // bindToLifecycle rejects outright.
        val binding = combine(lifecycleOwners, facings) { owner, facing ->
            owner?.let { it to facing }
        }
        launch {
            // Deduplicated here, not by the caller: rebinding is this function's
            // cost, so what counts as a change is its call. A repeat would tear the
            // camera down for nothing, silently. The same use cases are rebound
            // rather than the flow restarting — the detector and its loaded model
            // are none of CameraX's business — and all of this must run on the main
            // thread, which is what the flowOn below is for.
            binding.distinctUntilChanged().collectLatest { target ->
                // Back to "unknown", not the previous lens's range: the new one has
                // its own, and offering a stop it can't reach fails silently.
                options.value = CameraOptions(availableFacings)
                provider.unbind(preview, analysis)
                if (target == null) {
                    // Cleared only when the *consumer* is gone. A viewfinder that
                    // is still on screen keeps showing its last frame across a
                    // rebind, so clearing here too blanks the preview for the
                    // ~300 ms a lens change takes — measured, not guessed.
                    // A returning screen brings a new viewfinder instead, and
                    // handing that one a request the last one declined renders
                    // black permanently.
                    surfaceRequests.value = null
                    // Nothing is bound, so nothing is wrong. Left as it was, a
                    // camera that failed would still read as failed on the way
                    // back, before anything had tried to open.
                    availability.value = CameraAvailability.Ready
                    return@collectLatest
                }
                val (owner, facing) = target
                val camera = provider.bindToLifecycle(
                    owner,
                    facing.selector,
                    preview,
                    analysis,
                )
                coroutineScope {
                    // Observed rather than read once: bindToLifecycle returns before
                    // the camera has opened, so the range can arrive late.
                    launch {
                        camera.cameraInfo.zoomState.asFlow().collect { state ->
                            options.value = CameraOptions(
                                facings = availableFacings,
                                zoomRange = ZoomRange(state.minZoomRatio, state.maxZoomRatio),
                            )
                        }
                    }
                    // bindToLifecycle succeeds whether or not the device opens, and
                    // a failure to open is never thrown — this is the only report.
                    launch {
                        camera.cameraInfo.cameraState.asFlow().collect { state ->
                            availability.value = state.toAvailability()
                        }
                    }
                    // Re-collected per binding because a rebind resets zoom to 1x.
                    zoomRatios.distinctUntilChanged()
                        .collect { camera.cameraControl.setZoomRatio(it) }
                }
            }
        }
        awaitClose {
            // The documented way to stop analysis; unbinding alone leaves the
            // analyzer registered. It does not settle a detector.process() task
            // that is already in flight, which is why the executor outlives this.
            analysis.clearAnalyzer()
            // Not unbindAll(): the provider is a process singleton, so that would
            // also tear down any other camera use case bound elsewhere.
            provider.unbind(preview, analysis)
            detector.close()
            surfaceRequests.value = null
            options.value = null
            availability.value = CameraAvailability.Ready
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
