package com.replens.core.exercise

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
 */
data class Rep(
    val index: Int,
    val deepestAngle: Float,
    val startedAtMillis: Long,
    val bottomAtMillis: Long,
    val completedAtMillis: Long,
) {
    val descentMillis: Long get() = bottomAtMillis - startedAtMillis
    val ascentMillis: Long get() = completedAtMillis - bottomAtMillis
    val totalMillis: Long get() = completedAtMillis - startedAtMillis
}

/** Result of feeding one frame to a rep counter. */
data class RepUpdate(
    val phase: RepPhase,
    val completedRep: Rep? = null,
)
