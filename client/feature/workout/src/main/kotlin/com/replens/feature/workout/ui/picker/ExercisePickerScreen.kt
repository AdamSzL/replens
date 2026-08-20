package com.replens.feature.workout.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.replens.core.designsystem.component.appbar.RepLensLargeTopAppBar
import com.replens.core.designsystem.component.button.PrimaryButton
import com.replens.core.designsystem.preview.RepLensPreview
import com.replens.core.designsystem.theme.RepLensTheme
import com.replens.core.model.Exercise
import com.replens.feature.workout.R

@Composable
internal fun ExercisePickerRoot(
    navigateToWorkout: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExercisePickerScreen(
        onExerciseClick = navigateToWorkout,
        modifier = modifier,
    )
}

@Composable
private fun ExercisePickerScreen(
    onExerciseClick: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RepLensTheme.colors.background),
    ) {
        RepLensLargeTopAppBar(
            title = stringResource(R.string.exercise_picker_title),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            PrimaryButton(
                text = stringResource(R.string.exercise_squat),
                onClick = dropUnlessResumed { onExerciseClick(Exercise.SQUAT) },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ExercisePickerScreenPreview() {
    RepLensPreview {
        ExercisePickerScreen(onExerciseClick = {})
    }
}
