package com.replens.core.pose

import androidx.camera.core.CameraSelector

enum class CameraFacing {
    FRONT,
    BACK;

    val opposite: CameraFacing get() = if (this == FRONT) BACK else FRONT
}

internal val CameraFacing.selector: CameraSelector
    get() = when (this) {
        CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
    }

/**
 * Null for external and unknown lenses. Their orientation relative to the device
 * is undefined, so neither mirroring nor the rotation the overlay maps with can be
 * derived — a webcam is better invisible than drawn wrong.
 */
internal fun Int.toCameraFacing(): CameraFacing? = when (this) {
    CameraSelector.LENS_FACING_FRONT -> CameraFacing.FRONT
    CameraSelector.LENS_FACING_BACK -> CameraFacing.BACK
    else -> null
}
