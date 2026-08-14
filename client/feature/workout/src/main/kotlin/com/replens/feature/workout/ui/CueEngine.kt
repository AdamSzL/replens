package com.replens.feature.workout.ui

import com.replens.core.exercise.FormFault
import com.replens.core.exercise.SessionState
import com.replens.core.exercise.squat.SquatRepConfig
import com.replens.core.exercise.squat.squatFormFault
import com.replens.core.model.RepUpdate
import com.replens.core.ui.UiText
import com.replens.feature.workout.ui.mapper.spokenCue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Someone who squats shallow squats shallow on every rep, so a correction tied to
 * the fault alone would fire every two seconds and stop being heard. At ten
 * seconds it lands roughly every third rep, and the reps in between get their
 * number spoken — which is also what tells the user they still counted.
 *
 * A guess to be tuned by ear.
 */
private val CORRECTION_COOLDOWN = 10.seconds

/**
 * Decides what is worth saying on this frame; [CueAnnouncer] then decides whether
 * the listener has already heard it. The cooldown lives here rather than there
 * because it changes *which* cue is chosen — its job is to let the rep number win.
 *
 * **A correction and the rep number arrive on the same frame by construction** — a
 * shallow rep is a completed rep — but the fault lands on one frame while the
 * number stays true until the next rep. A correction that only won on the frame it
 * fired would be cut off by "eight" one frame later, `QUEUE_FLUSH` and all, so it
 * stands in for the number for as long as that rep is current: [correctingRepCount].
 * Losing "eight" is self-correcting, because the next rep says "nine".
 *
 * Squat-specific in exactly two expressions — [squatFormFault] and the wording in
 * `mapper/FormCue.kt`; the rest would read the same for another exercise.
 *
 * Stateful and not thread-safe; one instance per screen.
 */
internal class CueEngine(private val repConfig: SquatRepConfig) {

    private val announcer = CueAnnouncer()

    private var correcting: FormFault? = null

    /**
     * An abandoned descent counts nothing, so it pins the count it interrupted —
     * which is what stops the previous rep's number being announced into the
     * silence behind the correction.
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
