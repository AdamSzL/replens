package com.replens.feature.history.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.dropUnlessResumed
import com.replens.core.designsystem.component.appbar.RepLensTopAppBar
import com.replens.core.designsystem.theme.RepLensTheme
import com.replens.core.text.UiText
import com.replens.core.ui.ObserveAsEvents
import com.replens.core.ui.ScreenStateCrossfade
import com.replens.feature.history.R
import com.replens.feature.history.ui.history.components.WorkoutRow
import com.replens.feature.history.ui.history.model.WorkoutDay
import com.replens.feature.history.ui.history.model.WorkoutRowUiModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Composable
internal fun HistoryRoot(
    onBack: () -> Unit,
    onWorkoutClick: (workoutId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<HistoryViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            HistoryEvent.NavigateBack -> onBack()
            is HistoryEvent.NavigateToWorkout -> onWorkoutClick(event.workoutId)
        }
    }

    HistoryScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
private fun HistoryScreen(
    state: HistoryState,
    onAction: (HistoryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RepLensTheme.colors.background),
    ) {
        RepLensTopAppBar(
            title = stringResource(R.string.history_title),
            onBack = dropUnlessResumed {
                onAction(HistoryAction.BackClicked)
            },
            backContentDescription = stringResource(R.string.history_back),
        )
        ScreenStateCrossfade(
            targetState = state,
            modifier = Modifier.fillMaxSize(),
        ) { screenState ->
            when (screenState) {
                HistoryState.Loading -> Unit

                HistoryState.Empty -> HistoryEmpty()

                is HistoryState.Content -> WorkoutList(
                    workouts = screenState.workouts,
                    onWorkoutClick = { onAction(HistoryAction.WorkoutClicked(it)) },
                )
            }
        }
    }
}

@Composable
private fun WorkoutList(
    workouts: List<WorkoutRowUiModel>,
    onWorkoutClick: (workoutId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = workouts,
            key = { it.id },
        ) { workout ->
            WorkoutRow(
                workout = workout,
                onClick = dropUnlessResumed {
                    onWorkoutClick(workout.id)
                },
            )
        }
    }
}

@Composable
private fun HistoryEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.history_empty_title),
            style = RepLensTheme.typography.heading,
            color = RepLensTheme.colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.history_empty_body),
            style = RepLensTheme.typography.body,
            color = RepLensTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private fun previewRow(
    id: Long,
    day: WorkoutDay,
    startedAt: LocalTime,
    repCount: Int,
    repsAtDepth: Int,
): WorkoutRowUiModel {
    return WorkoutRowUiModel(
        id = id,
        day = day,
        startedAt = startedAt,
        setCount = 3,
        repCount = repCount,
        repsAtDepth = repsAtDepth,
        duration = UiText.Resource(R.string.duration_minutes, 24),
    )
}

private val previewState = HistoryState.Content(
    workouts = listOf(
        previewRow(
            id = 1,
            day = WorkoutDay.Today,
            startedAt = LocalTime.of(18, 24),
            repCount = 30,
            repsAtDepth = 18,
        ),
        previewRow(
            id = 2,
            day = WorkoutDay.Yesterday,
            startedAt = LocalTime.of(7, 5),
            repCount = 24,
            repsAtDepth = 21,
        ),
        previewRow(
            id = 3,
            day = WorkoutDay.Weekday(DayOfWeek.WEDNESDAY),
            startedAt = LocalTime.of(19, 40),
            repCount = 36,
            repsAtDepth = 12,
        ),
        previewRow(
            id = 4,
            day = WorkoutDay.ThisYear(LocalDate.of(2026, 8, 10)),
            startedAt = LocalTime.of(12, 15),
            repCount = 18,
            repsAtDepth = 9,
        ),
        previewRow(
            id = 5,
            day = WorkoutDay.Earlier(LocalDate.of(2025, 11, 2)),
            startedAt = LocalTime.of(9, 30),
            repCount = 21,
            repsAtDepth = 15,
        ),
    ),
)

@Preview
@Composable
private fun HistoryScreenLightThemePreview() {
    RepLensTheme(darkTheme = false) {
        HistoryScreen(state = previewState, onAction = {})
    }
}

@Preview
@Composable
private fun HistoryScreenDarkThemePreview() {
    RepLensTheme(darkTheme = true) {
        HistoryScreen(state = previewState, onAction = {})
    }
}

@Preview
@Composable
private fun HistoryEmptyPreview() {
    RepLensTheme {
        HistoryScreen(state = HistoryState.Empty, onAction = {})
    }
}

@Preview(fontScale = 2f)
@Composable
private fun HistoryScreenLargeFontPreview() {
    RepLensTheme(darkTheme = true) {
        HistoryScreen(state = previewState, onAction = {})
    }
}
