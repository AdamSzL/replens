package com.replens.feature.workout.ui

import com.replens.core.audio.Speaker
import com.replens.core.ui.UiText

internal class FakeSpeaker : Speaker {
    val spoken = mutableListOf<UiText>()

    override fun speak(cue: UiText) {
        spoken += cue
    }
}
