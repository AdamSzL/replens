package com.replens.core.audio.di

import com.replens.core.audio.Speaker
import com.replens.core.audio.TtsSpeaker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AudioModule {

    @Binds
    abstract fun bindSpeaker(speaker: TtsSpeaker): Speaker
}
