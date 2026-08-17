package com.replens.core.model

import kotlin.time.Instant

data class WorkoutOverview(
    val id: Long,
    val startedAt: Instant,
    val endedAt: Instant,
    val setCount: Int,
    val repCount: Int,
    val repsAtDepth: Int,
)
