package com.replens.core.pose.di

import com.replens.core.pose.CameraXPoseCameraDataSource
import com.replens.core.pose.PoseCameraDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PoseModule {

    @Binds
    abstract fun poseCameraDataSource(impl: CameraXPoseCameraDataSource): PoseCameraDataSource
}
