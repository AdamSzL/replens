package com.replens.core.exercise

import com.replens.core.model.BodyPose
import com.replens.core.model.Landmark
import com.replens.core.model.LandmarkType
import com.replens.core.model.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val FRAME_WIDTH = 480
private const val FRAME_HEIGHT = 640

private fun landmark(type: LandmarkType, x: Float, y: Float) =
    Landmark(type = type, x = x, y = y, z = 0f, inFrameLikelihood = 1f)

/** Shoulders and hips only — [torsoFraction] needs nothing else. */
private fun frameWithTorso(heightPx: Float, shoulderY: Float = 100f) = PoseFrame(
    pose = BodyPose(
        listOf(
            landmark(LandmarkType.LEFT_SHOULDER, 260f, shoulderY),
            landmark(LandmarkType.RIGHT_SHOULDER, 220f, shoulderY),
            landmark(LandmarkType.LEFT_HIP, 260f, shoulderY + heightPx),
            landmark(LandmarkType.RIGHT_HIP, 220f, shoulderY + heightPx),
        )
    ),
    sourceWidth = FRAME_WIDTH,
    sourceHeight = FRAME_HEIGHT,
    timestampMillis = 0L,
)

class FramingTest {

    @Test
    fun `torso fraction divides by frame height, not width`() {
        assertEquals(0.25f, frameWithTorso(heightPx = 160f).torsoFraction()!!, 0.001f)
    }

    @Test
    fun `a pose without a torso has no fraction`() {
        val frame = PoseFrame(BodyPose.Empty, FRAME_WIDTH, FRAME_HEIGHT, 0L)
        assertNull(frame.torsoFraction())
        assertEquals(Framing.NO_POSE, frame.framing())
    }

    @Test
    fun `a figure standing at training distance reads OK`() {
        // 0.193 — the mean measured across 231 frames of squatting at 2-3 m.
        assertEquals(Framing.OK, frameWithTorso(heightPx = 124f).framing())
    }

    /** A body filling the frame head to toe is tight framing, not a close-up. */
    @Test
    fun `a full-height figure still reads OK`() {
        assertEquals(Framing.OK, frameWithTorso(heightPx = FRAME_HEIGHT * 0.29f).framing())
    }

    @Test
    fun `a torso filling half the frame is too close`() {
        assertEquals(Framing.TOO_CLOSE, frameWithTorso(heightPx = 320f).framing())
    }

    @Test
    fun `the threshold itself is not too close`() {
        val atThreshold = FRAME_HEIGHT * Framing.MAX_TORSO_FRACTION
        assertEquals(Framing.OK, frameWithTorso(heightPx = atThreshold).framing())
    }

    /**
     * The fictional skeleton is the whole reason this exists, and it arrives at
     * full confidence — so the check must not be expressible as a likelihood
     * gate, and this fixes that it isn't.
     */
    @Test
    fun `a confident close-up skeleton is still rejected`() {
        val frame = frameWithTorso(heightPx = 400f, shoulderY = 20f)
        assertEquals(1f, frame.pose[LandmarkType.LEFT_HIP]!!.inFrameLikelihood, 0f)
        assertEquals(Framing.TOO_CLOSE, frame.framing())
    }
}
