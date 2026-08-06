package com.replens.core.posemath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

private const val FRAME_INTERVAL_MILLIS = 33L

/** Mean squared distance from [target] — how much a signal wobbles around truth. */
private fun List<Float>.errorAgainst(target: Float): Float =
    map { (it - target) * (it - target) }.average().toFloat()

class OneEuroFilterTest {

    @Test
    fun `the first sample passes through untouched`() {
        val filter = OneEuroFilter()
        assertEquals(42f, filter.filter(42f, timestampMillis = 0L), 0.0001f)
    }

    @Test
    fun `a clean constant signal stays constant`() {
        val filter = OneEuroFilter()
        var last = 0f
        repeat(50) { i ->
            last = filter.filter(100f, timestampMillis = i * FRAME_INTERVAL_MILLIS)
        }
        assertEquals(100f, last, 0.0001f)
    }

    @Test
    fun `noise around a constant is strongly attenuated`() {
        val random = Random(seed = 1)
        val filter = OneEuroFilter(minCutoff = 1f, beta = 0f)
        val truth = 100f

        val noisy = mutableListOf<Float>()
        val smoothed = mutableListOf<Float>()
        repeat(200) { i ->
            val sample = truth + (random.nextFloat() - 0.5f) * 20f
            noisy += sample
            smoothed += filter.filter(sample, timestampMillis = i * FRAME_INTERVAL_MILLIS)
        }

        // Ignore the warm-up, where the filter is still seeded by the first sample.
        val noisyError = noisy.drop(20).errorAgainst(truth)
        val smoothedError = smoothed.drop(20).errorAgainst(truth)

        assertTrue(
            "expected smoothing to cut error by 5x, was $noisyError -> $smoothedError",
            smoothedError < noisyError / 5f,
        )
    }

    @Test
    fun `a step is approached gradually and never overshot`() {
        val filter = OneEuroFilter()
        filter.filter(0f, timestampMillis = 0L)

        val outputs = (1..30).map { i ->
            filter.filter(100f, timestampMillis = i * FRAME_INTERVAL_MILLIS)
        }

        assertTrue("first response should lag the step", outputs.first() < 100f)
        assertTrue("should converge on the new value", outputs.last() > 95f)
        assertTrue("must not overshoot", outputs.all { it <= 100f })
        assertTrue(
            "should approach monotonically",
            outputs.zipWithNext().all { (a, b) -> b >= a },
        )
    }

    @Test
    fun `raising beta reduces lag on a fast ramp`() {
        fun trailingErrorOnRamp(beta: Float): Float {
            val filter = OneEuroFilter(minCutoff = 1f, beta = beta)
            var error = 0f
            repeat(40) { i ->
                val truth = i * 20f // fast, steady movement
                error = abs(filter.filter(truth, timestampMillis = i * FRAME_INTERVAL_MILLIS) - truth)
            }
            return error
        }

        val lowBeta = trailingErrorOnRamp(beta = 0f)
        val highBeta = trailingErrorOnRamp(beta = 2f)

        assertTrue(
            "higher beta should track faster, was $lowBeta -> $highBeta",
            highBeta < lowBeta,
        )
    }

    @Test
    fun `lowering minCutoff smooths harder at rest`() {
        fun errorOnNoisyConstant(minCutoff: Float): Float {
            val random = Random(seed = 7)
            val filter = OneEuroFilter(minCutoff = minCutoff, beta = 0f)
            return (0 until 150)
                .map { i ->
                    val sample = 50f + (random.nextFloat() - 0.5f) * 10f
                    filter.filter(sample, timestampMillis = i * FRAME_INTERVAL_MILLIS)
                }
                .drop(20)
                .errorAgainst(50f)
        }

        assertTrue(errorOnNoisyConstant(minCutoff = 0.3f) < errorOnNoisyConstant(minCutoff = 5f))
    }

    @Test
    fun `irregular sampling intervals do not disturb a constant signal`() {
        val filter = OneEuroFilter()
        val jitteryIntervals = listOf(33L, 12L, 51L, 8L, 70L, 30L, 44L, 19L)

        var timestamp = 0L
        var last = 0f
        repeat(6) {
            jitteryIntervals.forEach { step ->
                timestamp += step
                last = filter.filter(75f, timestamp)
            }
        }

        assertEquals(75f, last, 0.0001f)
    }

    @Test
    fun `a repeated timestamp returns the previous estimate`() {
        val filter = OneEuroFilter()
        filter.filter(10f, timestampMillis = 0L)
        val first = filter.filter(20f, timestampMillis = FRAME_INTERVAL_MILLIS)
        val repeated = filter.filter(999f, timestampMillis = FRAME_INTERVAL_MILLIS)

        assertEquals(first, repeated, 0.0001f)
    }

    @Test
    fun `a timestamp going backwards returns the previous estimate`() {
        val filter = OneEuroFilter()
        filter.filter(10f, timestampMillis = 1000L)
        val forward = filter.filter(20f, timestampMillis = 1033L)
        val backward = filter.filter(999f, timestampMillis = 500L)

        assertEquals(forward, backward, 0.0001f)
    }

    @Test
    fun `reset makes the next sample pass through again`() {
        // beta = 0 pins the cutoff, so the contrast is purely about filter state.
        // With adaptation on, a jump this large reads as fast motion and is
        // tracked almost fully — see `raising beta reduces lag on a fast ramp`.
        val filter = OneEuroFilter(beta = 0f)
        repeat(20) { i -> filter.filter(0f, timestampMillis = i * FRAME_INTERVAL_MILLIS) }

        val withoutReset = filter.filter(500f, timestampMillis = 20 * FRAME_INTERVAL_MILLIS)
        assertTrue(
            "should still be anchored near the old value, was $withoutReset",
            withoutReset < 200f,
        )

        filter.reset()
        val afterReset = filter.filter(500f, timestampMillis = 21 * FRAME_INTERVAL_MILLIS)
        assertEquals(500f, afterReset, 0.0001f)
    }

    @Test
    fun `a large jump is tracked when adaptation is on`() {
        val adaptive = OneEuroFilter(minCutoff = 1f, beta = 0.5f)
        repeat(20) { i -> adaptive.filter(0f, timestampMillis = i * FRAME_INTERVAL_MILLIS) }
        val tracked = adaptive.filter(500f, timestampMillis = 20 * FRAME_INTERVAL_MILLIS)

        val fixed = OneEuroFilter(minCutoff = 1f, beta = 0f)
        repeat(20) { i -> fixed.filter(0f, timestampMillis = i * FRAME_INTERVAL_MILLIS) }
        val lagged = fixed.filter(500f, timestampMillis = 20 * FRAME_INTERVAL_MILLIS)

        assertTrue("adaptation should track the jump, was $tracked vs $lagged", tracked > lagged)
    }

    @Test
    fun `output stays between the previous estimate and the new sample`() {
        val random = Random(seed = 3)
        val filter = OneEuroFilter()
        var previous = filter.filter(0f, timestampMillis = 0L)

        repeat(100) { i ->
            val sample = random.nextFloat() * 200f - 100f
            val output = filter.filter(sample, timestampMillis = (i + 1) * FRAME_INTERVAL_MILLIS)
            val low = minOf(previous, sample)
            val high = maxOf(previous, sample)
            assertTrue(
                "output $output escaped the interval [$low, $high]",
                output >= low - 0.0001f && output <= high + 0.0001f,
            )
            previous = output
        }
    }
}
