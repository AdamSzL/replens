package com.replens.feature.workout

import com.replens.core.exercise.RepPhase

/**
 * Screen state for the workout screen. The pose frame is deliberately **not**
 * here — see [WorkoutViewModel.poseFrame].
 *
 * @param zoomStops empty until a camera is bound and reports what it supports.
 */
data class WorkoutState(
    val repCount: Int = 0,
    val phase: RepPhase = RepPhase.STANDING,
    val zoomRatio: Float = 1f,
    val zoomStops: List<Float> = emptyList(),
)
