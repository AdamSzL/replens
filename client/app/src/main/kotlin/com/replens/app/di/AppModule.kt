package com.replens.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import kotlin.time.Clock

@Module
@InstallIn(SingletonComponent::class)
internal object AppModule {

    @Provides
    fun clock(): Clock = Clock.System

    /**
     * Unscoped, so it is read per injection rather than pinned for the process:
     * the time zone is not part of `Configuration`, so nothing recreates when the
     * user changes it and a cached one would outlive the change.
     */
    @Provides
    fun zoneId(): ZoneId = ZoneId.systemDefault()
}
