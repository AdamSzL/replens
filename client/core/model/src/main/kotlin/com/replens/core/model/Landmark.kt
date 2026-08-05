package com.replens.core.model

/**
 * One detected body point, in the coordinate space of the analyzed image
 * ([PoseFrame.sourceWidth] x [PoseFrame.sourceHeight]).
 *
 * @param z relative depth: smaller = closer to the camera, origin roughly at
 *   the hips. Same unit scale as x/y, but far less accurate.
 * @param inFrameLikelihood 0..1 confidence that the landmark is inside the
 *   frame. Occluded or out-of-frame joints are still reported, with low values —
 *   consumers must gate on this before trusting a position.
 */
data class Landmark(
    val type: LandmarkType,
    val x: Float,
    val y: Float,
    val z: Float,
    val inFrameLikelihood: Float,
)
