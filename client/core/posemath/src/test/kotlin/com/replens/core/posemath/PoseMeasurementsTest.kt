package com.replens.core.posemath

import com.replens.core.model.BodyPose
import com.replens.core.model.Landmark
import com.replens.core.model.LandmarkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val TOLERANCE = 0.001f

private fun landmark(type: LandmarkType, x: Float, y: Float) =
    Landmark(type = type, x = x, y = y, z = 0f, inFrameLikelihood = 1f)

private fun poseOf(vararg landmarks: Landmark) = BodyPose(landmarks.toList())

/**
 * A standing figure, y growing downwards: shoulders at y=100, hips at y=200,
 * knees at y=300, ankles at y=400. Torso size is therefore exactly 100.
 */
private fun standingPose(scale: Float = 1f, offsetX: Float = 0f) = poseOf(
    landmark(LandmarkType.LEFT_SHOULDER, (40f + offsetX) * scale, 100f * scale),
    landmark(LandmarkType.RIGHT_SHOULDER, (-40f + offsetX) * scale, 100f * scale),
    landmark(LandmarkType.LEFT_HIP, (20f + offsetX) * scale, 200f * scale),
    landmark(LandmarkType.RIGHT_HIP, (-20f + offsetX) * scale, 200f * scale),
    landmark(LandmarkType.LEFT_KNEE, (20f + offsetX) * scale, 300f * scale),
    landmark(LandmarkType.LEFT_ANKLE, (20f + offsetX) * scale, 400f * scale),
)

class PoseMeasurementsTest {

    @Test
    fun `a straight leg measures 180 degrees`() {
        val angle = standingPose().angleDegrees(
            LandmarkType.LEFT_HIP,
            LandmarkType.LEFT_KNEE,
            LandmarkType.LEFT_ANKLE,
        )
        assertEquals(180f, angle!!, TOLERANCE)
    }

    @Test
    fun `a bent leg measures the knee angle`() {
        // Ankle tucked straight back under the knee: hip above, ankle level.
        val pose = poseOf(
            landmark(LandmarkType.LEFT_HIP, 0f, 200f),
            landmark(LandmarkType.LEFT_KNEE, 0f, 300f),
            landmark(LandmarkType.LEFT_ANKLE, 100f, 300f),
        )
        val angle = pose.angleDegrees(
            LandmarkType.LEFT_HIP,
            LandmarkType.LEFT_KNEE,
            LandmarkType.LEFT_ANKLE,
        )
        assertEquals(90f, angle!!, TOLERANCE)
    }

    @Test
    fun `angle is null when any landmark is missing`() {
        val noAnkle = poseOf(
            landmark(LandmarkType.LEFT_HIP, 0f, 200f),
            landmark(LandmarkType.LEFT_KNEE, 0f, 300f),
        )
        assertNull(
            noAnkle.angleDegrees(
                LandmarkType.LEFT_HIP,
                LandmarkType.LEFT_KNEE,
                LandmarkType.LEFT_ANKLE,
            )
        )
    }

    @Test
    fun `torso size is the hip to shoulder distance`() {
        assertEquals(100f, standingPose().torsoSize()!!, TOLERANCE)
    }

    @Test
    fun `torso size scales with the pose`() {
        assertEquals(250f, standingPose(scale = 2.5f).torsoSize()!!, TOLERANCE)
    }

    @Test
    fun `torso size is null without both shoulders`() {
        val oneShoulder = poseOf(
            landmark(LandmarkType.LEFT_SHOULDER, 40f, 100f),
            landmark(LandmarkType.LEFT_HIP, 20f, 200f),
            landmark(LandmarkType.RIGHT_HIP, -20f, 200f),
        )
        assertNull(oneShoulder.torsoSize())
    }

    @Test
    fun `an upright torso leans zero degrees`() {
        assertEquals(0f, standingPose().torsoLeanDegrees()!!, TOLERANCE)
    }

    @Test
    fun `leaning forward increases the torso angle`() {
        // Shoulders pushed forward of the hips by exactly the torso height.
        val leaning = poseOf(
            landmark(LandmarkType.LEFT_SHOULDER, 140f, 100f),
            landmark(LandmarkType.RIGHT_SHOULDER, 60f, 100f),
            landmark(LandmarkType.LEFT_HIP, 20f, 200f),
            landmark(LandmarkType.RIGHT_HIP, -20f, 200f),
        )
        assertEquals(45f, leaning.torsoLeanDegrees()!!, TOLERANCE)
    }

    /** The point of normalization: identical geometry at any distance or size. */
    @Test
    fun `normalized distance is unchanged by scaling the pose`() {
        val near = standingPose(scale = 1f)
            .distanceNormalized(LandmarkType.LEFT_HIP, LandmarkType.LEFT_KNEE)!!
        val far = standingPose(scale = 0.35f)
            .distanceNormalized(LandmarkType.LEFT_HIP, LandmarkType.LEFT_KNEE)!!

        assertEquals(near, far, TOLERANCE)
        // Hip to knee is 100px against a 100px torso.
        assertEquals(1f, near, TOLERANCE)
    }

    @Test
    fun `normalized distance is unchanged by moving the pose across the frame`() {
        val centered = standingPose()
            .distanceNormalized(LandmarkType.LEFT_HIP, LandmarkType.LEFT_KNEE)!!
        val offset = standingPose(offsetX = 640f)
            .distanceNormalized(LandmarkType.LEFT_HIP, LandmarkType.LEFT_KNEE)!!

        assertEquals(centered, offset, TOLERANCE)
    }

    @Test
    fun `a tracking knee does not deviate from the hip to ankle line`() {
        val deviation = standingPose().deviationFromLineNormalized(
            point = LandmarkType.LEFT_KNEE,
            lineStart = LandmarkType.LEFT_HIP,
            lineEnd = LandmarkType.LEFT_ANKLE,
        )
        assertEquals(0f, deviation!!, TOLERANCE)
    }

    @Test
    fun `a caving knee deviates, and the amount is scale invariant`() {
        fun poseWithKneeInset(scale: Float) = poseOf(
            landmark(LandmarkType.LEFT_SHOULDER, 40f * scale, 100f * scale),
            landmark(LandmarkType.RIGHT_SHOULDER, -40f * scale, 100f * scale),
            landmark(LandmarkType.LEFT_HIP, 20f * scale, 200f * scale),
            landmark(LandmarkType.RIGHT_HIP, -20f * scale, 200f * scale),
            // Knee pulled 25px inwards from the hip->ankle line.
            landmark(LandmarkType.LEFT_KNEE, -5f * scale, 300f * scale),
            landmark(LandmarkType.LEFT_ANKLE, 20f * scale, 400f * scale),
        )

        val near = poseWithKneeInset(1f).deviationFromLineNormalized(
            LandmarkType.LEFT_KNEE, LandmarkType.LEFT_HIP, LandmarkType.LEFT_ANKLE,
        )!!
        val far = poseWithKneeInset(0.2f).deviationFromLineNormalized(
            LandmarkType.LEFT_KNEE, LandmarkType.LEFT_HIP, LandmarkType.LEFT_ANKLE,
        )!!

        assertEquals(near, far, TOLERANCE)
        // 25px off a vertical line, over a 100px torso.
        assertEquals(0.25f, near, TOLERANCE)
    }

    @Test
    fun `measurements needing a torso are null when the torso is unknown`() {
        val legsOnly = poseOf(
            landmark(LandmarkType.LEFT_HIP, 20f, 200f),
            landmark(LandmarkType.LEFT_KNEE, 20f, 300f),
        )
        assertNull(legsOnly.distanceNormalized(LandmarkType.LEFT_HIP, LandmarkType.LEFT_KNEE))
    }
}
