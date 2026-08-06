package com.replens.core.exercise.squat

import com.replens.core.model.BodyPose
import com.replens.core.model.Landmark
import com.replens.core.model.LandmarkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val TOLERANCE = 0.001f

private fun landmark(
    type: LandmarkType,
    x: Float,
    y: Float,
    likelihood: Float = 1f,
) = Landmark(type = type, x = x, y = y, z = 0f, inFrameLikelihood = likelihood)

/**
 * A figure with straight legs (180 degree knees) unless overridden, y growing
 * downwards: shoulders 100, hips 200, knees 300, ankles 400.
 */
private fun pose(
    leftLikelihood: Float = 1f,
    rightLikelihood: Float = 1f,
    leftAnkleX: Float = 20f,
    rightAnkleX: Float = -20f,
) = BodyPose(
    listOf(
        landmark(LandmarkType.LEFT_SHOULDER, 40f, 100f),
        landmark(LandmarkType.RIGHT_SHOULDER, -40f, 100f),
        landmark(LandmarkType.LEFT_HIP, 20f, 200f, leftLikelihood),
        landmark(LandmarkType.RIGHT_HIP, -20f, 200f, rightLikelihood),
        landmark(LandmarkType.LEFT_KNEE, 20f, 300f, leftLikelihood),
        landmark(LandmarkType.RIGHT_KNEE, -20f, 300f, rightLikelihood),
        landmark(LandmarkType.LEFT_ANKLE, leftAnkleX, 400f, leftLikelihood),
        landmark(LandmarkType.RIGHT_ANKLE, rightAnkleX, 400f, rightLikelihood),
    )
)

class SquatSignalsTest {

    @Test
    fun `straight legs read as 180 degrees on both sides`() {
        val signals = pose().squatSignals(timestampMillis = 0L)

        assertEquals(180f, signals.leftKneeAngle!!, TOLERANCE)
        assertEquals(180f, signals.rightKneeAngle!!, TOLERANCE)
        assertEquals(180f, signals.depthAngle!!, TOLERANCE)
    }

    @Test
    fun `both legs confident are averaged`() {
        // Left ankle displaced so the left knee bends; right stays straight.
        val signals = pose(leftAnkleX = 120f).squatSignals(timestampMillis = 0L)

        val left = signals.leftKneeAngle!!
        val right = signals.rightKneeAngle!!
        assertEquals((left + right) / 2f, signals.depthAngle!!, TOLERANCE)
    }

    @Test
    fun `an unconfident leg is excluded rather than averaged in`() {
        // This is the side-on case: the far leg is inferred and badly wrong.
        val sideOn = BodyPose(
            listOf(
                landmark(LandmarkType.LEFT_HIP, 20f, 200f, 0.96f),
                landmark(LandmarkType.LEFT_KNEE, 20f, 300f, 0.96f),
                landmark(LandmarkType.LEFT_ANKLE, 120f, 300f, 0.96f), // 90 degrees
                landmark(LandmarkType.RIGHT_HIP, -20f, 200f, 0.28f),
                landmark(LandmarkType.RIGHT_KNEE, -20f, 300f, 0.28f),
                landmark(LandmarkType.RIGHT_ANKLE, -20f, 400f, 0.28f), // 180, hallucinated
            )
        )

        val signals = sideOn.squatSignals(timestampMillis = 0L)

        assertEquals(90f, signals.leftKneeAngle!!, TOLERANCE)
        assertNull(signals.rightKneeAngle)
        // Averaging both would have given 135 and silently lost the rep.
        assertEquals(90f, signals.depthAngle!!, TOLERANCE)
    }

    @Test
    fun `one unconfident joint disqualifies its whole leg`() {
        val badAnkleOnly = BodyPose(
            listOf(
                landmark(LandmarkType.LEFT_HIP, 20f, 200f, 0.99f),
                landmark(LandmarkType.LEFT_KNEE, 20f, 300f, 0.99f),
                landmark(LandmarkType.LEFT_ANKLE, 20f, 400f, 0.10f),
            )
        )

        assertNull(badAnkleOnly.squatSignals(timestampMillis = 0L).leftKneeAngle)
    }

    @Test
    fun `no confident leg means no depth reading`() {
        val signals = pose(leftLikelihood = 0.2f, rightLikelihood = 0.2f)
            .squatSignals(timestampMillis = 0L)

        assertNull(signals.leftKneeAngle)
        assertNull(signals.rightKneeAngle)
        assertNull(signals.depthAngle)
    }

    @Test
    fun `a missing landmark yields null rather than a wrong number`() {
        val noAnkles = BodyPose(
            listOf(
                landmark(LandmarkType.LEFT_HIP, 20f, 200f),
                landmark(LandmarkType.LEFT_KNEE, 20f, 300f),
            )
        )

        assertNull(noAnkles.squatSignals(timestampMillis = 0L).depthAngle)
    }

    @Test
    fun `the likelihood threshold is configurable`() {
        val marginal = pose(leftLikelihood = 0.6f, rightLikelihood = 0.6f)

        assertEquals(
            180f,
            marginal.squatSignals(0L, minLikelihood = 0.5f).depthAngle!!,
            TOLERANCE,
        )
        assertNull(marginal.squatSignals(0L, minLikelihood = 0.8f).depthAngle)
    }

    @Test
    fun `standing upright reports a vertical femur and no torso lean`() {
        val signals = pose().squatSignals(timestampMillis = 0L)

        // Hip directly above knee: the thigh is vertical.
        assertEquals(180f, signals.femurInclination!!, TOLERANCE)
        assertEquals(0f, signals.torsoLeanDegrees!!, TOLERANCE)
    }

    @Test
    fun `the timestamp is carried through`() {
        assertEquals(4321L, pose().squatSignals(timestampMillis = 4321L).timestampMillis)
    }
}
