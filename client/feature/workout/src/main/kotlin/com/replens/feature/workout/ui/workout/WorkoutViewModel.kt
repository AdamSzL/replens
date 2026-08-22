package com.replens.feature.workout.ui.workout

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replens.core.audio.Speaker
import com.replens.core.data.WorkoutRepository
import com.replens.core.exercise.SessionState
import com.replens.core.exercise.SetSession
import com.replens.core.exercise.setupCheck
import com.replens.core.exercise.squat.SquatRepConfig
import com.replens.core.exercise.squat.SquatRepCounter
import com.replens.core.exercise.squat.isAtDepth
import com.replens.core.exercise.squat.squatDepthAngle
import com.replens.core.model.AbandonedDescent
import com.replens.core.model.Exercise
import com.replens.core.model.PoseFrame
import com.replens.core.model.Rep
import com.replens.core.model.RepPhase
import com.replens.core.pose.PoseCameraDataSource
import com.replens.core.posemath.PoseSmoother
import com.replens.core.ui.EventChannel
import com.replens.feature.workout.ui.workout.model.cameraProblem
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@HiltViewModel(assistedFactory = WorkoutViewModel.Factory::class)
internal class WorkoutViewModel @AssistedInject constructor(
    @Assisted private val exercise: Exercise,
    private val poseCamera: PoseCameraDataSource,
    private val speaker: Speaker,
    private val repository: WorkoutRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    internal interface Factory {
        fun create(exercise: Exercise): WorkoutViewModel
    }

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

    private val eventChannel = EventChannel<WorkoutEvent>(viewModelScope)
    val events = eventChannel.events

    private val smoother = PoseSmoother()
    // Shared so the counter and the depth grading cannot disagree about the bottom.
    private val repConfig = SquatRepConfig()
    private val repCounter = SquatRepCounter(repConfig)
    private val setSession = SetSession()
    private val cues = CueEngine(repConfig)

    /** Kept for the set summary and the write; the counter only knows the total. */
    private val reps = mutableListOf<Rep>()

    /** Never spoken about, only stored — they are what per-user calibration reads. */
    private val abandoned = mutableListOf<AbandonedDescent>()

    /**
     * Wall clock and frame clock at the same instant, so a set can be timed with
     * the clock its reps were timed with. Non-null exactly while a set is in
     * progress, which is also what stops a second Finish writing a second row.
     */
    private data class SetStart(val at: Instant, val frameMillis: Long)

    private var setStart: SetStart? = null
    private var lastFrameMillis = 0L

    private var cameraJob: Job? = null

    /**
     * Whose lifecycle the camera follows, and null while no screen is showing.
     * A field rather than a parameter captured once, because returning to this
     * screen brings a *new* owner — the previous one was destroyed with its
     * composition, and CameraX refuses to bind to a destroyed lifecycle.
     */
    private val lifecycleOwners = MutableStateFlow<LifecycleOwner?>(null)

    /**
     * The write [finishSet] started, carrying the workout id it produced. Done can
     * be pressed before it lands, so [doneWithSet] waits on this rather than
     * reading an id and hoping. Null after a set that wrote nothing.
     *
     * A [Deferred] rather than a job plus a field: [doneWithSet] captures this
     * before suspending, so a set started while the write is in flight cannot
     * change which id it resolves to.
     */
    private var recordJob: Deferred<Long>? = null

    /** Null, not true: the camera can fail before the first reading arrives on resume. */
    private val isCameraGranted = MutableStateFlow<Boolean?>(null)

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
        viewModelScope.launch {
            combine(poseCamera.availability, isCameraGranted, ::cameraProblem)
                .collect { problem -> state.update { it.copy(cameraProblem = problem) } }
        }
    }

    fun onAction(action: WorkoutAction) {
        when (action) {
            is WorkoutAction.ScreenAttached -> startCamera(action.lifecycleOwner)
            WorkoutAction.ScreenDetached -> releaseCamera()
            is WorkoutAction.ScreenResumed -> onScreenResumed(action.isCameraGranted)
            WorkoutAction.OpenSettingsClicked -> {
                eventChannel.send(WorkoutEvent.OpenAppSettings)
            }
            WorkoutAction.GoBackClicked -> eventChannel.send(WorkoutEvent.NavigateBack)
            is WorkoutAction.ZoomSelected -> selectZoom(action.ratio)
            WorkoutAction.CameraFlipClicked -> flipCamera()
            WorkoutAction.StartClicked, WorkoutAction.GoAgainClicked -> startSet()
            WorkoutAction.CancelClicked -> cancelSet()
            WorkoutAction.FinishClicked -> finishSet()
            WorkoutAction.DoneClicked -> doneWithSet()
        }
    }

    private fun startCamera(lifecycleOwner: LifecycleOwner) {
        lifecycleOwners.value = lifecycleOwner
        if (cameraJob?.isActive == true) return
        cameraJob = viewModelScope.launch {
            // Driven from state, so what the camera does can't drift from what the
            // screen shows.
            poseCamera.poseFrames(
                lifecycleOwners = lifecycleOwners,
                facings = state.mapNotNull { it.cameraFacing },
                zoomRatios = state.map { it.zoomRatio },
            ).collect(::onFrame)
        }
    }

    /**
     * The stream is left collecting on purpose: cancelling it would close the ML
     * Kit detector, and reloading that model is a visible stall on a path taken
     * once per workout.
     */
    private fun releaseCamera() {
        lifecycleOwners.value = null
        // The last pose is now arbitrarily old; leaving it would draw a skeleton
        // from a different room until the first frame after rebinding.
        poseFrame.value = null
    }

    /**
     * Nothing is rebound here: CameraX detaches on stop and re-attaches on start,
     * so a permission granted while we were away has already been retried. Only
     * the name for a failure that survived it is missing.
     */
    private fun onScreenResumed(isGranted: Boolean) {
        isCameraGranted.value = isGranted
    }

    private fun selectZoom(ratio: Float) {
        state.update { it.copy(zoomRatio = ratio) }
    }

    private fun startSet() {
        repCounter.reset()
        cues.reset()
        reps.clear()
        abandoned.clear()
        setStart = null
        // A new set makes the previous write irrelevant. Cleared here rather than
        // in finishSet, which returns early when there is nothing to write and
        // would otherwise leave the last set's workout to be navigated to. The
        // write itself is left to finish — the row is the user's data either way.
        recordJob = null
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
        // Discarding the set *is* what cancel means, so the write is dropped by
        // dropping what a write would need.
        setStart = null
        state.update { it.copy(session = setSession.cancel()) }
    }

    private fun finishSet() {
        val start = setStart
        setStart = null
        state.update { it.copy(session = setSession.finish()) }
        if (start == null) return
        // Nothing happened. Abandoned descents alone are still worth keeping: they
        // are the evidence that the counting threshold is wrong for this body.
        if (reps.isEmpty() && abandoned.isEmpty()) return

        // Read before launching, not inside: the coroutine body does not run until
        // the next dispatch, by which time "Go again" may have cleared both lists.
        val endedAt = start.at + (lastFrameMillis - start.frameMillis).milliseconds
        val recorded = reps.toList()
        val abandonedCount = abandoned.size
        val deepestAbandonedAngle = abandoned.minOfOrNull(AbandonedDescent::deepestAngle)
        val repsAtDepth = state.value.repsAtDepth

        recordJob = viewModelScope.async {
            repository.recordSet(
                exercise = exercise,
                startedAt = start.at,
                endedAt = endedAt,
                repsAtDepth = repsAtDepth,
                abandonedCount = abandonedCount,
                deepestAbandonedAngle = deepestAbandonedAngle,
                reps = recorded,
            )
        }
    }

    /**
     * Leaves the set behind and shows what it landed in. A set that wrote nothing
     * has no workout to show, so Done just clears the card — silence is right
     * there, since navigating to an empty summary would be worse than staying.
     */
    private fun doneWithSet() {
        val job = recordJob
        dismissSummary()
        if (job == null) return
        viewModelScope.launch {
            eventChannel.send(WorkoutEvent.NavigateToSummary(job.await()))
        }
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
        lastFrameMillis = smoothed.timestampMillis

        val session = setSession.onFrame(smoothed.setupCheck(), smoothed.timestampMillis)
        // The count-in and the walk out to your spot are not the set. Guarded on
        // null rather than on the transition, so re-entering Active cannot restart
        // the clock on a set already under way.
        if (session == SessionState.Active && setStart == null) {
            setStart = SetStart(clock.now(), smoothed.timestampMillis)
        }
        val repUpdate = if (session == SessionState.Active) {
            repCounter.update(smoothed.squatDepthAngle(), smoothed.timestampMillis)
        } else {
            null
        }
        repUpdate?.completedRep?.let(reps::add)
        repUpdate?.abandonedDescent?.let(abandoned::add)
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
