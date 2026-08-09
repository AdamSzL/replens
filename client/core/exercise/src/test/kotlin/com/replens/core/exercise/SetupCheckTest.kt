package com.replens.core.exercise

import com.replens.core.model.BodyPose
import com.replens.core.model.Landmark
import com.replens.core.model.LandmarkType
import com.replens.core.model.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Test

private const val FRAME_WIDTH = 480
private const val FRAME_HEIGHT = 640

private fun landmark(
    type: LandmarkType,
    x: Float,
    y: Float,
    likelihood: Float = 1f,
) = Landmark(type = type, x = x, y = y, z = 0f, inFrameLikelihood = likelihood)

/**
 * A figure whose torso spans [torsoPx], with both legs below it. Ankle position
 * and confidence are the levers the tests pull.
 */
private fun standingFrame(
    torsoPx: Float = 120f,
    ankleY: Float = 600f,
    ankleLikelihood: Float = 1f,
) = PoseFrame(
    pose = BodyPose(
        listOf(
            landmark(LandmarkType.LEFT_SHOULDER, 260f, 100f),
            landmark(LandmarkType.RIGHT_SHOULDER, 220f, 100f),
            landmark(LandmarkType.LEFT_HIP, 260f, 100f + torsoPx),
            landmark(LandmarkType.RIGHT_HIP, 220f, 100f + torsoPx),
            landmark(LandmarkType.LEFT_KNEE, 260f, 100f + torsoPx + 120f),
            landmark(LandmarkType.RIGHT_KNEE, 220f, 100f + torsoPx + 120f),
            landmark(LandmarkType.LEFT_ANKLE, 260f, ankleY, ankleLikelihood),
            landmark(LandmarkType.RIGHT_ANKLE, 220f, ankleY, ankleLikelihood),
        )
    ),
    sourceWidth = FRAME_WIDTH,
    sourceHeight = FRAME_HEIGHT,
    timestampMillis = 0L,
)

class SetupCheckTest {

    @Test
    fun `a framed figure with both legs visible is ready`() {
        assertEquals(SetupCheck.READY, standingFrame().setupCheck())
    }

    @Test
    fun `no pose at all is not ready`() {
        val empty = PoseFrame(BodyPose.Empty, FRAME_WIDTH, FRAME_HEIGHT, 0L)
        assertEquals(SetupCheck.NO_POSE, empty.setupCheck())
    }

    @Test
    fun `leaning in over the phone is too close`() {
        // 0.34 of frame height — the reading measured while leaning toward the
        // phone, which the lenient Framing gate lets through.
        val leaningIn = standingFrame(torsoPx = FRAME_HEIGHT * 0.34f)
        assertEquals(Framing.OK, leaningIn.framing())
        assertEquals(SetupCheck.TOO_CLOSE, leaningIn.setupCheck())
    }

    @Test
    fun `an ankle below the bottom edge is not observed`() {
        val feetCutOff = standingFrame(ankleY = FRAME_HEIGHT + 40f)
        assertEquals(SetupCheck.LEGS_NOT_VISIBLE, feetCutOff.setupCheck())
    }

    @Test
    fun `an unconfident ankle is not observed`() {
        val guessedFeet = standingFrame(ankleLikelihood = 0.2f)
        assertEquals(SetupCheck.LEGS_NOT_VISIBLE, guessedFeet.setupCheck())
    }

    /** 45-degree framing occludes the far side by design, so one leg is enough. */
    @Test
    fun `one visible leg is enough`() {
        val frame = standingFrame()
        val nearSideOnly = BodyPose(
            frame.pose.all.filterNot { it.type == LandmarkType.RIGHT_ANKLE }
        )
        assertEquals(SetupCheck.READY, frame.copy(pose = nearSideOnly).setupCheck())
    }

    /**
     * The two checks answer different questions and must not collapse into one:
     * a frame can be trustworthy mid-set while being a bad place to begin.
     */
    @Test
    fun `the trust gate stays lenient where the setup check refuses`() {
        val feetCutOff = standingFrame(ankleY = FRAME_HEIGHT + 40f)
        assertEquals(Framing.OK, feetCutOff.framing())
        assertEquals(SetupCheck.LEGS_NOT_VISIBLE, feetCutOff.setupCheck())
    }
}
