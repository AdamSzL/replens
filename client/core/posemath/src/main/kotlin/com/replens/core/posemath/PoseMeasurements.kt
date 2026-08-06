package com.replens.core.posemath

import com.replens.core.model.BodyPose
import com.replens.core.model.LandmarkType

/**
 * Measurements taken straight off a [BodyPose], by landmark type. Every function
 * returns null when a landmark it needs is missing, so callers compose with `?.`
 * instead of checking presence up front.
 *
 * Nothing here filters on `inFrameLikelihood` — how much confidence a
 * measurement needs varies per rule (side-view rules use near-side joints only
 * and ignore the inferred far side), so that decision stays with the caller.
 *
 * Angles need no normalization: they are already invariant to scale and
 * translation, so the same squat reads the same at 2 m and at 4 m. Distances do
 * not have that property, which is why they come in `...Normalized` form.
 */
fun BodyPose.point(type: LandmarkType): Point? = this[type]?.point

fun BodyPose.angleDegrees(
    a: LandmarkType,
    vertex: LandmarkType,
    b: LandmarkType,
): Float? {
    val pointA = point(a) ?: return null
    val pointVertex = point(vertex) ?: return null
    val pointB = point(b) ?: return null
    return angleDegrees(pointA, pointVertex, pointB)
}

fun BodyPose.midHip(): Point? {
    val left = point(LandmarkType.LEFT_HIP) ?: return null
    val right = point(LandmarkType.RIGHT_HIP) ?: return null
    return midpoint(left, right)
}

fun BodyPose.midShoulder(): Point? {
    val left = point(LandmarkType.LEFT_SHOULDER) ?: return null
    val right = point(LandmarkType.RIGHT_SHOULDER) ?: return null
    return midpoint(left, right)
}

/**
 * Hip-to-shoulder distance in pixels — the reference length every normalized
 * measurement divides by. Scales with both body size and camera distance, which
 * is exactly what makes it useful: dividing cancels both.
 */
fun BodyPose.torsoSize(): Float? {
    val hip = midHip() ?: return null
    val shoulder = midShoulder() ?: return null
    return distance(hip, shoulder).takeIf { it > 0f }
}

/** Torso lean away from vertical, in degrees. 0 is upright. */
fun BodyPose.torsoLeanDegrees(): Float? {
    val hip = midHip() ?: return null
    val shoulder = midShoulder() ?: return null
    return angleFromVerticalDegrees(hip, shoulder)
}

/** Distance between two landmarks, in torso lengths. */
fun BodyPose.distanceNormalized(a: LandmarkType, b: LandmarkType): Float? {
    val pointA = point(a) ?: return null
    val pointB = point(b) ?: return null
    val torso = torsoSize() ?: return null
    return distance(pointA, pointB) / torso
}

/**
 * Signed perpendicular distance from a landmark to the line through two others,
 * in torso lengths. Knee against hip->ankle is the valgus/varus measurement; see
 * [deviationFromLine] for what the sign means.
 */
fun BodyPose.deviationFromLineNormalized(
    point: LandmarkType,
    lineStart: LandmarkType,
    lineEnd: LandmarkType,
): Float? {
    val p = point(point) ?: return null
    val start = point(lineStart) ?: return null
    val end = point(lineEnd) ?: return null
    val torso = torsoSize() ?: return null
    return deviationFromLine(p, start, end)?.div(torso)
}
