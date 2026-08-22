package com.replens.feature.workout.ui.picker

internal enum class CameraAccess {
    Granted,

    /** The system will show its own dialog, so nothing needs explaining. */
    Requestable,

    /** The system has stopped asking; only Settings can turn it back on. */
    Blocked,
}

/**
 * What the app may do about the camera right now, from the two readings only a
 * composable can take plus one bit that has to outlive the process.
 *
 * Android reports no "permanently denied" state: `shouldShowRequestPermissionRationale`
 * is false both before the first denial and after the system stops asking. A
 * *warranted* rationale is the only informative reading — Android offers it only
 * while it has a denial on record — so [record] copies it down the moment it
 * appears, and [Blocked] is that memory plus the system's silence.
 */
internal class CameraGate {

    private var isGranted = false
    private var canShowRationale = false
    private var hasDenied = false

    val access: CameraAccess
        get() = when {
            isGranted -> CameraAccess.Granted
            hasDenied && !canShowRationale -> CameraAccess.Blocked
            else -> CameraAccess.Requestable
        }

    /**
     * Or-ed rather than assigned: the read that supplies this suspends, and a
     * denial recorded while it was in flight must not be erased by an answer
     * taken before it happened.
     */
    fun restore(wasDenied: Boolean) {
        hasDenied = hasDenied || wasDenied
    }

    /** Answers whether this reading is worth persisting, which happens once. */
    fun record(isGranted: Boolean, canShowRationale: Boolean): Boolean {
        this.isGranted = isGranted
        this.canShowRationale = canShowRationale

        if (!canShowRationale || hasDenied) return false
        hasDenied = true
        return true
    }
}