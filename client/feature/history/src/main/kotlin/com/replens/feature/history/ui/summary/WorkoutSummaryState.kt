package com.replens.feature.history.ui.summary

import com.replens.feature.history.ui.summary.model.DepthChartUiModel
import com.replens.feature.history.ui.summary.model.SessionTotalsUiModel
import com.replens.feature.history.ui.summary.model.SetRowUiModel

internal sealed interface WorkoutSummaryState {

    data object Loading : WorkoutSummaryState

    data object NotFound : WorkoutSummaryState

    data class Content(
        val totals: SessionTotalsUiModel,
        val depthChart: DepthChartUiModel?,
        val sets: List<SetRowUiModel>,
    ) : WorkoutSummaryState
}
