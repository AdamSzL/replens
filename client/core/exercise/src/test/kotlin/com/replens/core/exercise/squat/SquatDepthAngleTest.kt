package com.replens.core.exercise.squat

import com.replens.core.exercise.Framing
import com.replens.core.exercise.framing
import com.replens.core.model.BodyPose
import com.replens.core.model.Landmark
import com.replens.core.model.LandmarkType
import com.replens.core.model.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val FRAME_WIDTH = 480
private const val FRAME_HEIGHT = 640
private const val TOLERANCE = 0.001f

private fun landmark(type: LandmarkType, x: Float, y: Float) =
    Landmark(type = type, x = x, y = y, z = 0f, inFrameLikelihood = 1f)

/**
 * A figure with dead straight legs, so [squatDepthAngle] is 180 whenever it is
 * read at all — the point of these tests is which frames get read, not the angle.
 * [torsoHeight] is what moves the framing verdict: shoulders sit at y=100 and the
 * hips follow, so it is the torso fraction in pixels.
 */
private fun frame(torsoHeight: Float): PoseFrame {
    val hipY = 100f + torsoHeight
    return PoseFrame(
        pose = BodyPose(
            listOf(
                landmark(LandmarkType.LEFT_SHOULDER, 40f, 100f),
                landmark(LandmarkType.RIGHT_SHOULDER, -40f, 100f),
                landmark(LandmarkType.LEFT_HIP, 20f, hipY),
                landmark(LandmarkType.RIGHT_HIP, -20f, hipY),
                landmark(LandmarkType.LEFT_KNEE, 20f, hipY + 100f),
                landmark(LandmarkType.RIGHT_KNEE, -20f, hipY + 100f),
                landmark(LandmarkType.LEFT_ANKLE, 20f, hipY + 200f),
                landmark(LandmarkType.RIGHT_ANKLE, -20f, hipY + 200f),
            ),
        ),
        sourceWidth = FRAME_WIDTH,
        sourceHeight = FRAME_HEIGHT,
        timestampMillis = 0L,
    )
}

/** 0.194 of the frame — the mean measured over 231 frames of squatting at 2-3 m. */
private val atTrainingDistance = frame(torsoHeight = 124f)

/** Half the frame height, which is the phone in your hand rather than on the floor. */
private val toCloseToMeasure = frame(torsoHeight = 320f)

class SquatDepthAngleTest {

    @Test
    fun `a figure at training distance is measured`() {
        assertEquals(180f, atTrainingDistance.squatDepthAngle()!!, TOLERANCE)
    }

    /**
     * The bug this exists for. The skeleton here is geometrically perfect — the
     * knee angle reads fine, and it would read fine on a hallucinated one too,
     * because `inFrameLikelihood` answers "is this landmark in the picture", not
     * "is this a person". Apparent size is the only thing left to judge on.
     */
    @Test
    fun `a frame too close to be a person yields no reading, not a bad one`() {
        assertNotNull(toCloseToMeasure.pose.squatSignals(timestampMillis = 0L).depthAngle)

        assertNull(toCloseToMeasure.squatDepthAngle())
    }

    /**
     * The same null as a rejected frame on purpose: both are "no reading", which is
     * the one thing [SquatRepCounter] already knows how to abandon a rep from.
     */
    @Test
    fun `a frame with no pose yields no reading`() {
        val empty = PoseFrame(BodyPose.Empty, FRAME_WIDTH, FRAME_HEIGHT, 0L)

        assertNull(empty.squatDepthAngle())
    }

    /**
     * The threshold stays an argument rather than being baked in, so a test states
     * the input and asserts the decision instead of asserting the constant against
     * itself.
     */
    @Test
    fun `the framing threshold is the caller's to supply`() {
        assertNull(atTrainingDistance.squatDepthAngle(maxTorsoFraction = 0.1f))
        assertNotNull(toCloseToMeasure.squatDepthAngle(maxTorsoFraction = 0.9f))
    }

    /**
     * Legs the detector is unsure of are dropped by [squatSignals] before framing
     * ever comes into it — a well-framed shot of someone side-on still has to yield
     * nothing rather than an angle blended from a guess.
     */
    @Test
    fun `an unconfident reading is still no reading in a well-framed shot`() {
        assertEquals(Framing.OK, atTrainingDistance.framing())

        assertNull(atTrainingDistance.squatDepthAngle(minLikelihood = 1.1f))
    }
}
