package com.replens.feature.history.ui.summary.mapper

import com.replens.core.model.Exercise
import com.replens.core.model.ExerciseSet
import com.replens.core.model.Rep
import com.replens.core.model.Workout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal val EPOCH = Instant.fromEpochMilliseconds(1_700_000_000_000L)

/**
 * A set built from the two things the summary reads — when it happened and how
 * deep each rep went. Rep timings are the same throughout because nothing on this
 * screen reads them; a test that needed them would say so by passing them.
 */
internal fun set(
    id: Long = 1,
    startedAt: Instant,
    duration: Duration = 60.seconds,
    angles: List<Float> = emptyList(),
    repsAtDepth: Int = angles.count { it <= 95f },
    abandonedCount: Int = 0,
    deepestAbandonedAngle: Float? = null,
): ExerciseSet {
    return ExerciseSet(
        id = id,
        exercise = Exercise.SQUAT,
        startedAt = startedAt,
        endedAt = startedAt + duration,
        repsAtDepth = repsAtDepth,
        abandonedCount = abandonedCount,
        deepestAbandonedAngle = deepestAbandonedAngle,
        reps = angles.mapIndexed { position, angle ->
            Rep(
                index = position + 1,
                deepestAngle = angle,
                descent = 1.seconds,
                ascent = 1.seconds,
            )
        },
    )
}

/** Spans its sets, exactly as the repository builds one. */
internal fun workout(vararg sets: ExerciseSet): Workout {
    return Workout(
        id = 1,
        startedAt = sets.first().startedAt,
        endedAt = sets.last().endedAt,
        sets = sets.toList(),
    )
}
