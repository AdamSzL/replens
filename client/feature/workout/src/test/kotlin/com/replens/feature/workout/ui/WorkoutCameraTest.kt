package com.replens.feature.workout.ui

import com.replens.core.pose.CameraFacing
import com.replens.core.pose.CameraOptions
import com.replens.core.pose.ZoomRange
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

/**
 * Camera *selection* — what the user chose — as opposed to capabilities, which
 * the data source discovers. The two were conflated once and the app crashed at
 * startup on a device with no front lens.
 */
class WorkoutCameraTest {

    @get:Rule
    internal val mainDispatcherRule = MainDispatcherRule()

    private val camera = FakePoseCameraDataSource()

    private val viewModel by lazy {
        WorkoutViewModel(
            poseCamera = camera,
            speaker = FakeSpeaker(),
            repository = FakeWorkoutRepository(),
            clock = FakeClock(Instant.fromEpochMilliseconds(0L)),
        )
    }

    @Test
    fun `the front lens is preferred when the device has one`() = runTest {
        assertEquals(CameraFacing.FRONT, viewModel.state.value.cameraFacing)
    }

    /**
     * The manifest asks for `camera.any`, deliberately not `camera.front`, so the
     * app has to run on a back lens alone rather than be filtered off Play.
     */
    @Test
    fun `a device without a front lens selects the back one`() = runTest {
        camera.cameraOptions.value = CameraOptions(facings = setOf(CameraFacing.BACK))

        assertEquals(CameraFacing.BACK, viewModel.state.value.cameraFacing)
    }

    /** Nothing may bind until the lens is known to exist. */
    @Test
    fun `nothing is selected before the camera reports what it has`() = runTest {
        camera.cameraOptions.value = null

        assertEquals(null, viewModel.state.value.cameraFacing)
    }

    /**
     * The zoom range arrives *after* the first bind, so options update again once
     * a lens is open — and that update must not reset a flip the user just made.
     */
    @Test
    fun `a later options update does not undo a flip`() = runTest {
        viewModel.onAction(WorkoutAction.CameraFlipClicked)

        camera.cameraOptions.value = CameraOptions(
            facings = setOf(CameraFacing.FRONT, CameraFacing.BACK),
            zoomRange = ZoomRange(0.5f, 10f),
        )

        assertEquals(CameraFacing.BACK, viewModel.state.value.cameraFacing)
    }

    /** The new lens reports its own range, so a ratio from the old one is meaningless. */
    @Test
    fun `flipping resets the zoom`() = runTest {
        viewModel.onAction(WorkoutAction.ZoomSelected(2f))

        viewModel.onAction(WorkoutAction.CameraFlipClicked)

        assertEquals(CameraFacing.BACK, viewModel.state.value.cameraFacing)
        assertEquals(1f, viewModel.state.value.zoomRatio, 0.001f)
    }

    /**
     * The button is hidden with one lens, but binding one the device lacks throws
     * rather than no-ops, so the guard is in the ViewModel and not only in the UI.
     */
    @Test
    fun `flipping is refused when there is no second lens`() = runTest {
        camera.cameraOptions.value = CameraOptions(facings = setOf(CameraFacing.FRONT))

        viewModel.onAction(WorkoutAction.CameraFlipClicked)

        assertEquals(CameraFacing.FRONT, viewModel.state.value.cameraFacing)
    }

    @Test
    fun `starting the camera twice does not open a second stream`() = runTest {
        viewModel.startCamera(NoLifecycleOwner)
        viewModel.startCamera(NoLifecycleOwner)

        assertEquals(1, camera.frames.subscriptionCount.value)
    }
}
