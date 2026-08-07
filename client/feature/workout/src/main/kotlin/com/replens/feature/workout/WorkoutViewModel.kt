package com.replens.feature.workout

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replens.core.exercise.squat.SquatRepCounter
import com.replens.core.exercise.squat.squatSignals
import com.replens.core.model.PoseFrame
import com.replens.core.pose.CameraFacing
import com.replens.core.pose.PoseCameraDataSource
import com.replens.core.posemath.PoseSmoother
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Runs each frame through smoothing -> signal extraction -> rep counting. */
@HiltViewModel
internal class WorkoutViewModel @Inject constructor(
    private val poseCamera: PoseCameraDataSource,
) : ViewModel() {

    val surfaceRequests: StateFlow<SurfaceRequest?> = poseCamera.surfaceRequests

    /** Low-frequency screen state: changes about once per rep. */
    val state: StateFlow<WorkoutState>
        field = MutableStateFlow(WorkoutState())

    /**
     * The 30 fps geometry stream, **deliberately separate from [state]**: Compose
     * subscribes per `State` object, so sharing one would invalidate everything
     * reading the rep count at frame rate.
     */
    val poseFrame: StateFlow<PoseFrame?>
        field = MutableStateFlow<PoseFrame?>(null)

    private val smoother = PoseSmoother()
    private val repCounter = SquatRepCounter()

    private var session: Job? = null

    init {
        viewModelScope.launch {
            poseCamera.options.collect { options ->
                state.update {
                    it.copy(
                        cameraOptions = options,
                        // Resolved once — a later options update must not undo a flip.
                        cameraFacing = it.cameraFacing ?: options?.facings?.preferred(),
                    )
                }
            }
        }
    }

    /**
     * Takes a [LifecycleOwner] because CameraX binds the camera to UI visibility —
     * the session must stop when the screen does, not when the ViewModel is cleared.
     */
    fun startSession(lifecycleOwner: LifecycleOwner) {
        if (session?.isActive == true) return
        session = viewModelScope.launch {
            // Driven from state, not a second source of truth, so what the camera
            // does can't drift from what the screen shows.
            poseCamera.poseFrames(
                lifecycleOwner = lifecycleOwner,
                facings = state.map { it.cameraFacing }.filterNotNull(),
                zoomRatios = state.map { it.zoomRatio },
            ).collect(::onFrame)
        }
    }

    fun onAction(action: WorkoutAction) {
        when (action) {
            is WorkoutAction.ZoomSelected ->
                state.update { it.copy(zoomRatio = action.ratio) }

            WorkoutAction.CameraFlipClicked -> flipCamera()
        }
    }

    private fun flipCamera() {
        val current = state.value
        // Guarded rather than left to the hidden button: binding a lens the device
        // doesn't have throws.
        val next = current.cameraFacing?.opposite ?: return
        if (next !in current.cameraOptions?.facings.orEmpty()) return
        // Landmarks from the new lens are mirrored and differently framed; smoothing
        // them against the old ones would drag the skeleton across the screen. The
        // rep count deliberately survives.
        smoother.reset()
        // The new lens reports its own range, so a ratio from the old one is meaningless.
        state.update { it.copy(cameraFacing = next, zoomRatio = 1f) }
    }

    /** Front first: the only lens where you can see your own framing. */
    private fun Set<CameraFacing>.preferred(): CameraFacing? =
        if (CameraFacing.FRONT in this) CameraFacing.FRONT else firstOrNull()

    private fun onFrame(frame: PoseFrame) {
        // Publishing the smoothed pose keeps the overlay showing what the rep
        // counter actually decided on.
        val smoothed = smoother.smooth(frame)
        poseFrame.value = smoothed

        val signals = smoothed.pose.squatSignals(smoothed.timestampMillis)
        val phase = repCounter.update(signals.depthAngle, smoothed.timestampMillis).phase

        val current = state.value
        if (current.repCount != repCounter.repCount || current.phase != phase) {
            state.value = current.copy(repCount = repCounter.repCount, phase = phase)
        }
    }
}
