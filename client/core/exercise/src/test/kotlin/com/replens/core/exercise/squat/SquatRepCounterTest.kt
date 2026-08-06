package com.replens.core.exercise.squat

import com.replens.core.exercise.Rep
import com.replens.core.exercise.RepPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FRAME_INTERVAL_MILLIS = 33L
private const val STANDING_ANGLE = 175f

/** Frames of standing still. */
private fun standing(frames: Int = 5) = List(frames) { STANDING_ANGLE }

/**
 * A squat as the knee angle would trace it: down to [bottomAngle], hold, back up.
 * Default is ~1.2 s at 30 fps, which is a realistic tempo.
 */
private fun squat(
    bottomAngle: Float = 95f,
    descentFrames: Int = 15,
    bottomFrames: Int = 5,
    ascentFrames: Int = 15,
): List<Float> = buildList {
    repeat(descentFrames) { i ->
        add(STANDING_ANGLE + (bottomAngle - STANDING_ANGLE) * (i + 1) / descentFrames)
    }
    repeat(bottomFrames) { add(bottomAngle) }
    repeat(ascentFrames) { i ->
        add(bottomAngle + (STANDING_ANGLE - bottomAngle) * (i + 1) / ascentFrames)
    }
}

private class Session(val counter: SquatRepCounter = SquatRepCounter()) {
    private var timestamp = 0L
    val completed = mutableListOf<Rep>()
    val phases = mutableListOf<RepPhase>()

    fun feed(angles: List<Float?>): Session {
        angles.forEach { angle ->
            val update = counter.update(angle, timestamp)
            update.completedRep?.let(completed::add)
            phases += update.phase
            timestamp += FRAME_INTERVAL_MILLIS
        }
        return this
    }
}

class SquatRepCounterTest {

    @Test
    fun `a clean rep is counted once`() {
        val session = Session().feed(standing() + squat() + standing())

        assertEquals(1, session.counter.repCount)
        assertEquals(1, session.completed.size)
        assertEquals(RepPhase.STANDING, session.counter.phase)
    }

    @Test
    fun `five reps are counted as five`() {
        val session = Session().feed(standing() + List(5) { squat() }.flatten() + standing())

        assertEquals(5, session.counter.repCount)
        assertEquals(listOf(1, 2, 3, 4, 5), session.completed.map(Rep::index))
    }

    @Test
    fun `a half rep that never reaches depth is not counted`() {
        // Bottoms out at 130, above the 115 threshold.
        val session = Session().feed(standing() + squat(bottomAngle = 130f) + standing())

        assertEquals(0, session.counter.repCount)
        assertEquals(RepPhase.STANDING, session.counter.phase)
    }

    @Test
    fun `jitter across the depth threshold still counts one rep`() {
        val chatter = listOf(116f, 114f, 116f, 113f, 117f, 114f, 116f, 113f)
        val session = Session().feed(
            standing() + squat(bottomAngle = 115f, bottomFrames = 0) + chatter +
                squat(bottomAngle = 115f, descentFrames = 0, bottomFrames = 0) + standing()
        )

        assertEquals(1, session.counter.repCount)
    }

    @Test
    fun `pausing at the bottom still counts one rep`() {
        val session = Session().feed(standing() + squat(bottomFrames = 90) + standing())

        assertEquals(1, session.counter.repCount)
    }

    @Test
    fun `bouncing out of the bottom still counts one rep`() {
        val session = Session().feed(
            standing() +
                squat(bottomAngle = 95f, ascentFrames = 0) +
                listOf(130f, 140f, 130f, 110f, 100f, 110f, 130f) + // up, dip, up again
                squat(bottomAngle = 130f, descentFrames = 0, bottomFrames = 0) +
                standing()
        )

        assertEquals(1, session.counter.repCount)
    }

    @Test
    fun `losing tracking mid-rep abandons it`() {
        val descent = squat().take(20) // down and holding at the bottom
        val session = Session().feed(
            standing() + descent + List(12) { null } + standing(10)
        )

        assertEquals(0, session.counter.repCount)
        assertEquals(RepPhase.STANDING, session.counter.phase)
    }

    @Test
    fun `a brief tracking gap does not abandon the rep`() {
        val descent = squat().take(20)
        val ascent = squat().drop(20)
        val session = Session().feed(
            standing() + descent + List(3) { null } + ascent + standing()
        )

        assertEquals(1, session.counter.repCount)
    }

    @Test
    fun `starting mid-squat does not count the way up`() {
        val session = Session().feed(
            listOf(100f, 105f, 120f, 140f, 160f, 172f, 175f) + standing()
        )

        assertEquals(0, session.counter.repCount)
    }

    @Test
    fun `a rep after starting mid-squat is counted normally`() {
        val session = Session().feed(
            listOf(100f, 120f, 150f, 172f) + standing() + squat() + standing()
        )

        assertEquals(1, session.counter.repCount)
    }

    @Test
    fun `an implausibly fast rep is rejected`() {
        // Full range in 6 frames = 198 ms, below the 400 ms floor.
        val spike = listOf(175f, 150f, 110f, 90f, 120f, 175f)
        val session = Session().feed(standing() + spike + standing())

        assertEquals(0, session.counter.repCount)
    }

    @Test
    fun `phases follow the expected order`() {
        val session = Session().feed(standing() + squat() + standing())

        val distinctPhases = session.phases.fold(mutableListOf<RepPhase>()) { acc, phase ->
            if (acc.lastOrNull() != phase) acc += phase
            acc
        }
        assertEquals(
            listOf(
                RepPhase.STANDING,
                RepPhase.DESCENDING,
                RepPhase.BOTTOM,
                RepPhase.ASCENDING,
                RepPhase.STANDING,
            ),
            distinctPhases,
        )
    }

    @Test
    fun `the completed rep records how deep it went`() {
        val session = Session().feed(standing() + squat(bottomAngle = 88f) + standing())

        val rep = session.completed.single()
        assertEquals(88f, rep.deepestAngle, 0.001f)
    }

    @Test
    fun `depth is recorded even when it is shallow, because counting is lenient`() {
        val session = Session().feed(standing() + squat(bottomAngle = 112f) + standing())

        val rep = session.completed.single()
        assertEquals(1, session.counter.repCount)
        assertTrue(
            "a shallow rep should still count, and be judged by its depth",
            rep.deepestAngle > SquatRepConfig().goodDepthAngle,
        )
    }

    @Test
    fun `the completed rep records its timings`() {
        val session = Session().feed(standing() + squat() + standing())

        val rep = session.completed.single()
        assertTrue("descent should take time", rep.descentMillis > 0)
        assertTrue("ascent should take time", rep.ascentMillis > 0)
        assertEquals(rep.totalMillis, rep.descentMillis + rep.ascentMillis)
    }

    @Test
    fun `a slow rep and a fast rep both count once`() {
        val slow = Session().feed(
            standing() + squat(descentFrames = 60, bottomFrames = 30, ascentFrames = 60)
        )
        val fast = Session().feed(
            standing() + squat(descentFrames = 8, bottomFrames = 2, ascentFrames = 8) + standing()
        )

        assertEquals(1, slow.counter.repCount)
        assertEquals(1, fast.counter.repCount)
    }

    @Test
    fun `no rep is emitted while one is still in progress`() {
        val session = Session().feed(standing() + squat().take(20))

        assertTrue(session.completed.isEmpty())
        assertEquals(0, session.counter.repCount)
        assertTrue(
            "should still be mid-rep, was ${session.counter.phase}",
            session.counter.phase != RepPhase.STANDING,
        )
    }

    @Test
    fun `reset clears the count and the rep in progress`() {
        val session = Session().feed(standing() + squat() + standing())
        assertEquals(1, session.counter.repCount)

        session.counter.reset()

        assertEquals(0, session.counter.repCount)
        assertEquals(RepPhase.STANDING, session.counter.phase)
        // Standing has to be re-established, so a bare ascent counts for nothing.
        Session(session.counter).feed(listOf(100f, 130f, 160f, 175f))
        assertEquals(0, session.counter.repCount)
    }

    @Test
    fun `an update returns the completed rep exactly once`() {
        val session = Session().feed(standing() + squat() + standing(20))

        assertEquals(1, session.completed.size)
        assertNotNull(session.completed.single())
    }

    @Test
    fun `a config without hysteresis is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SquatRepConfig(standingExitAngle = 168f, standingEnterAngle = 160f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SquatRepConfig(bottomEnterAngle = 125f, bottomExitAngle = 115f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SquatRepConfig(standingExitAngle = 120f, bottomExitAngle = 125f)
        }
    }

    @Test
    fun `the default hysteresis bands are wide enough for post-smoothing noise`() {
        val config = SquatRepConfig()
        // Residual noise after One Euro is a few degrees; the cutoff also winds
        // down slowly after a descent, so the bottom is the noisiest moment.
        val bottomBand = config.bottomExitAngle - config.bottomEnterAngle
        val standingBand = config.standingEnterAngle - config.standingExitAngle

        assertTrue("bottom hysteresis $bottomBand is too narrow", bottomBand >= 8f)
        assertTrue("standing hysteresis $standingBand is too narrow", standingBand >= 5f)
    }
}
