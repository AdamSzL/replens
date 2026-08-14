package com.replens.core.data

import com.replens.core.model.Exercise
import com.replens.core.model.Rep
import com.replens.core.model.Workout
import kotlin.time.Instant

interface WorkoutRepository {

    /**
     * Stores a finished set, attaching it to the workout in progress or opening a
     * new one, and returns **the workout's** id — the set's is not returned
     * because nothing asks for a set on its own.
     *
     * The workout is what the summary screen is about, and it is the id the caller
     * cannot work out for itself: whether this set opened a workout or joined one
     * is decided in here, by the gap rule.
     *
     * These are exactly [com.replens.core.model.ExerciseSet]'s fields minus its
     * `id`, which the database assigns — that one difference is the only reason
     * this is a parameter list rather than that type.
     *
     * `repCount` is absent because it is derived from [reps], which have to be
     * passed anyway: they are rows. [abandonedCount] and [deepestAbandonedAngle]
     * are passed, because abandoned descents are *not* rows, so there is nothing
     * here to derive them from — which is the same asymmetry `ExerciseSet` has.
     */
    suspend fun recordSet(
        exercise: Exercise,
        startedAt: Instant,
        endedAt: Instant,
        repsAtDepth: Int,
        abandonedCount: Int,
        deepestAbandonedAngle: Float?,
        reps: List<Rep>,
    ): Long

    /** `null` if there is no such workout, or nothing in it this build can read. */
    suspend fun workout(id: Long): Workout?
}
