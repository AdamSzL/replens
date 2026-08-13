package com.replens.feature.workout.ui

import com.replens.core.exercise.FormFault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val COOLDOWN_MILLIS = 10_000L

class FormCueSelectorTest {

    private val selector = FormCueSelector()

    @Test
    fun `a clean rep says nothing`() {
        assertNull(selector.onFrame(fault = null, repCount = 1, timestampMillis = 0))
    }

    /**
     * The reason this class exists rather than an elvis in the ViewModel. The fault
     * fires on one frame, but the rep number stays true for every frame until the
     * next rep — so releasing the fault immediately would let "eight" follow "go
     * deeper" one frame later, and QUEUE_FLUSH would cut the correction off.
     */
    @Test
    fun `a fault holds for the whole rep it belongs to`() {
        selector.onFrame(FormFault.SHALLOW_REP, repCount = 8, timestampMillis = 0)

        assertEquals(
            FormFault.SHALLOW_REP,
            selector.onFrame(fault = null, repCount = 8, timestampMillis = 33),
        )
        assertEquals(
            FormFault.SHALLOW_REP,
            selector.onFrame(fault = null, repCount = 8, timestampMillis = 2_000),
        )
    }

    @Test
    fun `the next rep gets its number back`() {
        selector.onFrame(FormFault.SHALLOW_REP, repCount = 8, timestampMillis = 0)

        assertNull(selector.onFrame(fault = null, repCount = 9, timestampMillis = 2_500))
    }

    /**
     * The failure this guards is silence, not noise. Every rep of a shallow set
     * faults, and a held fault suppresses the number of the rep it belongs to — so
     * without the cooldown the app would say "go deeper" once and then never speak
     * again, because every subsequent number would be suppressed too.
     */
    @Test
    fun `a shallow rep inside the cooldown keeps its number`() {
        selector.onFrame(FormFault.SHALLOW_REP, repCount = 8, timestampMillis = 0)

        assertNull(
            selector.onFrame(FormFault.SHALLOW_REP, repCount = 9, timestampMillis = 2_500),
        )
        assertNull(
            selector.onFrame(FormFault.SHALLOW_REP, repCount = 10, timestampMillis = 5_000),
        )
    }

    @Test
    fun `the correction comes back once the cooldown passes`() {
        selector.onFrame(FormFault.SHALLOW_REP, repCount = 8, timestampMillis = 0)

        assertEquals(
            FormFault.SHALLOW_REP,
            selector.onFrame(
                fault = FormFault.SHALLOW_REP,
                repCount = 12,
                timestampMillis = COOLDOWN_MILLIS,
            ),
        )
    }

    /**
     * An abandoned descent counts nothing, so it pins the count it interrupted.
     * That is what stops the previous rep's number being announced into the silence
     * behind the correction — the session cue at that count is still "five".
     */
    @Test
    fun `an abandoned descent holds against an unchanged count`() {
        selector.onFrame(FormFault.ABANDONED_DESCENT, repCount = 5, timestampMillis = 0)

        assertEquals(
            FormFault.ABANDONED_DESCENT,
            selector.onFrame(fault = null, repCount = 5, timestampMillis = 4_000),
        )
        assertNull(selector.onFrame(fault = null, repCount = 6, timestampMillis = 8_000))
    }

    /** A different fault is worth hearing, but not at the cost of the one just given. */
    @Test
    fun `a second kind of fault still waits for the cooldown`() {
        selector.onFrame(FormFault.SHALLOW_REP, repCount = 8, timestampMillis = 0)

        assertNull(
            selector.onFrame(
                fault = FormFault.ABANDONED_DESCENT,
                repCount = 9,
                timestampMillis = 3_000,
            ),
        )
    }

    /**
     * The camera restarting on a new time base, as after a flip. Coaching again is
     * recoverable; staying quiet until the clock catches up is not, and the clock
     * may never catch up.
     */
    @Test
    fun `a backwards timestamp does not lock the cue out`() {
        selector.onFrame(FormFault.SHALLOW_REP, repCount = 8, timestampMillis = 900_000)

        assertEquals(
            FormFault.SHALLOW_REP,
            selector.onFrame(FormFault.SHALLOW_REP, repCount = 9, timestampMillis = 40),
        )
    }

    @Test
    fun `a new set starts with nothing held`() {
        selector.onFrame(FormFault.SHALLOW_REP, repCount = 8, timestampMillis = 0)
        selector.reset()

        // Same rep count as the hold, which is the only way to tell it went.
        assertNull(selector.onFrame(fault = null, repCount = 8, timestampMillis = 100))
    }

    @Test
    fun `the cooldown does not survive a new set`() {
        selector.onFrame(FormFault.SHALLOW_REP, repCount = 8, timestampMillis = 0)
        selector.reset()

        assertEquals(
            FormFault.SHALLOW_REP,
            selector.onFrame(FormFault.SHALLOW_REP, repCount = 1, timestampMillis = 2_000),
        )
    }
}
