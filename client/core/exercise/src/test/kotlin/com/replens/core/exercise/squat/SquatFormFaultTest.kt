package com.replens.core.exercise.squat

import com.replens.core.exercise.AbandonedDescent
import com.replens.core.exercise.FormFault
import com.replens.core.exercise.Rep
import com.replens.core.exercise.RepPhase
import com.replens.core.exercise.RepUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

private val config = SquatRepConfig()

private fun completed(deepestAngle: Float) = RepUpdate(
    phase = RepPhase.STANDING,
    completedRep = Rep(
        index = 1,
        deepestAngle = deepestAngle,
        descent = 500.milliseconds,
        ascent = 500.milliseconds,
    ),
)

private fun abandoned(deepestAngle: Float) = RepUpdate(
    phase = RepPhase.STANDING,
    abandonedDescent = AbandonedDescent(
        deepestAngle = deepestAngle,
        total = 800.milliseconds,
    ),
)

class SquatFormFaultTest {

    @Test
    fun `most frames are just movement`() {
        assertNull(RepUpdate(RepPhase.DESCENDING).squatFormFault(config))
    }

    @Test
    fun `a rep at depth is not a fault`() {
        assertNull(completed(deepestAngle = config.goodDepthAngle).squatFormFault(config))
    }

    /**
     * The gap between the two thresholds is the entire feature: this rep counted,
     * and is still worth a word. Grading it against `bottomEnterAngle` instead
     * would make the fault unreachable, since nothing shallower ever counts.
     */
    @Test
    fun `a rep that counted but missed parallel is shallow`() {
        assertEquals(
            FormFault.SHALLOW_REP,
            completed(deepestAngle = config.bottomEnterAngle).squatFormFault(config),
        )
    }

    @Test
    fun `a descent that turned back is its own fault`() {
        assertEquals(
            FormFault.ABANDONED_DESCENT,
            abandoned(deepestAngle = 140f).squatFormFault(config),
        )
    }

    /**
     * Not a real collision — the counter reports a completed rep from ASCENDING
     * and an abandoned descent from DESCENDING, so one frame cannot produce both.
     * Pinned anyway, because the precedence is the only thing the `when` ordering
     * says out loud and the next fault added will be read against it.
     */
    @Test
    fun `the abandoned descent wins if both ever appear together`() {
        val both = RepUpdate(
            phase = RepPhase.STANDING,
            completedRep = completed(deepestAngle = 130f).completedRep,
            abandonedDescent = abandoned(deepestAngle = 140f).abandonedDescent,
        )

        assertEquals(FormFault.ABANDONED_DESCENT, both.squatFormFault(config))
    }
}
