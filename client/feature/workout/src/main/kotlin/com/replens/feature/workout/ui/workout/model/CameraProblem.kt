package com.replens.feature.workout.ui.workout.model

import com.replens.core.pose.CameraAvailability

/** Split along a line CameraX does not draw: to it, the first two are both `PENDING_OPEN`. */
internal enum class CameraProblem {

    /** Only reachable on a screen restored after the revoke killed the process. */
    PermissionMissing,

    /** Clears itself when the other app lets go. */
    InUse,

    /** Critical: CameraX will not retry. Policy, a dead service, DND. */
    Broken,
}

/** Null [isCameraGranted] means nobody has asked yet — the reading needs a `Context`. */
internal fun cameraProblem(
    availability: CameraAvailability,
    isCameraGranted: Boolean?,
): CameraProblem? {
    return when {
        availability == CameraAvailability.Ready -> null
        // Outranks whatever CameraX reported. A permission we do not hold is the
        // problem, and neither waiting nor rebooting fixes it — while which error
        // a denial produces turned out to be device-specific.
        isCameraGranted == false -> CameraProblem.PermissionMissing
        availability == CameraAvailability.Failed -> CameraProblem.Broken
        // Naming the wrong problem is worse than naming none, and an unread
        // permission looks exactly like a camera still opening.
        isCameraGranted == null -> null
        else -> CameraProblem.InUse
    }
}
