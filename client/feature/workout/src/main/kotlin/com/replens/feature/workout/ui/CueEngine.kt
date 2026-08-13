package com.replens.feature.workout.ui

import com.replens.core.exercise.FormFault
import com.replens.core.exercise.RepUpdate
import com.replens.core.exercise.SessionState
import com.replens.core.exercise.squat.SquatRepConfig
import com.replens.core.exercise.squat.squatFormFault
import com.replens.core.ui.UiText
import com.replens.feature.workout.ui.mapper.spokenCue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How long before the same correction is worth making again.
 *
 * Someone who squats shallow squats shallow on every rep, so a correction tied to
 * the fault alone would fire every two seconds and stop being heard by the third —
 * the cue decay that is already on the risk list. At ten seconds a correction lands
 * roughly every third rep, and the reps in between get their number spoken, which
 * is also what tells the user those reps still counted.
 *
 * A guess to be tuned by ear, like the setup repeat interval.
 */
private val CORRECTION_COOLDOWN = 10.seconds

/**
 * Everything the app might say on one frame, reduced to the one line worth saying.
 *
 * This and [CueAnnouncer] answer different questions, and the split is what keeps
 * either of them small: **this decides what is true and what matters**, the
 * announcer decides **whether the listener has already heard it**. So the timing
 * rule here is a cooldown that changes *which* cue is chosen, while the announcer's
 * `repeatAfter` only changes whether the chosen one is said again.
 *
 * ### Why a correction has to be held
 *
 * **A fault and the rep number arrive on the same frame by construction** — a
 * shallow rep is a completed rep — but they are offered at different rates: the
 * fault on one frame, the number on every frame until the next rep. So a
 * correction that only won on the frame it fired would be followed by "eight" one
 * frame later, and `QUEUE_FLUSH` would cut it off about 33 ms in. It has to stand
 * in for the number for as long as that rep is the current one, which is what
 * [correctingRepCount] does. Losing "eight" is self-correcting, because the next
 * rep says "nine"; losing "go deeper" is not.
 *
 * The cooldown is the other half of that, and it is not politeness: a held
 * correction suppresses the number of the rep it belongs to, so without it a set
 * of uniformly shallow reps would go quiet after the first one.
 *
 * ### What is squat-specific
 *
 * Two expressions, deliberately kept identifiable: [squatFormFault] below, and the
 * wording in `mapper/FormCue.kt`. Everything else — the session cues, the
 * arbitration, the cooldown — reads the same for any exercise, so exercise #2
 * should be parameters rather than a second engine. Not parameterized today,
 * because a strategy interface with one implementation is dead code.
 *
 * Frame-driven, like [com.replens.core.exercise.SetSession] and the counter: every
 * interval is measured against the timestamps the frames already carry, so nothing
 * here runs on in a backgrounded app whose camera has unbound.
 *
 * Stateful and not thread-safe; one instance per screen.
 */
internal class CueEngine(private val repConfig: SquatRepConfig) {

    private val announcer = CueAnnouncer()

    private var correcting: FormFault? = null

    /**
     * The rep count [correcting] belongs to, and the whole hold mechanism. An
     * abandoned descent counts nothing, so it pins the count it interrupted and the
     * hold ends when the next real rep moves the number on — which is also what
     * stops the previous rep's number being announced into the silence behind the
     * correction.
     */
    private var correctingRepCount = 0

    private var correctedAtMillis = 0L

    /**
     * Call on every frame, including the ones where nothing changed: the frame
     * stream is the clock a repeating cue is measured against.
     *
     * @return the line to speak now, or null for silence.
     */
    fun onFrame(
        session: SessionState,
        repUpdate: RepUpdate?,
        repCount: Int,
        repsAtDepth: Int,
        timestampMillis: Long,
    ): UiText? {
        // Read only while counting, so a held correction cannot replace the set
        // summary the moment the set ends.
        val correction = if (session == SessionState.Active) {
            correction(
                fault = repUpdate?.squatFormFault(repConfig),
                repCount = repCount,
                timestampMillis = timestampMillis,
            )
        } else {
            null
        }

        return announcer.onFrame(
            // A correction does not queue behind the rep number, it stands in for it.
            cue = correction?.spokenCue ?: session.spokenCue(repCount, repsAtDepth),
            timestampMillis = timestampMillis,
        )
    }

    /** Belongs to starting a set; see [CueAnnouncer.reset] for why the press matters. */
    fun reset() {
        correcting = null
        correctingRepCount = 0
        correctedAtMillis = 0L
        announcer.reset()
    }

    private fun correction(
        fault: FormFault?,
        repCount: Int,
        timestampMillis: Long,
    ): FormFault? {
        if (fault != null && offCooldown(timestampMillis)) {
            correcting = fault
            correctingRepCount = repCount
            correctedAtMillis = timestampMillis
        }
        // A fault dropped for the cooldown leaves the rep number to be spoken as
        // normal, which is how the count survives a run of shallow reps.
        return correcting.takeIf { repCount == correctingRepCount }
    }

    private fun offCooldown(timestampMillis: Long): Boolean {
        if (correcting == null) return true
        val elapsed = (timestampMillis - correctedAtMillis).milliseconds
        // A negative gap is the camera restarting on a new time base; coaching again
        // is recoverable, going quiet until the clock catches up is not.
        return elapsed < Duration.ZERO || elapsed >= CORRECTION_COOLDOWN
    }
}
