package com.replens.feature.workout.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.replens.feature.workout.domain.CameraPermissionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private val CameraPermissionDeniedKey = booleanPreferencesKey("camera_permission_denied")

internal class CameraPermissionRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : CameraPermissionRepository {

    override suspend fun hasBeenDenied(): Boolean {
        return dataStore.data.first()[CameraPermissionDeniedKey] ?: false
    }

    override suspend fun markDenied() {
        dataStore.edit { preferences -> preferences[CameraPermissionDeniedKey] = true }
    }
}
