package com.replens.feature.history.ui.history

import com.replens.feature.history.ui.history.model.WorkoutRowUiModel

internal sealed interface HistoryState {

    data object Loading : HistoryState

    data object Empty : HistoryState

    data class Content(val workouts: List<WorkoutRowUiModel>) : HistoryState
}
