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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.component.card.RepLensCard
import com.replens.core.designsystem.theme.RepLensTheme
import com.replens.core.text.UiText
import com.replens.core.ui.asString
import com.replens.feature.history.R
import com.replens.feature.history.ui.model.SessionTotalsUiModel

@Composable
internal fun SessionTotals(
    totals: SessionTotalsUiModel,
    modifier: Modifier = Modifier,
) {
    RepLensCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = totals.repCount.toString(),
                    style = RepLensTheme.typography.display,
                    color = RepLensTheme.colors.onSurface,
                )
                Text(
                    text = pluralStringResource(R.plurals.workout_summary_reps, totals.repCount),
                    style = RepLensTheme.typography.title,
                    color = RepLensTheme.colors.onSurfaceMuted,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                )
            }
            Text(
                text = stringResource(
                    R.string.workout_summary_depth,
                    totals.repsAtDepth,
                    totals.repCount,
                ),
                style = RepLensTheme.typography.body,
                color = RepLensTheme.colors.accent,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.workout_summary_sets_duration,
                    totals.setCount,
                    totals.setCount,
                    totals.duration.asString(),
                ),
                style = RepLensTheme.typography.body,
                color = RepLensTheme.colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
internal fun StillOpenNote(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.workout_summary_still_open),
        style = RepLensTheme.typography.body,
        color = RepLensTheme.colors.onSurfaceMuted,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun SessionTotalsPreview() {
    RepLensTheme {
        Column {
            SessionTotals(
                totals = SessionTotalsUiModel(
                    setCount = 3,
                    repCount = 30,
                    repsAtDepth = 18,
                    duration = UiText.Resource(R.string.duration_minutes, 24),
                    isOpen = true,
                ),
            )
            StillOpenNote(
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
