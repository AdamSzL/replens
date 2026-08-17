package com.replens.feature.history.ui.model

import com.replens.core.text.UiText
import java.time.LocalTime

internal data class WorkoutRowUiModel(
    val id: Long,
    val day: WorkoutDay,
    val startedAt: LocalTime,
    val setCount: Int,
    val repCount: Int,
    val repsAtDepth: Int,
    val duration: UiText,
)
