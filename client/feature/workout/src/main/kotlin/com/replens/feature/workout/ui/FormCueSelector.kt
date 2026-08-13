package com.replens.feature.workout.ui

import com.replens.core.exercise.FormFault
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How long before the same correction is worth making again.
 *
 * Someone who squats shallow squats shallow on every rep, so a cue tied to the
 * fault alone would fire every two seconds and stop being heard by the third —
 * the decay that is already on the risk list. At ten seconds a correction lands
 * roughly every third rep, and the reps in between get their number spoken, which
 * is also what tells the user those reps still counted.
 *
 * A guess to be tuned by ear, like the setup repeat interval.
 */
private val FORM_CUE_COOLDOWN = 10.seconds

/**
 * Decides which form fault is live, so the announcer is only ever offered one
 * thing to say.
 *
 * **A fault and the rep number arrive on the same frame by construction** — a
 * shallow rep is a completed rep — and `QUEUE_FLUSH` means whichever is spoken
 * second erases the first about 33 ms in. So a fault cannot merely outrank the
 * number on the frame it fires: it has to *replace* it for as long as that rep is
 * the current one, which is what [heldAtRepCount] does. Losing "eight" is
 * self-correcting, because the next rep says "nine"; losing "go deeper" is not.
 *
 * The cooldown is the other half of that, and it is not just politeness: without
 * it, a set of uniformly shallow reps would suppress every number it replaced and
 * the app would go quiet after one correction.
 *
 * Frame-driven, like everything else on this path — the cooldown is measured
 * against the timestamps the frames already carry, so it cannot run on in a
 * backgrounded app.
 *
 * Stateful and not thread-safe; one instance per screen.
 */
internal class FormCueSelector {

    private var held: FormFault? = null

    /**
     * The rep count [held] belongs to, and the whole hold mechanism. An abandoned
     * descent counts nothing, so it pins the count it interrupted and the hold ends
     * when the next real rep moves the number on — which is also what stops the
     * previous rep's number being re-announced into the silence after it.
     */
    private var heldAtRepCount = 0

    private var heldAtMillis = 0L

    /**
     * Pass the fault this frame produced, if any. Returns what should be said
     * *instead of* the session cue, or null to leave the session cue alone.
     */
    fun onFrame(fault: FormFault?, repCount: Int, timestampMillis: Long): FormFault? {
        if (fault != null && offCooldown(timestampMillis)) {
            held = fault
            heldAtRepCount = repCount
            heldAtMillis = timestampMillis
        }
        // A fault dropped for the cooldown leaves the rep number to be spoken as
        // normal, which is how the count survives a run of shallow reps.
        return held.takeIf { repCount == heldAtRepCount }
    }

    fun reset() {
        held = null
        heldAtRepCount = 0
        heldAtMillis = 0L
    }

    private fun offCooldown(timestampMillis: Long): Boolean {
        if (held == null) return true
        val elapsed = (timestampMillis - heldAtMillis).milliseconds
        // A negative gap is the camera restarting on a new time base; coaching is
        // the recoverable answer, going quiet until it catches up is not.
        return elapsed < Duration.ZERO || elapsed >= FORM_CUE_COOLDOWN
    }
}
