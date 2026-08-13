package com.replens.feature.workout.ui

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replens.core.audio.Speaker
import com.replens.core.exercise.Framing
import com.replens.core.exercise.Rep
import com.replens.core.exercise.RepPhase
import com.replens.core.exercise.RepUpdate
import com.replens.core.exercise.SessionState
import com.replens.core.exercise.SetSession
import com.replens.core.exercise.framing
import com.replens.core.exercise.setupCheck
import com.replens.core.exercise.squat.SquatRepConfig
import com.replens.core.exercise.squat.SquatRepCounter
import com.replens.core.exercise.squat.isAtDepth
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
    private val speaker: Speaker,
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
        field = MutableStateFlow(null)

    private val smoother = PoseSmoother()
    // Held rather than defaulted twice, so the counter and the depth grading can
    // never disagree about where the bottom is.
    private val repConfig = SquatRepConfig()
    private val repCounter = SquatRepCounter(repConfig)
    private val setSession = SetSession()
    private val cues = CueEngine(repConfig)

    /** Kept for the set summary; the counter itself only ever knows the total. */
    private val reps = mutableListOf<Rep>()

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

        // The camera runs in every session state — you have to see yourself to get
        // into position — but only a started set counts.
        val session = setSession.onFrame(smoothed.setupCheck(), smoothed.timestampMillis)
        val repUpdate = if (session == SessionState.Active) countRep(smoothed) else null
        val phase = repUpdate?.phase ?: state.value.phase

        // Read first to skip the copy on the frames that change nothing, but write
        // through `update`: the buttons write this too.
        val current = state.value
        if (current.session != session ||
            current.repCount != repCounter.repCount ||
            current.phase != phase
        ) {
            state.update {
                it.copy(
                    session = session,
                    repCount = repCounter.repCount,
                    // Not in the guard above, and it does not need to be: `reps`
                    // only grows on a frame that also moves `repCount`.
                    repsAtDepth = reps.count { rep -> rep.isAtDepth(repConfig) },
                    phase = phase,
                )
            }
        }

        // After the state is written, so the summary speaks the numbers the card is
        // showing rather than the previous frame's. Every frame, not only the ones
        // that changed something: a setup instruction has to be repeated on a timer,
        // and the frame stream is that timer.
        cues.onFrame(
            session = session,
            repUpdate = repUpdate,
            repCount = repCounter.repCount,
            repsAtDepth = state.value.repsAtDepth,
            timestampMillis = smoothed.timestampMillis,
        )?.let(speaker::speak)
    }

    /**
     * A frame that fails the framing check reads as "no angle", not as a bad one —
     * which is the state the counter already knows how to abandon a rep from. The
     * overlay still draws it, so the user can see why nothing counts.
     */
    private fun countRep(frame: PoseFrame): RepUpdate {
        val depthAngle = frame.pose
            .squatSignals(frame.timestampMillis)
            .depthAngle
            .takeIf { frame.framing() == Framing.OK }
        val update = repCounter.update(depthAngle, frame.timestampMillis)
        update.completedRep?.let(reps::add)
        return update
    }
}
