package com.replens.feature.workout.ui.workout

import com.replens.core.audio.Speaker
import com.replens.core.text.UiText

internal class FakeSpeaker : Speaker {
    val spoken = mutableListOf<UiText>()

    override fun speak(cue: UiText) {
        spoken += cue
    }
}
