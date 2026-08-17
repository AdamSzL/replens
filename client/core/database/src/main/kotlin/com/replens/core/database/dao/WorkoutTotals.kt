package com.replens.core.database.dao

data class WorkoutTotals(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long,
    val setCount: Int,
    val repCount: Int,
    val repsAtDepth: Int,
)
