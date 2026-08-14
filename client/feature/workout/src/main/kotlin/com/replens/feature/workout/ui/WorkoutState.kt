package com.replens.feature.workout.ui

import com.replens.core.exercise.SessionState
import com.replens.core.model.RepPhase
import com.replens.core.pose.CameraFacing
import com.replens.core.pose.CameraOptions

/**
 * Screen state for the workout screen. The pose frame is deliberately **not**
 * here — see [WorkoutViewModel.poseFrame].
 *
 * @param repCount survives into [SessionState.Finished] rather than being copied
 *   onto it, so there is only ever one count to keep correct.
 * @param cameraFacing a choice, but one constrained by hardware — null until
 *   [cameraOptions] says which lenses exist. [zoomRatio] needs no such wait: 1x
 *   is valid on every camera.
 */
internal data class WorkoutState(
    val session: SessionState = SessionState.Idle,
    val repCount: Int = 0,
    /** Of [repCount]; counting and grading depth are separate questions. */
    val repsAtDepth: Int = 0,
    val phase: RepPhase = RepPhase.STANDING,
    val cameraFacing: CameraFacing? = null,
    val zoomRatio: Float = 1f,
    val cameraOptions: CameraOptions? = null,
)
