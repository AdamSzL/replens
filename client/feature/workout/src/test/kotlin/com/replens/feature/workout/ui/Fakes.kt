package com.replens.feature.workout.ui

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.replens.core.audio.Speaker
import com.replens.core.data.WorkoutRepository
import com.replens.core.model.Exercise
import com.replens.core.model.PoseFrame
import com.replens.core.model.Rep
import com.replens.core.model.Workout
import com.replens.core.pose.CameraFacing
import com.replens.core.pose.CameraOptions
import com.replens.core.pose.PoseCameraDataSource
import com.replens.core.ui.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Publishes [options] before anything asks for a facing, which is the handshake
 * the real data source performs — a fake that resolved them the other way round
 * would let a bug through that the camera would not.
 */
internal class FakePoseCameraDataSource : PoseCameraDataSource {

    override val surfaceRequests: StateFlow<SurfaceRequest?> = MutableStateFlow(null)

    val cameraOptions = MutableStateFlow<CameraOptions?>(
        CameraOptions(facings = setOf(CameraFacing.FRONT, CameraFacing.BACK)),
    )

    override val options: StateFlow<CameraOptions?> = cameraOptions

    val frames = MutableSharedFlow<PoseFrame>(extraBufferCapacity = 256)

    /** The owner currently bound, so a test can see a rebind onto a new one. */
    var boundTo: LifecycleOwner? = null
        private set

    override fun poseFrames(
        lifecycleOwners: Flow<LifecycleOwner?>,
        facings: Flow<CameraFacing>,
        zoomRatios: Flow<Float>,
    ): Flow<PoseFrame> = channelFlow {
        launch { lifecycleOwners.collect { boundTo = it } }
        // Frames stop while unbound rather than the stream ending — modeled here
        // because that difference is the whole reason the owner is a flow.
        frames.collect { if (boundTo != null) send(it) }
    }
}

/**
 * A distinct owner per call, because that is what a real return produces:
 * Navigation 3 destroys an entry's owner along with its composition and builds a
 * new one on the way back. Reusing one instance would hide the rebind the camera
 * has to perform.
 *
 * The fake camera never binds, so nothing may read the lifecycle itself.
 */
internal fun screenLifecycleOwner(): LifecycleOwner = object : LifecycleOwner {
    override val lifecycle: Lifecycle get() = error("the fake camera does not bind")
}

internal class FakeSpeaker : Speaker {
    val spoken = mutableListOf<UiText>()

    override fun speak(cue: UiText) {
        spoken += cue
    }
}

internal class FakeWorkoutRepository : WorkoutRepository {

    data class Recorded(
        val exercise: Exercise,
        val startedAt: Instant,
        val endedAt: Instant,
        val repsAtDepth: Int,
        val abandonedCount: Int,
        val deepestAbandonedAngle: Float?,
        val reps: List<Rep>,
    )

    val recorded = mutableListOf<Recorded>()

    /**
     * Held open to keep a write in flight, so a test can press Done before the
     * workout id exists. Null means the write completes as soon as it is called.
     */
    var inFlight: CompletableDeferred<Unit>? = null

    override suspend fun recordSet(
        exercise: Exercise,
        startedAt: Instant,
        endedAt: Instant,
        repsAtDepth: Int,
        abandonedCount: Int,
        deepestAbandonedAngle: Float?,
        reps: List<Rep>,
    ): Long {
        recorded += Recorded(
            exercise = exercise,
            startedAt = startedAt,
            endedAt = endedAt,
            repsAtDepth = repsAtDepth,
            abandonedCount = abandonedCount,
            deepestAbandonedAngle = deepestAbandonedAngle,
            reps = reps,
        )
        inFlight?.await()
        return WORKOUT_ID
    }

    override suspend fun workout(id: Long): Workout? = null

    companion object {
        /** Deliberately not 1: a set count standing in for a workout id would pass. */
        const val WORKOUT_ID = 7L
    }
}

internal class FakeClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
}
