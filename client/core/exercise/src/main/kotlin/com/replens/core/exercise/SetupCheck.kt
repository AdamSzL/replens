package com.replens.core.exercise

import com.replens.core.model.BodyPose
import com.replens.core.model.LandmarkType
import com.replens.core.model.PoseFrame

/**
 * Whether the user is positioned to *start* a set — a stricter question than
 * [Framing], and deliberately a separate one.
 *
 * [Framing] asks whether a frame is fiction, and stays lenient because rejecting
 * a real frame mid-set silently drops a rep. This asks whether a set should begin
 * at all, where the cost of being wrong is reversed: refusing costs three seconds
 * and a message, while starting on a body whose legs are invented produces a
 * whole set measured off nothing.
 *
 * Observed 2026-08-08: leaning toward the phone at ~1-1.5 m reads a torso
 * fraction of 0.29-0.34 — under [Framing.MAX_TORSO_FRACTION], so the count-in
 * ran, while below the knees the skeleton was a tangle.
 */
enum class SetupCheck {
    READY,

    NO_POSE,

    TOO_CLOSE,

    /**
     * Depth is `angleDegrees(hip, knee, ankle)`, so an ankle that is not really
     * observed makes every rep a measurement of an invented point. This is the
     * check that catches leaning in over the phone, and it needs no threshold —
     * a landmark outside the image is not an observation.
     */
    LEGS_NOT_VISIBLE,
    ;

    companion object {
        /**
         * Tighter than [Framing.MAX_TORSO_FRACTION] because this decides whether
         * to start rather than whether to trust: measured standing reps ran
         * 0.177-0.239, and a body filling the frame head to toe reaches ~0.29.
         *
         * There is no `TOO_FAR` arm. It is a real failure mode, but no too-far
         * case has ever been recorded, and a guessed lower bound would block
         * legitimate users — the exact mistake [Framing.MAX_TORSO_FRACTION] was
         * widened to avoid. It arrives when there is a measurement to put in it.
         */
        const val MAX_TORSO_FRACTION = 0.32f
    }
}

fun PoseFrame.setupCheck(
    maxTorsoFraction: Float = SetupCheck.MAX_TORSO_FRACTION,
    minLikelihood: Float = DEFAULT_MIN_LIKELIHOOD,
): SetupCheck {
    val fraction = torsoFraction() ?: return SetupCheck.NO_POSE
    return when {
        fraction > maxTorsoFraction -> SetupCheck.TOO_CLOSE
        !hasUsableLeg(minLikelihood) -> SetupCheck.LEGS_NOT_VISIBLE
        else -> SetupCheck.READY
    }
}

/**
 * One leg is enough — filming at 45 degrees occludes the far side by design, and
 * [squatSignals] already measures depth from whichever legs survive.
 */
private fun PoseFrame.hasUsableLeg(minLikelihood: Float): Boolean =
    isLegObserved(
        LandmarkType.LEFT_HIP, LandmarkType.LEFT_KNEE, LandmarkType.LEFT_ANKLE, minLikelihood,
    ) || isLegObserved(
        LandmarkType.RIGHT_HIP, LandmarkType.RIGHT_KNEE, LandmarkType.RIGHT_ANKLE, minLikelihood,
    )

private fun PoseFrame.isLegObserved(
    hip: LandmarkType,
    knee: LandmarkType,
    ankle: LandmarkType,
    minLikelihood: Float,
): Boolean = listOf(hip, knee, ankle).all { isObserved(pose, it, minLikelihood) }

/**
 * Confidence alone is not enough: ML Kit reports landmarks past the edge of the
 * image, which is what feet cut off by the bottom of the frame look like
 * numerically.
 */
private fun PoseFrame.isObserved(
    pose: BodyPose,
    type: LandmarkType,
    minLikelihood: Float,
): Boolean {
    val landmark = pose[type] ?: return false
    return landmark.inFrameLikelihood >= minLikelihood &&
        landmark.x in 0f..sourceWidth.toFloat() &&
        landmark.y in 0f..sourceHeight.toFloat()
}
