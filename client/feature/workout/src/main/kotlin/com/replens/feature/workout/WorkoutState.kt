package com.replens.feature.workout

import com.replens.core.exercise.RepPhase

/**
 * Screen state for the workout screen. The pose frame is deliberately **not**
 * here — see [WorkoutViewModel.poseFrame].
 */
data class WorkoutState(
    val repCount: Int = 0,
    val phase: RepPhase = RepPhase.STANDING,
)
