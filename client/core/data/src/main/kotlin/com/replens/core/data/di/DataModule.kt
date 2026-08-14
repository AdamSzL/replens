package com.replens.core.data.di

import com.replens.core.data.WorkoutRepository
import com.replens.core.data.WorkoutRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    abstract fun workoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository
}
