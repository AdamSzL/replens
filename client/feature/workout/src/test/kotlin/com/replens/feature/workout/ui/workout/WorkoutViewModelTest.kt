package com.replens.feature.workout.ui.workout

import com.replens.core.exercise.SessionState
import com.replens.core.model.Exercise
import com.replens.core.testing.FakeClock
import com.replens.core.testing.FakeWorkoutRepository
import com.replens.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val FRAME_MILLIS = 33L

/** Long enough to cover SetSession's settle plus its three-second count-in. */
private const val FRAMES_TO_ACTIVE = 120

class WorkoutViewModelTest {

    @get:Rule
    internal val mainDispatcherRule = MainDispatcherRule()

    private val camera = FakePoseCameraDataSource()
    private val speaker = FakeSpeaker()
    private val repository = FakeWorkoutRepository()
    private val startedTraining = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    private val clock = FakeClock(startedTraining)

    /**
     * Lazy because JUnit builds the test instance *before* applying rules, and the
     * ViewModel's init block touches `viewModelScope` — which is created on first
     * access, binding whatever `Dispatchers.Main` is at that moment. Built as a
     * field, it silently gets a scope that never runs anything.
     */
    private val viewModel by lazy {
        WorkoutViewModel(
            exercise = Exercise.SQUAT,
            poseCamera = camera,
            speaker = speaker,
            repository = repository,
            clock = clock,
        )
    }

    private var frameMillis = 0L

    private suspend fun hold(kneeAngle: Float, frames: Int) {
        repeat(frames) {
            frameMillis += FRAME_MILLIS
            camera.frames.emit(squatFrame(kneeAngle, frameMillis))
        }
    }

    private suspend fun sweep(from: Float, to: Float, frames: Int) {
        repeat(frames) {
            frameMillis += FRAME_MILLIS
            val progress = (it + 1).toFloat() / frames
            camera.frames.emit(squatFrame(from + (to - from) * progress, frameMillis))
        }
    }

    /** Stand still until the setup check settles and the count-in runs out. */
    private suspend fun standUntilCountedIn() = hold(kneeAngle = 180f, frames = FRAMES_TO_ACTIVE)

    /** Deep enough to clear `goodDepthAngle` even after One Euro lags the bottom. */
    private suspend fun rep() {
        sweep(from = 180f, to = 80f, frames = 15)
        hold(kneeAngle = 80f, frames = 10)
        sweep(from = 80f, to = 180f, frames = 15)
        hold(kneeAngle = 180f, frames = 10)
    }

    /** Past `bottomEnter` so it counts, but short of `goodDepthAngle` so it is not deep. */
    private suspend fun shallowRep() {
        sweep(from = 180f, to = 108f, frames = 15)
        hold(kneeAngle = 108f, frames = 10)
        sweep(from = 108f, to = 180f, frames = 15)
        hold(kneeAngle = 180f, frames = 10)
    }

    /** Past `standingExit` but never past `bottomEnter`, so nothing counts. */
    private suspend fun abandonedDescent() {
        sweep(from = 180f, to = 135f, frames = 15)
        hold(kneeAngle = 135f, frames = 10)
        sweep(from = 135f, to = 180f, frames = 15)
        hold(kneeAngle = 180f, frames = 10)
    }

    private fun start() {
        viewModel.onAction(WorkoutAction.ScreenAttached(fakeScreenLifecycleOwner()))
        viewModel.onAction(WorkoutAction.StartClicked)
    }

    /**
     * Fills as events arrive, so a test can assert that none did. Unconfined
     * explicitly: `backgroundScope` inherits runTest's *standard* dispatcher, so a
     * plain launch would leave the events buffered and unread until the scheduler
     * next ran — and every assertion here is about the moment an event appears.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.collectEvents(): List<WorkoutEvent> {
        val events = mutableListOf<WorkoutEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }
        return events
    }

    @Test
    fun `Done navigates to the workout the set landed in`() = runTest {
        val events = collectEvents()
        start()
        standUntilCountedIn()
        rep()
        viewModel.onAction(WorkoutAction.FinishClicked)

        viewModel.onAction(WorkoutAction.DoneClicked)

        val expected = WorkoutEvent.NavigateToSummary(FakeWorkoutRepository.WORKOUT_ID)
        assertEquals(listOf(expected), events)
    }

    /**
     * The write is what produces the id, and it is slower than a thumb. Without
     * waiting on it, Done reads null and silently goes nowhere.
     */
    @Test
    fun `Done pressed before the write lands still navigates, once it has`() = runTest {
        val events = collectEvents()
        repository.writeInFlight = CompletableDeferred()
        start()
        standUntilCountedIn()
        rep()
        viewModel.onAction(WorkoutAction.FinishClicked)

        viewModel.onAction(WorkoutAction.DoneClicked)
        assertTrue("navigated before the workout existed", events.isEmpty())

        repository.writeInFlight?.complete(Unit)

        val expected = WorkoutEvent.NavigateToSummary(FakeWorkoutRepository.WORKOUT_ID)
        assertEquals(listOf(expected), events)
    }

    /** No reps and no abandoned descents means no workout to show. */
    @Test
    fun `Done after a set that wrote nothing stays on the camera`() = runTest {
        val events = collectEvents()
        start()
        standUntilCountedIn()
        viewModel.onAction(WorkoutAction.FinishClicked)

        viewModel.onAction(WorkoutAction.DoneClicked)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `a finished set is recorded`() = runTest {
        start()
        standUntilCountedIn()
        rep()
        rep()

        viewModel.onAction(WorkoutAction.FinishClicked)

        val set = repository.recorded.single()
        assertEquals(Exercise.SQUAT, set.exercise)
        assertEquals(2, set.reps.size)
        assertEquals(listOf(1, 2), set.reps.map { it.index })
        assertEquals(2, set.repsAtDepth)
        assertEquals(0, set.abandonedCount)
    }

    /**
     * The guard that matters most: everything before this wrote to memory the user
     * could lose, and this is the first thing that writes something they keep.
     */
    @Test
    fun `finishing twice records one set`() = runTest {
        start()
        standUntilCountedIn()
        rep()

        viewModel.onAction(WorkoutAction.FinishClicked)
        viewModel.onAction(WorkoutAction.FinishClicked)

        assertEquals(1, repository.recorded.size)
    }

    @Test
    fun `canceling records nothing`() = runTest {
        start()
        standUntilCountedIn()
        rep()

        viewModel.onAction(WorkoutAction.CancelClicked)

        assertTrue(repository.recorded.isEmpty())
    }

    @Test
    fun `a set in which nothing happened is not recorded`() = runTest {
        start()
        standUntilCountedIn()

        viewModel.onAction(WorkoutAction.FinishClicked)

        assertTrue(repository.recorded.isEmpty())
    }

    /**
     * Zero reps, but not nothing: an abandoned descent is the evidence that the
     * counting threshold is wrong for this body, which is what per-user
     * calibration will be offered from.
     */
    @Test
    fun `a set of only abandoned descents is recorded`() = runTest {
        start()
        standUntilCountedIn()
        abandonedDescent()
        abandonedDescent()

        viewModel.onAction(WorkoutAction.FinishClicked)

        val set = repository.recorded.single()
        assertTrue(set.reps.isEmpty())
        assertEquals(2, set.abandonedCount)
        // Deepest is the smallest angle; both descents turned back around 135.
        assertTrue("was ${set.deepestAbandonedAngle}", set.deepestAbandonedAngle!! < 145f)
    }

    /**
     * Not when Start was pressed: the walk out to your spot has no duration by
     * design, and the count-in is three seconds of standing still.
     */
    @Test
    fun `the set starts when it becomes active, not when start is pressed`() = runTest {
        start()
        val walkedOut = startedTraining + 40.minutes
        clock.instant = walkedOut

        standUntilCountedIn()
        rep()
        viewModel.onAction(WorkoutAction.FinishClicked)

        assertEquals(walkedOut, repository.recorded.single().startedAt)
    }

    /**
     * The set is timed with the clock its reps were timed with, so a wall-clock
     * correction landing mid-set cannot stretch it.
     */
    @Test
    fun `the set is measured by the frame clock, not the wall clock`() = runTest {
        start()
        standUntilCountedIn()
        rep()
        clock.instant += 3.hours

        viewModel.onAction(WorkoutAction.FinishClicked)

        val set = repository.recorded.single()
        assertTrue(set.endedAt > set.startedAt)
        assertTrue("was ${set.endedAt - set.startedAt}", set.endedAt - set.startedAt < 1.minutes)
    }

    /**
     * A set does not begin on the press: it begins when the setup check has held
     * long enough to settle and the count-in has run out, which is why walking to
     * your spot can never eat a countdown.
     */
    @Test
    fun `the set waits for you to be in position before counting in`() = runTest {
        start()
        assertTrue(viewModel.state.value.session is SessionState.Waiting)

        standUntilCountedIn()

        assertEquals(SessionState.Active, viewModel.state.value.session)
    }

    /**
     * Carrying the phone to its stand sweeps invented joints through a full rep.
     * The framing gate catches the worst of it; only counting while Active is the
     * other half.
     */
    @Test
    fun `movement before the set starts is not counted`() = runTest {
        viewModel.onAction(WorkoutAction.ScreenAttached(fakeScreenLifecycleOwner()))
        rep()
        rep()

        viewModel.onAction(WorkoutAction.StartClicked)
        standUntilCountedIn()
        viewModel.onAction(WorkoutAction.FinishClicked)

        assertEquals(0, viewModel.state.value.repCount)
        assertTrue(repository.recorded.isEmpty())
    }

    /**
     * The whole reason counting and depth are separate questions: ten shallow reps
     * must read as "10 reps, depth 42%" rather than as zero.
     */
    @Test
    fun `a shallow rep counts but does not count as depth`() = runTest {
        start()
        standUntilCountedIn()
        rep()
        shallowRep()

        viewModel.onAction(WorkoutAction.FinishClicked)

        assertEquals(2, viewModel.state.value.repCount)
        assertEquals(1, viewModel.state.value.repsAtDepth)
        assertEquals(1, repository.recorded.single().repsAtDepth)
    }

    @Test
    fun `dismissing the summary clears the counts`() = runTest {
        start()
        standUntilCountedIn()
        rep()
        viewModel.onAction(WorkoutAction.FinishClicked)

        viewModel.onAction(WorkoutAction.DoneClicked)

        val state = viewModel.state.value
        assertEquals(SessionState.Idle, state.session)
        assertEquals(0, state.repCount)
        assertEquals(0, state.repsAtDepth)
    }

    /** A set has to be finishable by ear, so nothing may silently stop speaking. */
    @Test
    fun `cues reach the speaker`() = runTest {
        start()
        standUntilCountedIn()
        rep()

        assertTrue(speaker.spoken.isNotEmpty())
    }

    @Test
    fun `a second set is recorded separately`() = runTest {
        start()
        standUntilCountedIn()
        rep()
        viewModel.onAction(WorkoutAction.FinishClicked)

        viewModel.onAction(WorkoutAction.DoneClicked)
        viewModel.onAction(WorkoutAction.GoAgainClicked)
        standUntilCountedIn()
        rep()
        rep()
        viewModel.onAction(WorkoutAction.FinishClicked)

        assertEquals(listOf(1, 2), repository.recorded.map { it.reps.size })
    }
}
