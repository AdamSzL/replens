package com.replens.core.exercise.squat

import com.replens.core.model.BodyPose
import com.replens.core.model.LandmarkType
import com.replens.core.posemath.Point
import com.replens.core.posemath.angleDegrees
import com.replens.core.posemath.angleFromVerticalDegrees
import com.replens.core.posemath.midHip
import com.replens.core.posemath.midShoulder
import com.replens.core.posemath.point

/**
 * Everything one frame contributes to squat analysis, reduced to scalars.
 *
 * This is the boundary between "poses" and "logic": [SquatRepCounter] consumes
 * only [depthAngle], which keeps it testable with readable lists of numbers
 * instead of hand-built skeletons. It is also the row format for the CSV
 * fixtures generated from real footage.
 *
 * All angles are **interior angles in degrees** — 180 is a straight leg, ~90 is
 * parallel. Note that the literature quotes *flexion* instead, where 0 is a
 * straight leg: `interior = 180 - flexion`. The two coincide at 90, which is
 * exactly the number everyone quotes for parallel, so a mix-up hides itself.
 *
 * Any field is null when the landmarks it needs are missing or not confident
 * enough. Null means "no reading", never "zero".
 */
data class SquatSignals(
    val timestampMillis: Long,
    val leftKneeAngle: Float?,
    val rightKneeAngle: Float?,
    val depthAngle: Float?,
    val femurInclination: Float?,
    val torsoLeanDegrees: Float?,
) {
    companion object {
        const val DEFAULT_MIN_LIKELIHOOD = 0.5f
    }
}

/**
 * Reduces a pose to [SquatSignals].
 *
 * Each leg is gated as a unit on [minLikelihood] across **all three** of its
 * joints — one unreliable ankle makes that leg's whole angle a guess. When both
 * legs survive their angles are averaged; when one does it is used alone.
 *
 * Averaging rather than taking the deeper or shallower knee is deliberate: a
 * large left/right disagreement is far more often measurement error than real
 * asymmetry, and averaging cancels error where min/max amplify whichever reading
 * is wrong. The per-leg angles stay on the result so asymmetry rules can still
 * see them.
 *
 * Filming side-on is why the gate exists at all: the far leg is occluded, the
 * detector reports it anyway with low confidence, and blending a real 95 degrees
 * with a hallucinated 140 gives 117 — above the rep threshold, so the rep is
 * silently lost with nothing looking broken.
 */
fun BodyPose.squatSignals(
    timestampMillis: Long,
    minLikelihood: Float = SquatSignals.DEFAULT_MIN_LIKELIHOOD,
): SquatSignals {
    val left = kneeAngle(
        LandmarkType.LEFT_HIP, LandmarkType.LEFT_KNEE, LandmarkType.LEFT_ANKLE, minLikelihood,
    )
    val right = kneeAngle(
        LandmarkType.RIGHT_HIP, LandmarkType.RIGHT_KNEE, LandmarkType.RIGHT_ANKLE, minLikelihood,
    )
    return SquatSignals(
        timestampMillis = timestampMillis,
        leftKneeAngle = left,
        rightKneeAngle = right,
        depthAngle = mean(left, right),
        femurInclination = femurInclination(minLikelihood),
        torsoLeanDegrees = torsoLean(),
    )
}

private fun BodyPose.confidentPoint(type: LandmarkType, minLikelihood: Float): Point? =
    this[type]?.takeIf { it.inFrameLikelihood >= minLikelihood }?.point

private fun BodyPose.kneeAngle(
    hip: LandmarkType,
    knee: LandmarkType,
    ankle: LandmarkType,
    minLikelihood: Float,
): Float? {
    val hipPoint = confidentPoint(hip, minLikelihood) ?: return null
    val kneePoint = confidentPoint(knee, minLikelihood) ?: return null
    val anklePoint = confidentPoint(ankle, minLikelihood) ?: return null
    return angleDegrees(hipPoint, kneePoint, anklePoint)
}

/**
 * Thigh angle away from straight up. Measured hip -> knee, so **standing is ~180**
 * (the thigh points straight down) and **90 is horizontal** — the textbook
 * definition of parallel. Like the knee angle, it therefore falls as the squat
 * deepens.
 *
 * Reported because it states depth more honestly than a knee angle does, but
 * **never used for counting**: it needs the image's vertical to be the real
 * vertical, and camera roll breaks that. Interior angles need no reference frame
 * at all.
 */
private fun BodyPose.femurInclination(minLikelihood: Float): Float? {
    val left = segmentFromVertical(LandmarkType.LEFT_HIP, LandmarkType.LEFT_KNEE, minLikelihood)
    val right = segmentFromVertical(LandmarkType.RIGHT_HIP, LandmarkType.RIGHT_KNEE, minLikelihood)
    return mean(left, right)
}

private fun BodyPose.segmentFromVertical(
    from: LandmarkType,
    to: LandmarkType,
    minLikelihood: Float,
): Float? {
    val start = confidentPoint(from, minLikelihood) ?: return null
    val end = confidentPoint(to, minLikelihood) ?: return null
    return angleFromVerticalDegrees(start, end)
}

private fun BodyPose.torsoLean(): Float? {
    val hip = midHip() ?: return null
    val shoulder = midShoulder() ?: return null
    return angleFromVerticalDegrees(hip, shoulder)
}

private fun mean(a: Float?, b: Float?): Float? = when {
    a != null && b != null -> (a + b) / 2f
    else -> a ?: b
}
