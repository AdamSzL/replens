package com.replens.core.posemath

import com.replens.core.model.BodyPose
import com.replens.core.model.Landmark
import com.replens.core.model.LandmarkType
import com.replens.core.model.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

private const val FRAME_INTERVAL_MILLIS = 33L
private const val SOURCE_WIDTH = 480
private const val SOURCE_HEIGHT = 640

private fun frameOf(
    timestampMillis: Long,
    vararg landmarks: Landmark,
) = PoseFrame(
    pose = BodyPose(landmarks.toList()),
    sourceWidth = SOURCE_WIDTH,
    sourceHeight = SOURCE_HEIGHT,
    timestampMillis = timestampMillis,
)

private fun knee(x: Float, y: Float, z: Float = 0f, likelihood: Float = 1f) =
    Landmark(LandmarkType.LEFT_KNEE, x, y, z, likelihood)

class PoseSmootherTest {

    @Test
    fun `the first frame passes through unchanged`() {
        val frame = frameOf(0L, knee(x = 120f, y = 300f))
        val smoothed = PoseSmoother().smooth(frame)

        val landmark = smoothed.pose[LandmarkType.LEFT_KNEE]!!
        assertEquals(120f, landmark.x, 0.0001f)
        assertEquals(300f, landmark.y, 0.0001f)
    }

    @Test
    fun `jitter around a stationary landmark is attenuated`() {
        val random = Random(seed = 11)
        val smoother = PoseSmoother()
        val trueX = 200f
        val trueY = 400f

        var rawError = 0f
        var smoothedError = 0f
        val samples = 150
        repeat(samples) { i ->
            val noisyX = trueX + (random.nextFloat() - 0.5f) * 12f
            val noisyY = trueY + (random.nextFloat() - 0.5f) * 12f
            val result = smoother.smooth(
                frameOf(i * FRAME_INTERVAL_MILLIS, knee(noisyX, noisyY))
            )
            if (i >= 20) {
                val landmark = result.pose[LandmarkType.LEFT_KNEE]!!
                rawError += abs(noisyX - trueX) + abs(noisyY - trueY)
                smoothedError += abs(landmark.x - trueX) + abs(landmark.y - trueY)
            }
        }

        assertTrue(
            "expected smoothing to cut jitter, was $rawError -> $smoothedError",
            smoothedError < rawError / 2f,
        )
    }

    @Test
    fun `every landmark keeps its own filter`() {
        val smoother = PoseSmoother()
        repeat(30) { i ->
            smoother.smooth(
                frameOf(
                    i * FRAME_INTERVAL_MILLIS,
                    Landmark(LandmarkType.LEFT_KNEE, 100f, 100f, 0f, 1f),
                    Landmark(LandmarkType.RIGHT_KNEE, 300f, 300f, 0f, 1f),
                )
            )
        }
        val result = smoother.smooth(
            frameOf(
                30 * FRAME_INTERVAL_MILLIS,
                Landmark(LandmarkType.LEFT_KNEE, 100f, 100f, 0f, 1f),
                Landmark(LandmarkType.RIGHT_KNEE, 300f, 300f, 0f, 1f),
            )
        )

        assertEquals(100f, result.pose[LandmarkType.LEFT_KNEE]!!.x, 0.5f)
        assertEquals(300f, result.pose[LandmarkType.RIGHT_KNEE]!!.x, 0.5f)
    }

    @Test
    fun `frame metadata and untouched landmark fields survive`() {
        val smoother = PoseSmoother()
        val frame = frameOf(1234L, knee(x = 10f, y = 20f, z = -7.5f, likelihood = 0.42f))
        val smoothed = smoother.smooth(frame)

        assertEquals(SOURCE_WIDTH, smoothed.sourceWidth)
        assertEquals(SOURCE_HEIGHT, smoothed.sourceHeight)
        assertEquals(1234L, smoothed.timestampMillis)

        val landmark = smoothed.pose[LandmarkType.LEFT_KNEE]!!
        assertEquals(LandmarkType.LEFT_KNEE, landmark.type)
        assertEquals(-7.5f, landmark.z, 0.0001f)
        assertEquals(0.42f, landmark.inFrameLikelihood, 0.0001f)
    }

    @Test
    fun `a long gap resets rather than gliding from the old position`() {
        val smoother = PoseSmoother(resetAfterGapMillis = 500L)
        repeat(30) { i ->
            smoother.smooth(frameOf(i * FRAME_INTERVAL_MILLIS, knee(x = 50f, y = 50f)))
        }

        val afterGap = smoother.smooth(
            frameOf(30 * FRAME_INTERVAL_MILLIS + 5_000L, knee(x = 400f, y = 400f))
        )

        val landmark = afterGap.pose[LandmarkType.LEFT_KNEE]!!
        assertEquals(400f, landmark.x, 0.0001f)
        assertEquals(400f, landmark.y, 0.0001f)
    }

    @Test
    fun `a normal frame interval does not reset`() {
        // beta = 0 so this tests only that state survived; with adaptation on, a
        // jump this large is legitimately tracked rather than damped.
        val smoother = PoseSmoother(beta = 0f, resetAfterGapMillis = 500L)
        repeat(30) { i ->
            smoother.smooth(frameOf(i * FRAME_INTERVAL_MILLIS, knee(x = 50f, y = 50f)))
        }

        val next = smoother.smooth(
            frameOf(30 * FRAME_INTERVAL_MILLIS, knee(x = 400f, y = 400f))
        )

        val landmark = next.pose[LandmarkType.LEFT_KNEE]!!
        assertTrue(
            "should still be anchored near the old position, was ${landmark.x}",
            landmark.x < 200f,
        )
    }

    @Test
    fun `reset clears state`() {
        val smoother = PoseSmoother()
        repeat(30) { i ->
            smoother.smooth(frameOf(i * FRAME_INTERVAL_MILLIS, knee(x = 50f, y = 50f)))
        }
        smoother.reset()

        val landmark = smoother
            .smooth(frameOf(30 * FRAME_INTERVAL_MILLIS, knee(x = 400f, y = 400f)))
            .pose[LandmarkType.LEFT_KNEE]!!
        assertEquals(400f, landmark.x, 0.0001f)
    }

    @Test
    fun `landmarks appearing mid-stream are not dragged from elsewhere`() {
        val smoother = PoseSmoother()
        repeat(30) { i ->
            smoother.smooth(frameOf(i * FRAME_INTERVAL_MILLIS, knee(x = 50f, y = 50f)))
        }

        val withNewLandmark = smoother.smooth(
            frameOf(
                30 * FRAME_INTERVAL_MILLIS,
                knee(x = 50f, y = 50f),
                Landmark(LandmarkType.LEFT_ANKLE, 350f, 600f, 0f, 1f),
            )
        )

        val ankle = withNewLandmark.pose[LandmarkType.LEFT_ANKLE]
        assertNotNull(ankle)
        assertEquals(350f, ankle!!.x, 0.0001f)
        assertEquals(600f, ankle.y, 0.0001f)
    }

    @Test
    fun `smoothing is isotropic - equal jitter on both axes is damped equally`() {
        val smoother = PoseSmoother()
        // Same coordinate on both axes, moved by the same amount each frame.
        repeat(20) { i -> smoother.smooth(frameOf(i * FRAME_INTERVAL_MILLIS, knee(100f, 100f))) }

        val stepped = smoother
            .smooth(frameOf(20 * FRAME_INTERVAL_MILLIS, knee(150f, 150f)))
            .pose[LandmarkType.LEFT_KNEE]!!

        assertEquals(
            "x and y must be smoothed identically despite width != height",
            stepped.x,
            stepped.y,
            0.0001f,
        )
    }

    @Test
    fun `a zero width frame is passed through rather than producing infinities`() {
        val smoother = PoseSmoother()
        val broken = PoseFrame(
            pose = BodyPose(listOf(knee(10f, 20f))),
            sourceWidth = 0,
            sourceHeight = 0,
            timestampMillis = 0L,
        )

        val landmark = smoother.smooth(broken).pose[LandmarkType.LEFT_KNEE]!!
        assertEquals(10f, landmark.x, 0.0001f)
        assertEquals(20f, landmark.y, 0.0001f)
    }
}
