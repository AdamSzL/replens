package com.replens.feature.workout.domain

internal interface CameraPermissionRepository {

    suspend fun hasBeenDenied(): Boolean

    suspend fun markDenied()
}
