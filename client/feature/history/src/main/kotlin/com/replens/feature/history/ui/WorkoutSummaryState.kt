package com.replens.feature.history.ui

import com.replens.feature.history.ui.model.DepthChartUiModel
import com.replens.feature.history.ui.model.SessionTotalsUiModel
import com.replens.feature.history.ui.model.SetRowUiModel

internal sealed interface WorkoutSummaryState {

    data object Loading : WorkoutSummaryState

    data object NotFound : WorkoutSummaryState

    data class Loaded(
        val totals: SessionTotalsUiModel,
        val depthChart: DepthChartUiModel?,
        val sets: List<SetRowUiModel>,
    ) : WorkoutSummaryState
}
