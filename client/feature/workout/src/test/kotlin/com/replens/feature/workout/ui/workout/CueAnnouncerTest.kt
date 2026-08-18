package com.replens.feature.workout.ui.workout

import com.replens.core.text.UiText
import com.replens.feature.workout.ui.workout.model.SpokenCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

private val STEP_BACK = UiText.Resource(1)
private val GO = UiText.Resource(2)

private val nagging = SpokenCue(STEP_BACK, repeatAfter = 4.seconds)
private val once = SpokenCue(GO)

class CueAnnouncerTest {

    private val announcer = CueAnnouncer()

    @Test
    fun `a new cue is spoken`() {
        assertEquals(STEP_BACK, announcer.onFrame(nagging, 0))
    }

    /** The mapper reports the same condition on every frame; only the first speaks. */
    @Test
    fun `an unchanged cue is not spoken again immediately`() {
        announcer.onFrame(nagging, 0)
        assertNull(announcer.onFrame(nagging, 33))
        assertNull(announcer.onFrame(nagging, 66))
    }

    @Test
    fun `a nagging cue is spoken again once its interval passes`() {
        announcer.onFrame(nagging, 0)
        assertNull(announcer.onFrame(nagging, 3_999))
        assertEquals(STEP_BACK, announcer.onFrame(nagging, 4_000))
    }

    /** And the interval restarts, rather than firing on every frame from then on. */
    @Test
    fun `a repeat resets the interval`() {
        announcer.onFrame(nagging, 0)
        announcer.onFrame(nagging, 4_000)
        assertNull(announcer.onFrame(nagging, 4_033))
        assertEquals(STEP_BACK, announcer.onFrame(nagging, 8_000))
    }

    @Test
    fun `a one-shot cue is never repeated`() {
        assertEquals(GO, announcer.onFrame(once, 0))
        assertNull(announcer.onFrame(once, 60_000))
    }

    @Test
    fun `a different cue interrupts`() {
        announcer.onFrame(nagging, 0)
        assertEquals(GO, announcer.onFrame(once, 33))
    }

    /**
     * The case this protects against: a setup gate flickering across its threshold
     * alternates the cue with silence, and treating each return as news would say
     * "step back from the phone" several times a second.
     */
    @Test
    fun `silence does not make an unchanged cue news again`() {
        announcer.onFrame(nagging, 0)
        assertNull(announcer.onFrame(null, 33))
        assertNull(announcer.onFrame(nagging, 66))
    }

    /** Which is why starting a set has to say so explicitly. */
    @Test
    fun `a reset speaks the same cue again immediately`() {
        announcer.onFrame(nagging, 0)
        announcer.reset()
        assertEquals(STEP_BACK, announcer.onFrame(nagging, 33))
    }

    /**
     * A backwards timestamp is the camera rebinding on a new time base. Speaking
     * recovers on the next frame; going quiet would last until the clock caught up.
     */
    @Test
    fun `a backwards timestamp speaks rather than going silent`() {
        announcer.onFrame(nagging, 60_000)
        assertEquals(STEP_BACK, announcer.onFrame(nagging, 100))
    }
}
