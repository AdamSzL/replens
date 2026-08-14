package com.replens.core.exercise.squat

import com.replens.core.model.Rep
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Runs the rep counter over angle curves generated from real recordings by
 * `:core:pose`'s `FixtureGenerator`.
 *
 * One assertion per file, and the number asserted is **what a human actually
 * did** — not what the counter currently produces. A failure here means the
 * thresholds are wrong, not the fixture.
 *
 * The synthetic tests in [SquatRepCounterTest] decide whether the logic is
 * correct; these decide whether it survives real landmark noise.
 */
class SquatFixtureTest {

    @Test
    fun `five deep reps all count`() {
        assertEquals(5, reps("squats_5_deep").size)
    }

    @Test
    fun `five parallel-depth reps all count`() {
        assertEquals(5, reps("squats_5_parallel").size)
    }

    @Test
    fun `small knee bends are not reps`() {
        assertEquals(0, reps("squats_5_tiny").size)
    }

    /**
     * Ten deliberate rep-like movements that all land just above
     * [SquatRepConfig.bottomEnterAngle] — 124 to 141 degrees — in dimmer light
     * than the other clips. Zero is the answer, and the point is that it is
     * *consistently* zero: a threshold this close is exactly where a counter
     * without hysteresis would emit the occasional phantom.
     */
    @Test
    fun `repeated movements just above the threshold produce no phantom reps`() {
        assertEquals(0, reps("squats_10_borderline").size)
    }

    @Test
    fun `pausing at the bottom does not split a rep in two`() {
        assertEquals(3, reps("squats_3_paused").size)
    }

    @Test
    fun `dipping twice without standing up is one rep`() {
        assertEquals(1, reps("squats_1_double_dip").size)
    }

    @Test
    fun `walking in and out of frame produces no phantom reps`() {
        assertEquals(4, reps("squats_4_walk_in_out").size)
    }

    /**
     * Six reps, each shallower than the last, sweeping through
     * [SquatRepConfig.bottomEnterAngle]. Not an assertion about how many *should*
     * count — it exists to show where the threshold falls in real movement, so
     * the depths are what matter.
     */
    @Test
    fun `a descending sweep counts reps down to the threshold`() {
        val counted = reps("squats_6_descending")
        assertEquals(5, counted.size)
        assertEquals(
            listOf(51, 73, 87, 97, 113),
            counted.map { it.deepestAngle.roundToInt() },
        )
    }
}

private fun reps(fixture: String): List<Rep> {
    val counter = SquatRepCounter()
    val completed = mutableListOf<Rep>()
    readFixture(fixture).forEach { (timestampMillis, depthAngle) ->
        counter.update(depthAngle, timestampMillis).completedRep?.let(completed::add)
    }
    return completed
}

/** `timestampMillis to depthAngle`, with null for frames that had no confident reading. */
private fun readFixture(fixture: String): List<Pair<Long, Float?>> {
    val stream = checkNotNull(SquatFixtureTest::class.java.getResourceAsStream("/fixtures/$fixture.csv")) {
        "Missing fixture $fixture.csv — regenerate with :core:pose's FixtureGenerator"
    }
    return stream.bufferedReader().useLines { lines ->
        lines.drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val columns = line.split(',')
                columns[TIMESTAMP_COLUMN].toLong() to columns[DEPTH_ANGLE_COLUMN].toFloatOrNull()
            }
            .toList()
    }
}

// frame,timestampMillis,depthAngle,leftKneeAngle,rightKneeAngle,femurInclination,torsoLeanDegrees
private const val TIMESTAMP_COLUMN = 1
private const val DEPTH_ANGLE_COLUMN = 2
