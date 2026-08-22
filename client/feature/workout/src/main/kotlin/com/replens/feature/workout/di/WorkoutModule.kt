package com.replens.feature.workout.di

import com.replens.feature.workout.data.CameraPermissionRepositoryImpl
import com.replens.feature.workout.domain.CameraPermissionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WorkoutModule {

    @Binds
    abstract fun cameraPermissionRepository(
        impl: CameraPermissionRepositoryImpl,
    ): CameraPermissionRepository
}
