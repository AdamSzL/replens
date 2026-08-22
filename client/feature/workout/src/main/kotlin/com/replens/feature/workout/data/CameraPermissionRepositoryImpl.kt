package com.replens.feature.workout.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.replens.core.datastore.PreferencesDataSource
import com.replens.feature.workout.domain.CameraPermissionRepository
import javax.inject.Inject

private val CameraPermissionDeniedKey = booleanPreferencesKey("camera_permission_denied")

internal class CameraPermissionRepositoryImpl @Inject constructor(
    private val preferences: PreferencesDataSource,
) : CameraPermissionRepository {

    override suspend fun hasBeenDenied(): Boolean {
        return preferences.read(CameraPermissionDeniedKey, false)
    }

    /** The result is dropped: losing this costs one tap, and the next denial rewrites it. */
    override suspend fun markDenied() {
        preferences.write(CameraPermissionDeniedKey, true)
    }
}
