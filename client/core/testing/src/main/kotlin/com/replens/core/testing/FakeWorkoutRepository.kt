package com.replens.core.testing

import com.replens.core.data.WorkoutRepository
import com.replens.core.model.Exercise
import com.replens.core.model.Rep
import com.replens.core.model.Workout
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Instant

/**
 * Both halves of the repository, because the two callers use opposite ones: the
 * workout screen writes sets, the summary reads a workout back.
 *
 * The `inFlight` gates model the one thing a fake otherwise gets wrong — a real
 * repository suspends on disk, so there is a window in which the write has not
 * landed and the read has not returned. Tests that care about that window hold
 * the gate open; the rest leave it null and both calls complete immediately.
 */
class FakeWorkoutRepository(
    private val workouts: List<Workout> = emptyList(),
) : WorkoutRepository {

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

    var writeInFlight: CompletableDeferred<Unit>? = null
    var readInFlight: CompletableDeferred<Unit>? = null

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
        writeInFlight?.await()
        return WORKOUT_ID
    }

    override suspend fun workout(id: Long): Workout? {
        readInFlight?.await()
        return workouts.firstOrNull { it.id == id }
    }

    companion object {
        /** Deliberately not 1: a set count standing in for a workout id would pass. */
        const val WORKOUT_ID = 7L
    }
}
