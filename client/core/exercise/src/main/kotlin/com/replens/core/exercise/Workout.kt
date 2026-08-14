package com.replens.core.exercise

import kotlin.time.Instant

/**
 * [endedAt] is its last set's. Nothing ends a workout — the boundary is inferred
 * when the next set starts — so one abandoned mid-session is already correct
 * rather than a case to repair.
 */
data class Workout(
    val id: Long,
    val startedAt: Instant,
    val endedAt: Instant,
    val sets: List<ExerciseSet>,
)
