package com.replens.feature.workout.ui.mapper

import com.replens.core.exercise.SessionState
import com.replens.core.exercise.SetupCheck
import com.replens.core.ui.UiText
import com.replens.feature.workout.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `R` fields are plain ints on the unit test classpath, so none of this needs a
 * device or Robolectric — only the resolution of a [UiText] would.
 */
class SessionCueTest {

    @Test
    fun `every setup check has a message`() {
        // Exhaustiveness is the compiler's job; that no arm resolves to the same
        // line as another is not, and two identical instructions would leave the
        // user with no way to tell which problem they have.
        val messages = SetupCheck.entries.map { it.message }
        assertEquals(messages.size, messages.toSet().size)
    }

    @Test
    fun `idle says nothing`() {
        assertNull(SessionState.Idle.spokenCue(repCount = 0, repsAtDepth = 0))
    }

    @Test
    fun `a setup problem is spoken and repeats`() {
        val cue = SessionState.Waiting(SetupCheck.TOO_CLOSE).spokenCue(0, 0)

        assertEquals(SetupCheck.TOO_CLOSE.message, cue?.text)
        // The condition is still true on the next frame and every frame after it,
        // so this is the one kind of cue that has to be allowed to nag.
        assertNotNull(cue?.repeatAfter)
    }

    /**
     * Drawn but never spoken: it holds for `SetSession.settleFor`, half a second,
     * and the count-in flushes whatever is still being said. The asymmetry is
     * deliberate, and nothing but this test says so.
     */
    @Test
    fun `being ready is drawn but not spoken`() {
        assertNull(SessionState.Waiting(SetupCheck.READY).spokenCue(0, 0))
        assertEquals(
            UiText.Resource(R.string.workout_setup_hold_still),
            SetupCheck.READY.message,
        )
    }

    /**
     * What makes the countdown audible at all. [CueAnnouncer] speaks on inequality,
     * so a count-in resource that dropped its `%1$d` — "Get ready" is an entirely
     * reasonable-looking edit — would be spoken once and the rest of the count-in
     * would be silence.
     */
    @Test
    fun `each second of the count-in is a different line`() {
        val counts = (1..3).map { SessionState.CountingIn(it).spokenCue(0, 0) }
        assertEquals(counts.size, counts.toSet().size)
    }

    @Test
    fun `the count-in is spoken once per second, not once per frame`() {
        assertNull(SessionState.CountingIn(3).spokenCue(0, 0)?.repeatAfter)
    }

    @Test
    fun `go is spoken once`() {
        val cue = SessionState.Active.spokenCue(0, 0)

        assertEquals(UiText.Resource(R.string.workout_cue_go), cue?.text)
        assertNull(cue?.repeatAfter)
    }

    @Test
    fun `the summary says both numbers`() {
        val cue = SessionState.Finished.spokenCue(repCount = 12, repsAtDepth = 8)

        assertEquals(
            UiText.Plural(
                id = R.plurals.workout_cue_set_summary,
                quantity = 12,
                args = listOf(12, 8),
            ),
            cue?.text,
        )
    }

    /**
     * `quantity` picks the grammatical form and fills nothing, so the count has to
     * appear in `args` as well — otherwise `getQuantityString` throws on a plural
     * whose text contains a number, which is every plural here.
     */
    @Test
    fun `the summary passes the count as an argument too`() {
        val cue = SessionState.Finished.spokenCue(repCount = 1, repsAtDepth = 0)

        assertEquals(listOf(1, 0), (cue?.text as UiText.Plural).args)
    }
}
