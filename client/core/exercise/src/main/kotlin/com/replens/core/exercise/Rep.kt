package com.replens.core.exercise

import kotlin.time.Duration

/**
 * Vocabulary shared by every exercise, kept out of the per-exercise packages
 * (`…exercise.squat`, and whatever follows) so those never need to depend on
 * each other.
 *
 * Whether this genuinely generalises is **not yet established** — one exercise is
 * not enough evidence. Push-ups and curls plausibly reuse the same four phases,
 * but that gets decided when the second one is written, not now.
 */

/** Where the lifter is within a rep. */
enum class RepPhase {
    STANDING,
    DESCENDING,
    BOTTOM,
    ASCENDING,
}

/**
 * One completed rep.
 *
 * [deepestAngle] is kept rather than a pass/fail flag because **counting a rep
 * and judging its depth are separate questions**: the counter is deliberately
 * lenient so reps that felt real are not silently dropped, and quality is graded
 * from this number afterwards. Ten shallow reps should read as "10 reps, depth
 * 42%", not "0 reps".
 *
 * Durations rather than the timestamps they were measured from, so **the frame
 * clock never leaves the counter**. Those timestamps are monotonic with an
 * arbitrary origin, which would make a stored rep and a live one mean different
 * things by the same field name.
 *
 * [index] is 1-based, so it is the number a user is told. Carried on the rep
 * rather than left to list position because the round trip has to survive one:
 * re-deriving it on write would make "the list is complete and in order" a silent
 * invariant of the mapper, and every reader wanting to name a rep would pay an
 * off-by-one.
 */
data class Rep(
    val index: Int,
    val deepestAngle: Float,
    val descent: Duration,
    val ascent: Duration,
)

/**
 * A descent that turned back before reaching the counting threshold, so nothing
 * was counted.
 *
 * Reported rather than dropped, because silence is the wrong feedback here:
 * someone doing quarter-squats gets no reps *and* no explanation, which is
 * indistinguishable from the app being broken. [deepestAngle] is also the
 * evidence per-user calibration needs — it is how far this body actually goes.
 *
 * One duration, not two: there is no bottom to split it at.
 */
data class AbandonedDescent(
    val deepestAngle: Float,
    val total: Duration,
)

/** Result of feeding one frame to a rep counter. */
data class RepUpdate(
    val phase: RepPhase,
    val completedRep: Rep? = null,
    val abandonedDescent: AbandonedDescent? = null,
)
