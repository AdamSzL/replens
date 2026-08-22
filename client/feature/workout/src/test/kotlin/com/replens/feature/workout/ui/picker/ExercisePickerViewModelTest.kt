package com.replens.feature.workout.ui.picker

import com.replens.core.model.Exercise
import com.replens.core.testing.MainDispatcherRule
import com.replens.core.testing.collectEvents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExercisePickerViewModelTest {

    @get:Rule
    internal val mainDispatcherRule = MainDispatcherRule()

    private val cameraPermission = FakeCameraPermissionRepository()

    /** Lazy for the reason spelled out in `WorkoutViewModelTest`. */
    private val viewModel by lazy { ExercisePickerViewModel(cameraPermission) }

    private fun resume(isGranted: Boolean = false, canShowRationale: Boolean = false) {
        viewModel.onAction(
            ExercisePickerAction.ScreenResumed(
                isCameraGranted = isGranted,
                canShowCameraRationale = canShowRationale,
            )
        )
    }

    private fun answer(isGranted: Boolean, canShowRationale: Boolean = false) {
        viewModel.onAction(
            ExercisePickerAction.PermissionAnswered(
                isGranted = isGranted,
                canShowRationale = canShowRationale,
            )
        )
    }

    private fun pickSquat() {
        viewModel.onAction(ExercisePickerAction.ExerciseClicked(Exercise.SQUAT))
    }

    private val isBlockedDialogVisible: Boolean
        get() = viewModel.state.value.isCameraBlockedDialogVisible

    @Test
    fun `an exercise picked with the camera granted opens the workout`() = runTest {
        val events = collectEvents(viewModel.events)
        resume(isGranted = true)

        pickSquat()

        assertEquals(listOf(ExercisePickerEvent.NavigateToWorkout(Exercise.SQUAT)), events)
    }

    @Test
    fun `an exercise picked without the camera asks for it`() = runTest {
        val events = collectEvents(viewModel.events)
        resume()

        pickSquat()

        assertEquals(listOf(ExercisePickerEvent.RequestCameraPermission), events)
    }

    @Test
    fun `granting the camera opens the exercise that asked for it`() = runTest {
        val events = collectEvents(viewModel.events)
        resume()
        pickSquat()

        answer(isGranted = true)

        val expected = listOf(
            ExercisePickerEvent.RequestCameraPermission,
            ExercisePickerEvent.NavigateToWorkout(Exercise.SQUAT),
        )
        assertEquals(expected, events)
    }

    /**
     * A warranted rationale after a result is the only moment Android reports the
     * denial. The next one flips it back to false and the fact is unrecoverable.
     */
    @Test
    fun `a denial the system still asks about is remembered`() = runTest {
        resume()
        pickSquat()

        answer(isGranted = false, canShowRationale = true)

        assertTrue(cameraPermission.markedDenied)
    }

    /**
     * Backing out of the system dialog reports a denial Android records nowhere.
     * Remembering it would route the user to Settings over a permission they were
     * never really asked about.
     */
    @Test
    fun `backing out of the system dialog is not a denial`() = runTest {
        val events = collectEvents(viewModel.events)
        resume()
        pickSquat()

        answer(isGranted = false, canShowRationale = false)
        pickSquat()

        assertFalse(cameraPermission.markedDenied)
        assertFalse(isBlockedDialogVisible)
        val expected = listOf(
            ExercisePickerEvent.RequestCameraPermission,
            ExercisePickerEvent.RequestCameraPermission,
        )
        assertEquals(expected, events)
    }

    @Test
    fun `an exercise picked once the system stops asking shows the blocked dialog`() = runTest {
        val events = collectEvents(viewModel.events)
        resume()
        pickSquat()
        answer(isGranted = false, canShowRationale = true)
        pickSquat()
        answer(isGranted = false, canShowRationale = false)

        pickSquat()

        assertTrue(isBlockedDialogVisible)
        assertEquals(2, events.count { it == ExercisePickerEvent.RequestCameraPermission })
    }

    /** The flag outlives the process; the rationale the system reports does not. */
    @Test
    fun `a denial remembered from an earlier process blocks without asking`() = runTest {
        cameraPermission.isDenied = true
        val events = collectEvents(viewModel.events)
        resume()

        pickSquat()

        assertTrue(isBlockedDialogVisible)
        assertTrue(events.isEmpty())
    }

    /**
     * Revoking in Settings is a denial the app never sees happen: the warranted
     * rationale on the next resume is the whole of the notice we get.
     */
    @Test
    fun `a camera revoked in settings is remembered on resume`() = runTest {
        resume(isGranted = false, canShowRationale = true)

        assertTrue(cameraPermission.markedDenied)
    }

    @Test
    fun `opening settings dismisses the blocked dialog`() = runTest {
        cameraPermission.isDenied = true
        val events = collectEvents(viewModel.events)
        resume()
        pickSquat()

        viewModel.onAction(ExercisePickerAction.OpenSettingsClicked)

        assertEquals(listOf(ExercisePickerEvent.OpenAppSettings), events)
        assertFalse(isBlockedDialogVisible)
    }

    @Test
    fun `granting in settings opens the exercise that was blocked`() = runTest {
        cameraPermission.isDenied = true
        val events = collectEvents(viewModel.events)
        resume()
        pickSquat()
        viewModel.onAction(ExercisePickerAction.OpenSettingsClicked)

        resume(isGranted = true)

        val expected = listOf(
            ExercisePickerEvent.OpenAppSettings,
            ExercisePickerEvent.NavigateToWorkout(Exercise.SQUAT),
        )
        assertEquals(expected, events)
    }

    /**
     * A kept intent has no expiry: granting the camera by any route later, and
     * coming back to the picker, would open a workout nobody asked for on that
     * visit.
     */
    @Test
    fun `returning from settings without the camera forgets the exercise`() = runTest {
        cameraPermission.isDenied = true
        val events = collectEvents(viewModel.events)
        resume()
        pickSquat()
        viewModel.onAction(ExercisePickerAction.OpenSettingsClicked)

        resume()
        resume(isGranted = true)

        assertEquals(listOf(ExercisePickerEvent.OpenAppSettings), events)
    }

    /**
     * Otherwise the next resume that finds the camera granted starts a workout the
     * user backed out of.
     */
    @Test
    fun `dismissing the blocked dialog forgets the exercise`() = runTest {
        cameraPermission.isDenied = true
        val events = collectEvents(viewModel.events)
        resume()
        pickSquat()

        viewModel.onAction(ExercisePickerAction.DialogDismissed)
        resume(isGranted = true)

        assertFalse(isBlockedDialogVisible)
        assertTrue(events.isEmpty())
    }

    /**
     * Closing the system dialog fires both, in an order nothing guarantees, so
     * each has to be a no-op once the other has navigated.
     */
    @Test
    fun `a result followed by the resume navigates once`() = runTest {
        val events = collectEvents(viewModel.events)
        resume()
        pickSquat()

        answer(isGranted = true)
        resume(isGranted = true)

        assertEquals(1, events.count { it is ExercisePickerEvent.NavigateToWorkout })
    }

    @Test
    fun `a resume followed by the result navigates once`() = runTest {
        val events = collectEvents(viewModel.events)
        resume()
        pickSquat()

        resume(isGranted = true)
        answer(isGranted = true)

        assertEquals(1, events.count { it is ExercisePickerEvent.NavigateToWorkout })
    }

    /**
     * The flag is read once and never awaited, so a tap that beats the read asks
     * the system rather than leaving the button dead until disk answers.
     */
    @Test
    fun `an exercise picked before the flag has loaded asks the system`() = runTest {
        cameraPermission.isDenied = true
        cameraPermission.readInFlight = CompletableDeferred()
        val events = collectEvents(viewModel.events)
        resume()

        pickSquat()

        assertEquals(listOf(ExercisePickerEvent.RequestCameraPermission), events)
    }
}
