package com.replens.feature.workout.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.replens.feature.workout.domain.CameraPermissionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private val CameraPermissionRequested = booleanPreferencesKey("camera_permission_requested")

internal class CameraPermissionRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : CameraPermissionRepository {

    override suspend fun hasRequestedBefore(): Boolean {
        return dataStore.data.first()[CameraPermissionRequested] ?: false
    }

    override suspend fun markRequested() {
        dataStore.edit { preferences -> preferences[CameraPermissionRequested] = true }
    }
}
