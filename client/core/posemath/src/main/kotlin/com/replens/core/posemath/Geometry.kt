package com.replens.core.posemath

import com.replens.core.model.Landmark
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * A 2D point in image coordinates: x grows right, **y grows down**. Only x and y
 * — a landmark's z is relative depth and far less accurate, so no measurement
 * here uses it.
 */
data class Point(val x: Float, val y: Float)

val Landmark.point: Point get() = Point(x, y)

private const val RAD_TO_DEG = 180.0 / PI

fun midpoint(a: Point, b: Point): Point = Point((a.x + b.x) / 2f, (a.y + b.y) / 2f)

fun distance(a: Point, b: Point): Float = hypot(b.x - a.x, b.y - a.y)

/**
 * Angle at [vertex] between the rays to [a] and [b], in degrees `0..180`.
 * For a leg (hip, knee, ankle) that is ~180 standing and ~90 at parallel.
 *
 * Null when either ray has zero length — the angle is undefined, and detectors
 * do occasionally report identical coordinates for adjacent joints. Returning
 * null rather than NaN forces the caller to handle it instead of letting NaN
 * propagate silently through every downstream computation.
 *
 * Uses `atan2(|cross|, dot)` rather than `acos(dot / (|v1|*|v2|))`: acos needs
 * its argument clamped (floating-point error yields 1.0000001 on a straight leg,
 * whose acos is NaN) and is numerically worst near 0 and 180 degrees, exactly
 * where a standing leg sits.
 */
fun angleDegrees(a: Point, vertex: Point, b: Point): Float? {
    val v1x = a.x - vertex.x
    val v1y = a.y - vertex.y
    val v2x = b.x - vertex.x
    val v2y = b.y - vertex.y
    if ((v1x == 0f && v1y == 0f) || (v2x == 0f && v2y == 0f)) return null

    val cross = v1x * v2y - v1y * v2x
    val dot = v1x * v2x + v1y * v2y
    return (atan2(abs(cross), dot) * RAD_TO_DEG).toFloat()
}

/**
 * Angle of the segment [from] -> [to] away from straight up, in degrees `0..180`.
 * Hip -> shoulder gives torso lean: 0 is upright, 90 is horizontal.
 *
 * Null when the segment has zero length.
 */
fun angleFromVerticalDegrees(from: Point, to: Point): Float? {
    val dx = to.x - from.x
    val dy = to.y - from.y
    if (dx == 0f && dy == 0f) return null

    // Straight up is (0, -1) because y grows downwards.
    return (atan2(abs(dx), -dy) * RAD_TO_DEG).toFloat()
}

/**
 * Signed perpendicular distance from [point] to the infinite line through
 * [lineStart] -> [lineEnd], in pixels. Knee against the hip->ankle line is the
 * valgus/varus measurement.
 *
 * The sign says which side of the *directed* line the point falls on; opposite
 * sides give opposite signs. Which sign means "caving in" depends on the leg and
 * on which way the user faces, so that mapping belongs to the rule, not here.
 *
 * Null when the line has zero length. **Pixels — normalize before comparing to a
 * threshold** (see [deviationFromLineNormalized]).
 */
fun deviationFromLine(point: Point, lineStart: Point, lineEnd: Point): Float? {
    val dx = lineEnd.x - lineStart.x
    val dy = lineEnd.y - lineStart.y
    val length = hypot(dx, dy)
    if (length == 0f) return null

    val cross = dx * (point.y - lineStart.y) - dy * (point.x - lineStart.x)
    return cross / length
}
