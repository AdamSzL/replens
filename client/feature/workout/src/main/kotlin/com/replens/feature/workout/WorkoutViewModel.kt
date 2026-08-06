package com.replens.feature.workout

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replens.core.exercise.squat.SquatRepCounter
import com.replens.core.exercise.squat.squatSignals
import com.replens.core.model.PoseFrame
import com.replens.core.pose.PoseCameraDataSource
import com.replens.core.posemath.PoseSmoother
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Runs each frame through smoothing -> signal extraction -> rep counting. */
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val poseCamera: PoseCameraDataSource,
) : ViewModel() {

    val surfaceRequest: StateFlow<SurfaceRequest?> = poseCamera.surfaceRequests

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

    /**
     * Takes a [LifecycleOwner] because CameraX binds the camera to UI visibility —
     * the session must stop when the screen does, not when the ViewModel is cleared.
     */
    fun startSession(lifecycleOwner: LifecycleOwner) {
        if (session?.isActive == true) return
        session = viewModelScope.launch {
            poseCamera.poseFrames(lifecycleOwner).collect(::onFrame)
        }
    }

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
