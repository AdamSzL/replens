package com.replens.feature.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replens.core.designsystem.component.appbar.RepLensTopAppBar
import com.replens.core.designsystem.theme.RepLensTheme
import com.replens.core.text.UiText
import com.replens.core.ui.ScreenStateCrossfade
import com.replens.feature.history.R
import com.replens.feature.history.ui.components.SessionTotals
import com.replens.feature.history.ui.components.SetList
import com.replens.feature.history.ui.components.StillOpenNote
import com.replens.feature.history.ui.model.SessionTotalsUiModel
import com.replens.feature.history.ui.model.SetRowUiModel

@Composable
internal fun WorkoutSummaryRoot(
    workoutId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<WorkoutSummaryViewModel, WorkoutSummaryViewModel.Factory>(
        creationCallback = { factory -> factory.create(workoutId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    WorkoutSummaryScreen(
        state = state,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun WorkoutSummaryScreen(
    state: WorkoutSummaryState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RepLensTheme.colors.background),
    ) {
        RepLensTopAppBar(
            title = stringResource(R.string.workout_summary_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.workout_summary_back),
        )
        ScreenStateCrossfade(
            targetState = state,
            modifier = Modifier.fillMaxSize(),
        ) { screenState ->
            when (screenState) {
                WorkoutSummaryState.Loading -> Box(Modifier.fillMaxSize())

                WorkoutSummaryState.NotFound -> WorkoutNotFound()

                is WorkoutSummaryState.Loaded -> LoadedWorkout(state = screenState)
            }
        }
    }
}

@Composable
private fun LoadedWorkout(
    state: WorkoutSummaryState.Loaded,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SessionTotals(
            totals = state.totals,
        )
        SetList(
            sets = state.sets,
        )
        if (state.totals.isOpen) {
            StillOpenNote(
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun WorkoutNotFound(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.workout_summary_not_found),
            style = RepLensTheme.typography.body,
            color = RepLensTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private val previewState = WorkoutSummaryState.Loaded(
    totals = SessionTotalsUiModel(
        setCount = 3,
        repCount = 30,
        repsAtDepth = 18,
        duration = UiText.Resource(R.string.duration_minutes, 24),
        isOpen = true,
    ),
    depthChart = null,
    sets = listOf(
        SetRowUiModel(
            id = 1,
            index = 1,
            exercise = UiText.Resource(R.string.exercise_squat),
            repCount = 12,
            repsAtDepth = 10,
            duration = UiText.Resource(R.string.duration_seconds, 48),
            restBefore = null,
            abandonedCount = 0,
        ),
        SetRowUiModel(
            id = 2,
            index = 2,
            exercise = UiText.Resource(R.string.exercise_squat),
            repCount = 10,
            repsAtDepth = 7,
            duration = UiText.Resource(R.string.duration_seconds, 44),
            restBefore = UiText.Resource(R.string.duration_minutes, 3),
            abandonedCount = 1,
        ),
        SetRowUiModel(
            id = 3,
            index = 3,
            exercise = UiText.Resource(R.string.exercise_squat),
            repCount = 8,
            repsAtDepth = 4,
            duration = UiText.Resource(R.string.duration_seconds, 39),
            restBefore = UiText.Resource(R.string.duration_minutes, 4),
            abandonedCount = 3,
        ),
    ),
)

@Preview
@Composable
private fun WorkoutSummaryScreenLightThemePreview() {
    RepLensTheme(darkTheme = false) {
        WorkoutSummaryScreen(state = previewState, onBack = {})
    }
}

@Preview
@Composable
private fun WorkoutSummaryScreenDarkThemePreview() {
    RepLensTheme(darkTheme = true) {
        WorkoutSummaryScreen(state = previewState, onBack = {})
    }
}

@Preview
@Composable
private fun WorkoutSummaryNotFoundPreview() {
    RepLensTheme {
        WorkoutSummaryScreen(state = WorkoutSummaryState.NotFound, onBack = {})
    }
}
