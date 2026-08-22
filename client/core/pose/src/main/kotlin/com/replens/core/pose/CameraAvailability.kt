package com.replens.core.pose

import androidx.camera.core.CameraState

/**
 * Whether a camera can be expected at all, which is a different question from
 * [CameraOptions] — that one is about what a working camera could do.
 *
 * Derived from CameraX's `CameraState`, and the mapping is not the obvious one.
 * An error is **not** a failure signal: while CameraX is retrying, the state
 * stays `OPENING` and carries the error it is recovering from. What separates
 * "still trying" from "gave up" is the state, not the error.
 */
enum class CameraAvailability {

    /** Open, opening, or closed with nothing wrong — every ordinary moment. */
    Ready,

    /**
     * `PENDING_OPEN`: the lens is held by another app, or CameraX exhausted its
     * reopen attempts — which is where a `SecurityException` from a revoked
     * permission lands, since camera2 offers no permission error code.
     *
     * Not terminal. CameraX reopens on its own when the camera service says the
     * device is free again, so this clears without anyone pressing anything.
     */
    Unavailable,

    /**
     * A critical `CameraState` error: disabled by device policy, a fatal device
     * error, "Do Not Disturb" on some API 28 devices, or the camera physically
     * removed. CameraX will not retry; the user has to do something.
     */
    Failed,
}

/**
 * The state decides and the error only separates the terminal cases: `OPENING`
 * carrying an error is a retry in progress, since CameraX keeps the state and
 * exposes what it is recovering from. An error-free `CLOSING`/`CLOSED` is an
 * ordinary teardown and must read as [Ready], or leaving the screen would report
 * a broken camera every time.
 */
internal fun CameraState.toAvailability(): CameraAvailability {
    return when {
        error?.type == CameraState.ErrorType.CRITICAL -> CameraAvailability.Failed
        type == CameraState.Type.PENDING_OPEN -> CameraAvailability.Unavailable
        else -> CameraAvailability.Ready
    }
}
