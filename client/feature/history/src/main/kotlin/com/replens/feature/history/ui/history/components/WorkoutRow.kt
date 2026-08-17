package com.replens.feature.history.ui.history.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.component.card.RepLensCard
import com.replens.core.designsystem.theme.RepLensTheme
import com.replens.core.text.UiText
import com.replens.core.ui.asString
import com.replens.feature.history.R
import com.replens.feature.history.ui.history.model.WorkoutDay
import com.replens.feature.history.ui.history.model.WorkoutRowUiModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun WorkoutRow(
    workout: WorkoutRowUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RepLensCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = workout.day.asLabel(),
                    style = RepLensTheme.typography.heading,
                    color = RepLensTheme.colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = workout.startedAt.asClockTime(),
                    style = RepLensTheme.typography.body,
                    color = RepLensTheme.colors.onSurfaceMuted,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = pluralStringResource(
                    R.plurals.workout_reps_at_depth,
                    workout.repCount,
                    workout.repCount,
                    workout.repsAtDepth,
                ),
                style = RepLensTheme.typography.body,
                color = RepLensTheme.colors.accent,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.workout_sets_duration,
                    workout.setCount,
                    workout.setCount,
                    workout.duration.asString(),
                ),
                style = RepLensTheme.typography.body,
                color = RepLensTheme.colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun WorkoutDay.asLabel(): String {
    return when (this) {
        WorkoutDay.Today -> stringResource(R.string.history_today)
        WorkoutDay.Yesterday -> stringResource(R.string.history_yesterday)
        is WorkoutDay.Weekday -> day.getDisplayName(TextStyle.FULL, locale())
        is WorkoutDay.Earlier -> date.format(rememberDateTimeFormatter(DATE_SKELETON))
    }
}

@Composable
private fun LocalTime.asClockTime(): String {
    val skeleton = if (DateFormat.is24HourFormat(LocalContext.current)) "Hm" else "hm"
    return format(rememberDateTimeFormatter(skeleton))
}

@Composable
private fun rememberDateTimeFormatter(skeleton: String): DateTimeFormatter {
    val locale = locale()
    return remember(locale, skeleton) {
        DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(locale, skeleton),
            locale,
        )
    }
}

@Composable
private fun locale(): Locale = LocalLocale.current.platformLocale

private const val DATE_SKELETON = "dMMM"

private val previewWorkout = WorkoutRowUiModel(
    id = 1,
    day = WorkoutDay.Today,
    startedAt = LocalTime.of(18, 24),
    setCount = 3,
    repCount = 30,
    repsAtDepth = 18,
    duration = UiText.Resource(R.string.duration_minutes, 24),
)

@Preview
@Composable
private fun WorkoutRowLightThemePreview() {
    RepLensTheme(darkTheme = false) {
        WorkoutRow(workout = previewWorkout, onClick = {})
    }
}

@Preview
@Composable
private fun WorkoutRowDarkThemePreview() {
    RepLensTheme(darkTheme = true) {
        WorkoutRow(workout = previewWorkout, onClick = {})
    }
}

/** The three labels that are not "Today", and the one that has to wrap. */
@Preview
@Composable
private fun WorkoutRowDayLabelsPreview() {
    RepLensTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkoutRow(
                workout = previewWorkout.copy(day = WorkoutDay.Yesterday),
                onClick = {},
            )
            WorkoutRow(
                workout = previewWorkout.copy(day = WorkoutDay.Weekday(DayOfWeek.WEDNESDAY)),
                onClick = {},
            )
            WorkoutRow(
                workout = previewWorkout.copy(
                    day = WorkoutDay.Earlier(LocalDate.of(2026, 8, 10)),
                ),
                onClick = {},
            )
        }
    }
}

@Preview(fontScale = 2f)
@Composable
private fun WorkoutRowLargeFontPreview() {
    RepLensTheme(darkTheme = true) {
        WorkoutRow(
            workout = previewWorkout.copy(day = WorkoutDay.Weekday(DayOfWeek.WEDNESDAY)),
            onClick = {},
        )
    }
}
