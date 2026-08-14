package com.replens.feature.workout.ui

import com.replens.core.model.BodyPose
import com.replens.core.model.Landmark
import com.replens.core.model.LandmarkType
import com.replens.core.model.PoseFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val FRAME_WIDTH = 480
private const val FRAME_HEIGHT = 640

/** 124/640 = 0.194, the mean measured over 231 frames of squatting at 2-3 m. */
private const val TORSO_PX = 124f
private const val SEGMENT_PX = 100f
private const val KNEE_X = 200f
private const val KNEE_Y = 400f

private fun landmark(type: LandmarkType, x: Float, y: Float): Landmark {
    return Landmark(type = type, x = x, y = y, z = 0f, inFrameLikelihood = 1f)
}

/**
 * A body bent to [kneeAngle] — the interior hip-knee-ankle angle, so 180 is
 * standing and smaller is deeper, per CLAUDE.md's convention trap.
 *
 * Framed so `setupCheck` reads READY and `squatDepthAngle` returns the angle
 * asked for: torso at 0.194 of the frame, every landmark inside the image, both
 * legs fully confident. The ankles stay put and the hips travel, which is what a
 * squat does — and is why the torso fraction cannot rise mid-rep.
 *
 * The x offsets are ±10 on both the hip and the knee so they cancel, leaving the
 * per-leg knee angle exactly [kneeAngle] rather than approximately.
 */
internal fun squatFrame(kneeAngle: Float, timestampMillis: Long): PoseFrame {
    val radians = kneeAngle * PI.toFloat() / 180f
    val hipX = KNEE_X + SEGMENT_PX * sin(radians)
    val hipY = KNEE_Y + SEGMENT_PX * cos(radians)
    val shoulderY = hipY - TORSO_PX
    return PoseFrame(
        pose = BodyPose(
            listOf(
                landmark(LandmarkType.LEFT_SHOULDER, hipX + 20f, shoulderY),
                landmark(LandmarkType.RIGHT_SHOULDER, hipX - 20f, shoulderY),
                landmark(LandmarkType.LEFT_HIP, hipX + 10f, hipY),
                landmark(LandmarkType.RIGHT_HIP, hipX - 10f, hipY),
                landmark(LandmarkType.LEFT_KNEE, KNEE_X + 10f, KNEE_Y),
                landmark(LandmarkType.RIGHT_KNEE, KNEE_X - 10f, KNEE_Y),
                landmark(LandmarkType.LEFT_ANKLE, KNEE_X + 10f, KNEE_Y + SEGMENT_PX),
                landmark(LandmarkType.RIGHT_ANKLE, KNEE_X - 10f, KNEE_Y + SEGMENT_PX),
            ),
        ),
        sourceWidth = FRAME_WIDTH,
        sourceHeight = FRAME_HEIGHT,
        timestampMillis = timestampMillis,
    )
}
