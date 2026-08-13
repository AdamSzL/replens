package com.replens.feature.workout.ui

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replens.core.audio.Speaker
import com.replens.core.exercise.Rep
import com.replens.core.exercise.RepPhase
import com.replens.core.exercise.SessionState
import com.replens.core.exercise.SetSession
import com.replens.core.exercise.setupCheck
import com.replens.core.exercise.squat.SquatRepConfig
import com.replens.core.exercise.squat.SquatRepCounter
import com.replens.core.exercise.squat.isAtDepth
import com.replens.core.exercise.squat.squatDepthAngle
import com.replens.core.model.PoseFrame
import com.replens.core.pose.PoseCameraDataSource
import com.replens.core.posemath.PoseSmoother
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class WorkoutViewModel @Inject constructor(
    private val poseCamera: PoseCameraDataSource,
    private val speaker: Speaker,
) : ViewModel() {

    val surfaceRequests: StateFlow<SurfaceRequest?> = poseCamera.surfaceRequests

    val state: StateFlow<WorkoutState>
        field = MutableStateFlow(WorkoutState())

    /**
     * The 30 fps geometry stream, **deliberately separate from [state]**: Compose
     * subscribes per `State` object, so sharing one would invalidate everything
     * reading the rep count at frame rate.
     */
    val poseFrame: StateFlow<PoseFrame?>
        field = MutableStateFlow(null)

    private val smoother = PoseSmoother()
    // Shared so the counter and the depth grading cannot disagree about the bottom.
    private val repConfig = SquatRepConfig()
    private val repCounter = SquatRepCounter(repConfig)
    private val setSession = SetSession()
    private val cues = CueEngine(repConfig)

    /** Kept for the set summary; the counter itself only ever knows the total. */
    private val reps = mutableListOf<Rep>()

    private var cameraJob: Job? = null

    init {
        viewModelScope.launch {
            poseCamera.options.collect { options ->
                state.update {
                    it.copy(
                        cameraOptions = options,
                        // Resolved once — a later options update must not undo a flip.
                        cameraFacing = it.cameraFacing ?: options?.facings?.preferred,
                    )
                }
            }
        }
    }

    fun startCamera(lifecycleOwner: LifecycleOwner) {
        if (cameraJob?.isActive == true) return
        cameraJob = viewModelScope.launch {
            // Driven from state, so what the camera does can't drift from what the
            // screen shows.
            poseCamera.poseFrames(
                lifecycleOwner = lifecycleOwner,
                facings = state.mapNotNull { it.cameraFacing },
                zoomRatios = state.map { it.zoomRatio },
            ).collect(::onFrame)
        }
    }

    fun onAction(action: WorkoutAction) {
        when (action) {
            is WorkoutAction.ZoomSelected -> selectZoom(action.ratio)
            WorkoutAction.CameraFlipClicked -> flipCamera()
            WorkoutAction.StartClicked, WorkoutAction.GoAgainClicked -> startSet()
            WorkoutAction.CancelClicked -> cancelSet()
            WorkoutAction.FinishClicked -> finishSet()
            WorkoutAction.DoneClicked -> dismissSummary()
        }
    }

    private fun selectZoom(ratio: Float) {
        state.update { it.copy(zoomRatio = ratio) }
    }

    private fun startSet() {
        repCounter.reset()
        cues.reset()
        reps.clear()
        state.update {
            it.copy(
                session = setSession.start(),
                repCount = 0,
                repsAtDepth = 0,
                phase = RepPhase.STANDING,
            )
        }
    }

    private fun cancelSet() {
        state.update { it.copy(session = setSession.cancel()) }
    }

    private fun finishSet() {
        state.update { it.copy(session = setSession.finish()) }
    }

    private fun dismissSummary() {
        state.update {
            it.copy(
                session = setSession.dismiss(),
                repCount = 0,
                repsAtDepth = 0,
            )
        }
    }

    private fun flipCamera() {
        val current = state.value
        // The button is hidden without a second lens; guarded anyway, because
        // binding one the device lacks throws rather than no-ops.
        val next = current.cameraFacing?.opposite ?: return
        if (next !in current.cameraOptions?.facings.orEmpty()) return
        // The new lens is mirrored and differently framed, so smoothing across the
        // flip would drag the skeleton. The count deliberately survives.
        smoother.reset()
        // The new lens reports its own range, so a ratio from the old one is meaningless.
        state.update { it.copy(cameraFacing = next, zoomRatio = 1f) }
    }

    private fun onFrame(frame: PoseFrame) {
        val smoothed = smoother.smooth(frame)
        poseFrame.value = smoothed

        val session = setSession.onFrame(smoothed.setupCheck(), smoothed.timestampMillis)
        val repUpdate = if (session == SessionState.Active) {
            repCounter.update(smoothed.squatDepthAngle(), smoothed.timestampMillis)
        } else {
            null
        }
        repUpdate?.completedRep?.let(reps::add)
        val phase = repUpdate?.phase ?: state.value.phase
        // Derived rather than incremented, so the list is the only thing that can
        // be wrong.
        val repsAtDepth = reps.count { it.isAtDepth(repConfig) }

        state.update {
            it.copy(
                session = session,
                repCount = repCounter.repCount,
                repsAtDepth = repsAtDepth,
                phase = phase,
            )
        }

        cues.onFrame(
            session = session,
            repUpdate = repUpdate,
            repCount = repCounter.repCount,
            repsAtDepth = repsAtDepth,
            timestampMillis = smoothed.timestampMillis,
        )?.let(speaker::speak)
    }

}
