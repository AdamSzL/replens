package com.replens.core.exercise

import kotlin.time.Instant

/**
 * [id] is always real: recording a set does not go through this type, so there is
 * no unsaved state to encode.
 *
 * [repsAtDepth] is carried rather than derived from [reps] because depth is
 * graded against a **tunable** threshold — re-deriving it would make an old set
 * report a different score than it did at the time.
 *
 * [deepestAbandonedAngle] is a **minimum**: 128 is shallower than 95.
 */
data class ExerciseSet(
    val id: Long,
    val exercise: Exercise,
    val startedAt: Instant,
    val endedAt: Instant,
    val repsAtDepth: Int,
    val abandonedCount: Int,
    val deepestAbandonedAngle: Float?,
    val reps: List<Rep>,
) {
    val repCount: Int get() = reps.size
}
