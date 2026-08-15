package com.replens.feature.history.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.component.card.RepLensCard
import com.replens.core.designsystem.theme.RepLensTheme
import com.replens.core.text.UiText
import com.replens.core.ui.asString
import com.replens.feature.history.R
import com.replens.feature.history.ui.model.SetRowUiModel

@Composable
internal fun SetList(
    sets: List<SetRowUiModel>,
    modifier: Modifier = Modifier,
) {
    RepLensCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            sets.forEach { set ->
                set.restBefore?.let { RestBetweenSets(rest = it) }
                SetRow(
                    set = set,
                )
            }
        }
    }
}

@Composable
private fun SetRow(
    set: SetRowUiModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(R.string.workout_summary_set_number, set.index) +
                    " · " + set.exercise.asString(),
                style = RepLensTheme.typography.label,
                color = RepLensTheme.colors.onSurface,
            )
            Text(
                text = set.duration.asString(),
                style = RepLensTheme.typography.label,
                color = RepLensTheme.colors.onSurfaceMuted,
            )
        }
        Text(
            text = stringResource(
                R.string.workout_summary_set_reps,
                set.repCount,
                set.repsAtDepth,
            ),
            style = RepLensTheme.typography.body,
            color = RepLensTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
        // Only when there were any: a zero here would report a non-event, and the
        // whole reason these are stored is that they are evidence when they happen.
        if (set.abandonedCount > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.workout_summary_abandoned,
                    set.abandonedCount,
                    set.abandonedCount,
                ),
                style = RepLensTheme.typography.body,
                color = RepLensTheme.colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun RestBetweenSets(
    rest: UiText,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.workout_summary_rest, rest.asString()),
        style = RepLensTheme.typography.label,
        color = RepLensTheme.colors.onSurfaceMuted,
        modifier = modifier.padding(vertical = 12.dp),
    )
}

@Preview
@Composable
private fun SetListPreview() {
    RepLensTheme {
        SetList(
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
            ),
        )
    }
}
