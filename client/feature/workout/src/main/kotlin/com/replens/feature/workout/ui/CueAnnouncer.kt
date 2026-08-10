package com.replens.feature.workout.ui

import com.replens.core.ui.UiText
import com.replens.feature.workout.ui.model.SpokenCue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Decides which cues actually reach the speaker.
 *
 * The mapper describes what is true on every frame, so the same cue arrives ~30
 * times a second; this is what turns that into speech a person would tolerate.
 *
 * **Frame-driven**, like [com.replens.core.exercise.SetSession] and
 * [com.replens.core.exercise.squat.SquatRepCounter]: the repeat interval is
 * measured against the timestamps the frames already carry rather than a clock of
 * its own. A cue is a statement about what the camera is seeing, so when the
 * camera stops delivering there is nothing left to say — and a `delay` would go on
 * announcing it to a backgrounded app anyway.
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
     * `List` — with an `Array` two identical cues would never compare equal and
     * every frame would speak.
     */
    fun onFrame(cue: SpokenCue?, timestampMillis: Long): UiText? {
        // Silence deliberately does not clear what was last said. A gate that sits
        // near its threshold flickers, and clearing here would let a torso fraction
        // hovering at MAX_TORSO_FRACTION restart "step back from the phone" several
        // times a second. The interval is about the listener, not the condition.
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
     * Forgets what was said, so the next cue speaks however recently the same line
     * was heard. Belongs to starting a set: pressing Start and getting four seconds
     * of silence because the same instruction was given during the last attempt
     * reads as a broken app, and the press itself proves the user is listening.
     */
    fun reset() {
        spoken = null
    }
}
