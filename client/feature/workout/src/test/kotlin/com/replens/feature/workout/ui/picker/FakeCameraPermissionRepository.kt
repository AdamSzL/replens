package com.replens.feature.workout.ui.picker

import com.replens.feature.workout.domain.CameraPermissionRepository
import kotlinx.coroutines.CompletableDeferred

internal class FakeCameraPermissionRepository : CameraPermissionRepository {

    var isDenied = false

    /** Held to model a tap arriving before the read has come back from disk. */
    var readInFlight: CompletableDeferred<Unit>? = null

    var markedDenied = false
        private set

    override suspend fun hasBeenDenied(): Boolean {
        readInFlight?.await()
        return isDenied
    }

    override suspend fun markDenied() {
        isDenied = true
        markedDenied = true
    }
}
