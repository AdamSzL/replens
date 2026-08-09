package com.replens.core.exercise.squat

import com.replens.core.exercise.Rep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val config = SquatRepConfig()

private fun rep(deepestAngle: Float) = Rep(
    index = 1,
    deepestAngle = deepestAngle,
    startedAtMillis = 0L,
    bottomAtMillis = 500L,
    completedAtMillis = 1_000L,
)

class RepDepthTest {

    @Test
    fun `parallel is at depth`() {
        assertTrue(rep(deepestAngle = 95f).isAtDepth(config))
    }

    @Test
    fun `below parallel is at depth`() {
        assertTrue(rep(deepestAngle = 60f).isAtDepth(config))
    }

    /**
     * The gap this reports: a rep at the counting threshold is real — it counted —
     * but 115 degrees is nowhere near parallel.
     */
    @Test
    fun `a rep that only just counted is not at depth`() {
        assertFalse(rep(deepestAngle = config.bottomEnterAngle).isAtDepth(config))
    }
}
