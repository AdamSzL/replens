package com.replens.core.data.mapper

import com.replens.core.database.entity.RepEntity
import com.replens.core.database.entity.SetEntity
import com.replens.core.database.entity.WorkoutEntity
import com.replens.core.model.Exercise
import com.replens.core.model.ExerciseSet
import com.replens.core.model.Rep
import com.replens.core.model.Workout
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * The only place that knows a `Duration` is stored as millis and an [Exercise] as
 * its name. Both directions live here rather than in a Room converter, so the
 * entity keeps saying `descentMillis: Long` to anyone who opens it.
 */

internal fun Rep.toEntity(setId: Long): RepEntity {
    return RepEntity(
        setId = setId,
        index = index,
        deepestAngle = deepestAngle,
        descentMillis = descent.inWholeMilliseconds,
        ascentMillis = ascent.inWholeMilliseconds,
    )
}

internal fun RepEntity.toDomain(): Rep {
    return Rep(
        index = index,
        deepestAngle = deepestAngle,
        descent = descentMillis.milliseconds,
        ascent = ascentMillis.milliseconds,
    )
}

/**
 * `null` when the stored name is one this build has no constant for, which the
 * caller turns into a dropped row. Deliberately not `enumValueOf`, which throws:
 * enums are DTOs, and a name written by a newer build — or, later, arriving from
 * the server — must not take out the screen reading it.
 */
internal fun SetEntity.toDomain(reps: List<RepEntity>): ExerciseSet? {
    val known = Exercise.entries.find { it.name == exercise } ?: return null
    return ExerciseSet(
        id = id,
        exercise = known,
        startedAt = Instant.fromEpochMilliseconds(startedAt),
        endedAt = Instant.fromEpochMilliseconds(endedAt),
        repsAtDepth = repsAtDepth,
        abandonedCount = abandonedCount,
        deepestAbandonedAngle = deepestAbandonedAngle,
        reps = reps.map(RepEntity::toDomain),
    )
}

internal fun WorkoutEntity.toDomain(sets: List<ExerciseSet>): Workout {
    return Workout(
        id = id,
        startedAt = Instant.fromEpochMilliseconds(startedAt),
        endedAt = Instant.fromEpochMilliseconds(endedAt),
        sets = sets,
    )
}
