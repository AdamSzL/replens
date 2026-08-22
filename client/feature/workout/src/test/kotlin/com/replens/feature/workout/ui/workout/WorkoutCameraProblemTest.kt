package com.replens.feature.workout.ui.workout

import com.replens.core.model.Exercise
import com.replens.core.pose.CameraAvailability
import com.replens.core.testing.FakeClock
import com.replens.core.testing.FakeWorkoutRepository
import com.replens.core.testing.MainDispatcherRule
import com.replens.feature.workout.ui.workout.model.CameraProblem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

/**
 * What the screen says when the camera will not open — a state that only exists
 * because this screen can be restored straight from the back stack, with no
 * picker in front of it to check anything.
 */
class WorkoutCameraProblemTest {

    @get:Rule
    internal val mainDispatcherRule = MainDispatcherRule()

    private val camera = FakePoseCameraDataSource()

    private val viewModel by lazy {
        WorkoutViewModel(
            exercise = Exercise.SQUAT,
            poseCamera = camera,
            speaker = FakeSpeaker(),
            repository = FakeWorkoutRepository(),
            clock = FakeClock(Instant.fromEpochMilliseconds(0L)),
        )
    }

    private val problem: CameraProblem?
        get() = viewModel.state.value.cameraProblem

    private fun resume(isCameraGranted: Boolean) {
        viewModel.onAction(WorkoutAction.ScreenResumed(isCameraGranted))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.collectEvents(): List<WorkoutEvent> {
        val events = mutableListOf<WorkoutEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }
        return events
    }

    @Test
    fun `a camera that opens is no problem`() = runTest {
        resume(isCameraGranted = true)

        assertNull(problem)
    }

    /** The #33 case: restored onto this screen after a revoke killed the process. */
    @Test
    fun `a revoked camera names the permission`() = runTest {
        resume(isCameraGranted = false)

        camera.cameraAvailability.value = CameraAvailability.Unavailable

        assertEquals(CameraProblem.PermissionMissing, problem)
    }

    @Test
    fun `a camera held by another app says so`() = runTest {
        resume(isCameraGranted = true)

        camera.cameraAvailability.value = CameraAvailability.Unavailable

        assertEquals(CameraProblem.InUse, problem)
    }

    /**
     * The camera binds on composition, the permission is read on resume, and
     * nothing orders the two. Until the reading lands there is nothing to say.
     */
    @Test
    fun `a failure before the first resume says nothing`() = runTest {
        camera.cameraAvailability.value = CameraAvailability.Unavailable

        assertNull(problem)
    }

    /**
     * The observed sequence carries a critical error into the waiting state, so
     * both arrive before the screen settles and must read the same.
     */
    @Test
    fun `a critical error without the permission still names the permission`() = runTest {
        resume(isCameraGranted = false)

        camera.cameraAvailability.value = CameraAvailability.Failed

        assertEquals(CameraProblem.PermissionMissing, problem)
    }

    /**
     * Granting in Settings does not rebind anything here — CameraX re-attaches on
     * start by itself — so the recovery this asserts is the camera reporting it.
     */
    @Test
    fun `a recovered camera clears the problem`() = runTest {
        resume(isCameraGranted = false)
        camera.cameraAvailability.value = CameraAvailability.Unavailable

        resume(isCameraGranted = true)
        camera.cameraAvailability.value = CameraAvailability.Ready

        assertNull(problem)
    }

    /**
     * A resume with the permission back must re-decide even when the camera has
     * not reported again, or the screen keeps blaming a permission that is held.
     */
    @Test
    fun `a resume re-decides without the camera reporting again`() = runTest {
        resume(isCameraGranted = false)
        camera.cameraAvailability.value = CameraAvailability.Unavailable

        resume(isCameraGranted = true)

        assertEquals(CameraProblem.InUse, problem)
    }

    @Test
    fun `the settings button asks for settings`() = runTest {
        val events = collectEvents()

        viewModel.onAction(WorkoutAction.OpenSettingsClicked)

        assertEquals(listOf(WorkoutEvent.OpenAppSettings), events)
    }

    @Test
    fun `the back button leaves the screen`() = runTest {
        val events = collectEvents()

        viewModel.onAction(WorkoutAction.GoBackClicked)

        assertEquals(listOf(WorkoutEvent.NavigateBack), events)
    }
}
