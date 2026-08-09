package com.replens.feature.workout.ui

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replens.core.exercise.Framing
import com.replens.core.exercise.RepPhase
import com.replens.core.exercise.SetupCheck
import com.replens.core.exercise.framing
import com.replens.core.exercise.setupCheck
import com.replens.core.exercise.squat.SquatRepCounter
import com.replens.core.exercise.squat.squatSignals
import com.replens.core.model.PoseFrame
import com.replens.core.pose.CameraFacing
import com.replens.core.pose.PoseCameraDataSource
import com.replens.core.posemath.PoseSmoother
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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

    /** The reason [SessionState.Waiting] shows, kept current by the frame stream. */
    private var setup = SetupCheck.NO_POSE

    /**
     * Asymmetric on purpose: one bad frame drops it, but regaining it takes
     * [READY_FRAMES] in a row. Slow to commit, quick to refuse — the cost of
     * starting a set on a body whose legs are invented is a whole set measured
     * off nothing, while refusing costs a message.
     */
    private val steadilyReady = MutableStateFlow(false)
    private var readyStreak = 0

    private var session: Job? = null
    private var countIn: Job? = null

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

    private fun cancelSet() {
        countIn?.cancel()
        steadilyReady.value = false
        state.update { it.copy(session = SessionState.Idle) }
    }

    private fun finishSet() {
        state.update { it.copy(session = SessionState.Finished) }
    }

    private fun dismissSummary() {
        state.update { it.copy(session = SessionState.Idle, repCount = 0) }
    }

    /**
     * Waits for the camera to actually see the user before counting in, so the
     * walk out to the spot never eats the countdown — and stepping back out of
     * frame mid-count returns to waiting rather than starting without you.
     */
    private fun startSet() {
        countIn?.cancel()
        repCounter.reset()
        readyStreak = 0
        steadilyReady.value = false
        state.update {
            it.copy(session = waiting(), repCount = 0, phase = RepPhase.STANDING)
        }
        countIn = viewModelScope.launch {
            while (true) {
                steadilyReady.first { it }
                if (countInWhileReady()) break
                state.update { it.copy(session = waiting()) }
            }
            state.update { it.copy(session = SessionState.Active) }
        }
    }

    /**
     * False if the user left position partway through, which restarts the wait.
     *
     * Equal [SessionState.CountingIn] values conflate, so polling ten times a
     * second costs no emissions.
     */
    private suspend fun countInWhileReady(): Boolean {
        var remaining = COUNT_IN
        while (remaining > Duration.ZERO) {
            state.update {
                it.copy(session = SessionState.CountingIn(ceil(remaining / 1.seconds).toInt()))
            }
            delay(COUNT_IN_POLL)
            if (!steadilyReady.value) return false
            remaining -= COUNT_IN_POLL
        }
        return true
    }

    private fun waiting() = SessionState.Waiting(setup)

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

    private fun updateReadiness(frame: PoseFrame) {
        val check = frame.setupCheck()
        setup = check
        readyStreak = if (check == SetupCheck.READY) readyStreak + 1 else 0
        steadilyReady.value = readyStreak >= READY_FRAMES
        // Keep the on-screen reason current while waiting, but never overwrite a
        // later session state from the frame stream.
        state.update {
            if (it.session is SessionState.Waiting) it.copy(session = SessionState.Waiting(check))
            else it
        }
    }

    private fun onFrame(frame: PoseFrame) {
        // Publishing the smoothed pose keeps the overlay showing what the rep
        // counter actually decided on.
        val smoothed = smoother.smooth(frame)
        poseFrame.value = smoothed
        updateReadiness(smoothed)

        val current = state.value
        // The camera runs in every session state — you have to see yourself to get
        // into position — but only a started set counts.
        if (current.session != SessionState.Active) return

        // A frame that fails the framing check reads as "no angle", not as a bad
        // one — which is the state the counter already knows how to abandon a rep
        // from. The overlay still draws it, so the user can see why nothing counts.
        val depthAngle = smoothed.pose
            .squatSignals(smoothed.timestampMillis)
            .depthAngle
            .takeIf { smoothed.framing() == Framing.OK }
        val phase = repCounter.update(depthAngle, smoothed.timestampMillis).phase

        // Read first to skip the copy on the ~29 frames in 30 that change nothing,
        // but write through `update`: the count-in job and the buttons now write
        // this too, so a stale `current` would put a finished set back to Active.
        if (current.repCount != repCounter.repCount || current.phase != phase) {
            state.update { it.copy(repCount = repCounter.repCount, phase = phase) }
        }
    }
}

private val COUNT_IN = 3.seconds

/**
 * Far finer than the count-in ticks: checking only when the number changes reads
 * one instant per second and lets the other ~29 frames say nothing, so a set
 * could start off a single lucky frame.
 */
private val COUNT_IN_POLL = 100.milliseconds

/** ~0.5 s at the ~27 fps this pipeline sustains. */
private const val READY_FRAMES = 14
