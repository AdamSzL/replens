package com.replens.feature.workout.ui

import com.replens.core.pose.CameraFacing
import com.replens.core.pose.ZoomRange

/**
 * What this screen offers the user to choose about the camera.
 *
 * Opinions about squatting rather than facts about hardware, which is why they
 * live here: `:core:pose` reports what the device can do and has no standing to
 * say which of it is worth having.
 */

/**
 * Front first because it is the only lens where you can check your own framing,
 * and getting framed correctly is the hardest part of setting this app up.
 *
 * Null is not a case to handle: nothing is selected, so nothing binds, and the
 * app waits rather than asking CameraX for a lens that isn't there.
 */
internal val Set<CameraFacing>.preferred: CameraFacing?
    get() = if (CameraFacing.FRONT in this) CameraFacing.FRONT else firstOrNull()

/**
 * Framing a whole body is a zoom-*out* problem, so the widest lens is the one
 * that matters; 2x earns its place only in a room big enough that the body is
 * small in frame. Past that you would crop the user out of their own workout.
 */
internal val ZoomRange.stops: List<Float>
    get() = buildList {
        if (min < 1f) add(min)
        add(1f)
        if (max >= 2f) add(2f)
    }
