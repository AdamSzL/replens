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
