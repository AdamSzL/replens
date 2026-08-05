package com.replens.core.model

/**
 * One analyzed camera frame: the detected pose plus the geometry needed to
 * interpret it.
 *
 * @param sourceWidth width of the (rotation-corrected) image the landmark
 *   coordinates live in
 * @param timestampMillis capture time on an arbitrary monotonic clock — only
 *   deltas between frames are meaningful (time-based smoothing needs them).
 */
data class PoseFrame(
    val pose: BodyPose,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val timestampMillis: Long,
)
