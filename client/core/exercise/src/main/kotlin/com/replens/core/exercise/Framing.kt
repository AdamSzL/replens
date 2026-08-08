package com.replens.core.exercise

import com.replens.core.model.PoseFrame
import com.replens.core.posemath.torsoSize

/**
 * Whether a frame plausibly shows someone set up to train.
 *
 * This exists because of an observed failure, not a hypothetical one: at ~20 cm
 * ML Kit does not report "no body", it invents a **full skeleton** whose
 * `inFrameLikelihood` clears any usable gate, and carrying the phone to its
 * stand sweeps those fictional joints through 168 -> 115 -> 168 — one clean rep
 * off a face. Confidence cannot catch it, because the model is confident.
 * Apparent size can: a torso spanning a third of the frame is not a person
 * standing 2-3 m away.
 *
 * Deliberately not exercise-specific. It lives here because thresholds live
 * here; it moves the day setup guidance gets a module, which is a directory
 * move.
 */
enum class Framing {
    OK,
    TOO_CLOSE,

    /** Nothing to measure — the same "no reading" a rep counter already handles. */
    NO_POSE,
    ;

    companion object {
        /**
         * Measured 2026-08-08, Pixel 10 Pro XL, one continuous take of walking
         * out, squatting, and walking back: squatting at 2-3 m read 0.177-0.239
         * over 231 frames, while the phone in hand read 0.75-0.81. Everything
         * between is the walk, and it is sparse.
         *
         * Set above the top of that corridor rather than just above the measured
         * maximum, because the measurement is one room. A body filling the frame
         * head to toe — a legitimately tighter setup — reaches ~0.29, since the
         * hip-to-shoulder segment is about 29% of a person's height. Rejecting
         * that user means zero reps and an app that looks broken, which is far
         * worse than accepting a few frames of someone walking.
         *
         * No hysteresis on purpose: the two regimes are nowhere near each other,
         * so the value should never sit at the boundary. If it ever flickers,
         * that is the fix — not a shifted threshold.
         */
        const val MAX_TORSO_FRACTION = 0.40f
    }
}

/**
 * Torso length as a fraction of frame height. Both the torso and the frame scale
 * with distance and resolution, so dividing leaves a number that means the same
 * thing on any camera.
 */
fun PoseFrame.torsoFraction(): Float? = pose.torsoSize()?.div(sourceHeight)

fun PoseFrame.framing(
    maxTorsoFraction: Float = Framing.MAX_TORSO_FRACTION,
): Framing {
    val fraction = torsoFraction() ?: return Framing.NO_POSE
    return if (fraction > maxTorsoFraction) Framing.TOO_CLOSE else Framing.OK
}
