package com.replens.feature.workout.ui.workout.mapper

import com.replens.core.exercise.FormFault
import com.replens.core.exercise.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormCueTest {

    /**
     * Cue *inequality* is how anything gets spoken at all, so two faults sharing a
     * line would mean the second one is silent whenever it follows the first.
     */
    @Test
    fun `every fault has its own line`() {
        val lines = FormFault.entries.map { it.spokenCue.text }

        assertEquals(lines.size, lines.toSet().size)
    }

    /**
     * A correction must not collide with the rep number it replaces either — and
     * this is the one pair that lands on the very same frame.
     */
    @Test
    fun `a correction is never the same line as a rep count`() {
        val corrections = FormFault.entries.map { it.spokenCue.text }.toSet()
        val counts = (1..20).map {
            SessionState.Active.spokenCue(repCount = it, repsAtDepth = 0)?.text
        }

        assertEquals(emptySet<Any>(), corrections intersect counts.toSet())
    }

    /** Repeating is [com.replens.feature.workout.ui.workout.CueEngine]'s question. */
    @Test
    fun `a correction is never told to repeat itself`() {
        FormFault.entries.forEach { assertNull(it.spokenCue.repeatAfter) }
    }
}
