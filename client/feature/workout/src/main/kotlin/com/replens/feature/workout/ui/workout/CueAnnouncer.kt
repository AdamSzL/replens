package com.replens.feature.workout.ui.workout

import com.replens.core.text.UiText
import com.replens.feature.workout.ui.workout.model.SpokenCue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Turns a per-frame condition into speech a person would tolerate: the mapper
 * reports the same cue ~30 times a second, and only what differs from the last
 * line is spoken.
 *
 * **Frame-driven** — intervals are measured against the timestamps frames carry
 * rather than a clock of their own, so a backgrounded app whose camera has unbound
 * falls silent by construction. A `delay` would keep announcing to it.
 *
 * Stateful and not thread-safe; one instance per screen.
 */
internal class CueAnnouncer {

    private var spoken: UiText? = null
    private var spokenAtMillis = 0L

    /**
     * Returns the line to speak now, or null for silence.
     *
     * Sameness is [UiText] equality, which is why its arms are data classes over a
     * `List`: with an `Array`, two identical cues never compare equal and every
     * frame speaks.
     */
    fun onFrame(cue: SpokenCue?, timestampMillis: Long): UiText? {
        // Silence deliberately does not clear what was last said: a gate sitting
        // near its threshold flickers, and clearing here would restart the same
        // instruction several times a second.
        if (cue == null) return null

        if (cue.text == spoken) {
            val repeatAfter = cue.repeatAfter ?: return null
            val elapsed = (timestampMillis - spokenAtMillis).milliseconds
            // A negative gap is the camera restarting on a new time base; speaking
            // is the recoverable answer, staying silent until it catches up is not.
            if (elapsed >= Duration.ZERO && elapsed < repeatAfter) return null
        }
        spoken = cue.text
        spokenAtMillis = timestampMillis
        return cue.text
    }

    /**
     * Belongs to starting a set: pressing Start and hearing nothing because the
     * same instruction was given during the last attempt reads as a broken app.
     */
    fun reset() {
        spoken = null
    }
}
