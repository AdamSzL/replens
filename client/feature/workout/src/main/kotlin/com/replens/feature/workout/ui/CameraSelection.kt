package com.replens.feature.workout.ui

import com.replens.core.pose.CameraFacing

/**
 * Which lens a set should start on, or null on a device that reports none.
 *
 * Front first because it is the only lens where you can check your own framing,
 * and getting framed correctly is the hardest part of setting this app up. **Not
 * a hardware fact** — it is this screen's opinion, which is why it does not live
 * beside [CameraFacing]: that module knows which lenses exist and has no standing
 * to have a favorite.
 *
 * Null is the honest answer for a device with no usable lens rather than a
 * problem to handle: nothing is selected, so nothing binds, and the app waits
 * instead of asking CameraX for a camera that isn't there.
 *
 * Shelf life: once the choice is remembered across launches this becomes only the
 * first-launch default.
 */
internal val Set<CameraFacing>.preferred: CameraFacing?
    get() = if (CameraFacing.FRONT in this) CameraFacing.FRONT else firstOrNull()
