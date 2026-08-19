package com.replens.feature.workout.domain

internal interface CameraPermissionRepository {

    suspend fun hasRequestedBefore(): Boolean

    suspend fun markRequested()
}
