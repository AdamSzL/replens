package com.replens.feature.history.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
                set.restBefore?.let { RestDivider(rest = it) }
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
    val heading = set.exercise
        ?.let {
            stringResource(
                R.string.workout_summary_set_number_exercise,
                set.index,
                it.asString(),
            )
        }
        ?: stringResource(R.string.workout_summary_set_number, set.index)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = heading,
                style = RepLensTheme.typography.heading,
                color = RepLensTheme.colors.onSurface,
            )
            Text(
                text = set.duration.asString(),
                style = RepLensTheme.typography.body,
                color = RepLensTheme.colors.onSurfaceMuted,
            )
        }
        Text(
            text = pluralStringResource(
                R.plurals.workout_summary_set_reps,
                set.repCount,
                set.repCount,
                set.repsAtDepth,
            ),
            style = RepLensTheme.typography.body,
            color = RepLensTheme.colors.onSurfaceMuted,
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
private fun RestDivider(
    rest: UiText,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Hairline(
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.workout_summary_rest, rest.asString()),
            style = RepLensTheme.typography.label,
            color = RepLensTheme.colors.onSurfaceMuted,
        )
        Hairline(
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(RepLensTheme.colors.border),
    )
}

@Composable
private fun SetListPreview(
    darkTheme: Boolean,
    sets: List<SetRowUiModel>,
) {
    RepLensTheme(darkTheme = darkTheme) {
        SetList(
            sets = sets,
        )
    }
}

private fun setRow(
    index: Int,
    repCount: Int,
    repsAtDepth: Int,
    seconds: Int,
    restMinutes: Int?,
    abandonedCount: Int = 0,
    exercise: UiText? = null,
): SetRowUiModel {
    return SetRowUiModel(
        id = index.toLong(),
        index = index,
        exercise = exercise,
        repCount = repCount,
        repsAtDepth = repsAtDepth,
        duration = UiText.Resource(R.string.duration_seconds, seconds),
        restBefore = restMinutes?.let { UiText.Resource(R.string.duration_minutes, it) },
        abandonedCount = abandonedCount,
    )
}

private val session = listOf(
    setRow(index = 1, repCount = 12, repsAtDepth = 10, seconds = 64, restMinutes = null),
    setRow(
        index = 2,
        repCount = 10,
        repsAtDepth = 7,
        seconds = 58,
        restMinutes = 2,
        abandonedCount = 1,
    ),
    // One rep on purpose: the singular is a different string, and it reached a
    // screenshot reading "1 reps" because no preview ever showed a set this short.
    setRow(
        index = 3,
        repCount = 1,
        repsAtDepth = 1,
        seconds = 51,
        restMinutes = 3,
        abandonedCount = 1,
    ),
)

@Preview
@Composable
private fun SetListDarkPreview() = SetListPreview(darkTheme = true, sets = session)

@Preview
@Composable
private fun SetListLightPreview() = SetListPreview(darkTheme = false, sets = session)

/** A workout that mixed exercises, which is the only case that names them. */
@Preview
@Composable
private fun SetListMixedPreview() = SetListPreview(
    darkTheme = true,
    sets = listOf(
        setRow(
            index = 1,
            repCount = 12,
            repsAtDepth = 10,
            seconds = 64,
            restMinutes = null,
            exercise = UiText.Resource(R.string.exercise_squat),
        ),
        setRow(
            index = 2,
            repCount = 15,
            repsAtDepth = 15,
            seconds = 40,
            restMinutes = 2,
            exercise = UiText.Raw("Push-up"),
        ),
    ),
)

/** 200% font scale: the rest label wraps rather than crushing its rules. */
@Preview(fontScale = 2f)
@Composable
private fun SetListLargeFontPreview() = SetListPreview(darkTheme = true, sets = session)
