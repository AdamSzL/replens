package com.replens.core.testing

import com.replens.core.data.WorkoutRepository
import com.replens.core.model.Exercise
import com.replens.core.model.Rep
import com.replens.core.model.Workout
import com.replens.core.model.WorkoutOverview
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val stored: List<Workout> = emptyList(),
) : WorkoutRepository {

    /**
     * The history list's source, mutable so a test can land a workout while the
     * screen is already collecting — which is the behavior that separates a
     * reactive list from one that reads once.
     *
     * Emits nothing until a test says so, for the same reason the `inFlight`
     * gates exist: a `StateFlow` starting at `emptyList()` would answer "no
     * workouts" before the query has run, and no test could tell the screen's
     * loading state from its empty one.
     *
     * Independent of [stored] rather than derived from it: the two answer
     * different callers, and no test wants both.
     */
    val overviews = MutableSharedFlow<List<WorkoutOverview>>(replay = 1)

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
        return stored.firstOrNull { it.id == id }
    }

    override fun workouts(): Flow<List<WorkoutOverview>> = overviews

    companion object {
        /** Deliberately not 1: a set count standing in for a workout id would pass. */
        const val WORKOUT_ID = 7L
    }
}
