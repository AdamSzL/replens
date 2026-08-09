package com.replens.core.exercise.squat

import com.replens.core.exercise.AbandonedDescent
import com.replens.core.exercise.Rep
import com.replens.core.exercise.RepPhase
import com.replens.core.exercise.RepUpdate

/**
 * Thresholds for [SquatRepCounter], in **interior knee angle degrees** (180 is a
 * straight leg, ~90 is parallel).
 *
 * Each boundary has separate enter and exit angles — that gap is the hysteresis,
 * and it is the whole reason this works. With one threshold, an angle hovering at
 * 114.6, 115.4, 114.8 would produce three reps out of one. The band must stay
 * wider than the residual noise, including the ~10 frames after a descent while
 * the One Euro cutoff is still winding back down.
 *
 * [bottomEnterAngle] decides only "this was a rep attempt" and is deliberately
 * forgiving — depth quality is graded separately from [Rep.deepestAngle], so
 * being lenient here costs nothing and being strict makes the app look broken.
 *
 * Starting values are research-informed, not tuned. Tune against fixtures.
 */
data class SquatRepConfig(
    val standingExitAngle: Float = 160f,
    val bottomEnterAngle: Float = 115f,
    val bottomExitAngle: Float = 125f,
    val standingEnterAngle: Float = 168f,
    val goodDepthAngle: Float = 95f,
    val minRepDurationMillis: Long = 400L,
    val maxMissingFrames: Int = 10,
) {
    init {
        require(standingEnterAngle > standingExitAngle) {
            "standingEnterAngle ($standingEnterAngle) must exceed standingExitAngle " +
                "($standingExitAngle), otherwise standing has no hysteresis"
        }
        require(bottomExitAngle > bottomEnterAngle) {
            "bottomExitAngle ($bottomExitAngle) must exceed bottomEnterAngle " +
                "($bottomEnterAngle), otherwise the bottom has no hysteresis"
        }
        require(standingExitAngle > bottomExitAngle) {
            "standingExitAngle ($standingExitAngle) must exceed bottomExitAngle " +
                "($bottomExitAngle), otherwise standing and the bottom overlap"
        }
        require(minRepDurationMillis >= 0) { "minRepDurationMillis must not be negative" }
        require(maxMissingFrames > 0) { "maxMissingFrames must be positive" }
    }
}

/**
 * Counts squat reps from a stream of knee angles.
 *
 * ```
 *         < standingExit            < bottomEnter
 * STANDING ──────────► DESCENDING ──────────► BOTTOM
 *    ▲                     │                     │
 *    │                     │ >= standingEnter    │ > bottomExit
 *    │                     │ (aborted, no rep)   ▼
 *    └───────────────────  ┴───────────────  ASCENDING
 *         >= standingEnter  →  rep counted here
 * ```
 *
 * The rep is counted on ASCENDING -> STANDING: you have not done a rep until you
 * have stood back up. At most one transition happens per frame, so a movement
 * that skips a phase in a single frame simply takes two.
 *
 * Stateful and not thread-safe; one instance per set.
 */
class SquatRepCounter(private val config: SquatRepConfig = SquatRepConfig()) {

    var phase: RepPhase = RepPhase.STANDING
        private set

    var repCount: Int = 0
        private set

    /**
     * Guards against starting mid-squat. Until a genuinely standing frame is
     * seen, no descent can begin — otherwise launching the app while already
     * crouched would count the way up as a whole rep.
     */
    private var standingConfirmed = false

    private var repStartedAtMillis: Long? = null
    private var bottomAtMillis: Long? = null
    private var deepestAngle: Float = Float.MAX_VALUE
    private var missingFrames = 0

    /**
     * Feeds one frame. [depthAngle] is null when the pose was missing or not
     * confident enough; after [SquatRepConfig.maxMissingFrames] consecutive
     * nulls any rep in progress is abandoned, so walking out of frame halfway
     * down does not produce a rep on the way back in.
     */
    fun update(depthAngle: Float?, timestampMillis: Long): RepUpdate {
        if (depthAngle == null) {
            missingFrames++
            if (missingFrames >= config.maxMissingFrames) abandonAfterTrackingLost()
            return RepUpdate(phase)
        }
        missingFrames = 0

        if (repStartedAtMillis != null) {
            deepestAngle = minOf(deepestAngle, depthAngle)
        }

        var completed: Rep? = null
        var abandoned: AbandonedDescent? = null
        when (phase) {
            RepPhase.STANDING -> {
                if (depthAngle >= config.standingEnterAngle) standingConfirmed = true
                if (standingConfirmed && depthAngle < config.standingExitAngle) {
                    phase = RepPhase.DESCENDING
                    repStartedAtMillis = timestampMillis
                    deepestAngle = depthAngle
                }
            }

            RepPhase.DESCENDING -> when {
                depthAngle < config.bottomEnterAngle -> {
                    phase = RepPhase.BOTTOM
                    bottomAtMillis = timestampMillis
                }
                // Came back up without reaching depth: not a rep, but worth
                // reporting — it is the only evidence the user is trying.
                depthAngle >= config.standingEnterAngle -> {
                    abandoned = abandonDescent(timestampMillis)
                    phase = RepPhase.STANDING
                }
            }

            RepPhase.BOTTOM -> {
                if (depthAngle > config.bottomExitAngle) phase = RepPhase.ASCENDING
            }

            RepPhase.ASCENDING -> when {
                // Dipped again before standing up — still the same rep.
                depthAngle < config.bottomEnterAngle -> phase = RepPhase.BOTTOM
                depthAngle >= config.standingEnterAngle -> {
                    completed = completeRep(timestampMillis)
                    phase = RepPhase.STANDING
                }
            }
        }
        return RepUpdate(phase, completed, abandoned)
    }

    fun reset() {
        phase = RepPhase.STANDING
        repCount = 0
        standingConfirmed = false
        missingFrames = 0
        clearRepInProgress()
    }

    /**
     * Only the deliberate turn-around reports one. Losing tracking does not: the
     * lifter may still be at the bottom, and "deeper" would be a guess.
     */
    private fun abandonDescent(timestampMillis: Long): AbandonedDescent? {
        val startedAt = repStartedAtMillis
        val deepest = deepestAngle
        clearRepInProgress()
        if (startedAt == null) return null
        return AbandonedDescent(
            deepestAngle = deepest,
            startedAtMillis = startedAt,
            abandonedAtMillis = timestampMillis,
        )
    }

    private fun completeRep(timestampMillis: Long): Rep? {
        val startedAt = repStartedAtMillis
        val bottomAt = bottomAtMillis
        val deepest = deepestAngle
        clearRepInProgress()

        if (startedAt == null || bottomAt == null) return null
        // A full down-and-up faster than a person can move is a noise spike.
        if (timestampMillis - startedAt < config.minRepDurationMillis) return null

        repCount++
        return Rep(
            index = repCount,
            deepestAngle = deepest,
            startedAtMillis = startedAt,
            bottomAtMillis = bottomAt,
            completedAtMillis = timestampMillis,
        )
    }

    /**
     * Unlike an aborted descent — where the high angle proves the lifter is
     * standing — losing tracking says nothing about where they are, so standing
     * has to be re-established before another rep can start.
     */
    private fun abandonAfterTrackingLost() {
        phase = RepPhase.STANDING
        standingConfirmed = false
        clearRepInProgress()
    }

    private fun clearRepInProgress() {
        repStartedAtMillis = null
        bottomAtMillis = null
        deepestAngle = Float.MAX_VALUE
    }
}

/**
 * Whether a counted rep actually reached depth.
 *
 * A separate question from whether it counted, and a stricter one:
 * [SquatRepConfig.bottomEnterAngle] is deliberately lenient so real reps are not
 * silently dropped, while [SquatRepConfig.goodDepthAngle] is roughly parallel.
 * Everything between counts and is shallow, which is the gap "8 of 12 at depth"
 * reports.
 *
 * [config] is required rather than defaulted: once thresholds are calibrated per
 * user, a defaulted call would grade against stock numbers reps that were counted
 * against personal ones, and agree with nothing.
 */
fun Rep.isAtDepth(config: SquatRepConfig): Boolean =
    deepestAngle <= config.goodDepthAngle
