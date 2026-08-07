package com.replens.feature.workout

import com.replens.core.exercise.RepPhase
import com.replens.core.pose.CameraFacing
import com.replens.core.pose.CameraOptions

/**
 * Screen state for the workout screen. The pose frame is deliberately **not**
 * here — see [WorkoutViewModel.poseFrame].
 *
 * @param cameraFacing a choice, but one constrained by hardware — null until
 *   [cameraOptions] says which lenses exist. [zoomRatio] needs no such wait: 1x
 *   is valid on every camera.
 */
internal data class WorkoutState(
    val repCount: Int = 0,
    val phase: RepPhase = RepPhase.STANDING,
    val cameraFacing: CameraFacing? = null,
    val zoomRatio: Float = 1f,
    val cameraOptions: CameraOptions? = null,
)
