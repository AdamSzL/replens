package com.replens.feature.workout.ui.picker

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.replens.core.designsystem.component.appbar.RepLensLargeTopAppBar
import com.replens.core.designsystem.component.button.PrimaryButton
import com.replens.core.designsystem.component.dialog.RepLensAlertDialog
import com.replens.core.designsystem.preview.RepLensPreview
import com.replens.core.designsystem.theme.RepLensTheme
import com.replens.core.model.Exercise
import com.replens.core.ui.ObserveAsEvents
import com.replens.feature.workout.R
import com.replens.feature.workout.ui.picker.mapper.labelRes

@Composable
internal fun ExercisePickerRoot(
    navigateToWorkout: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ExercisePickerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        viewModel.onAction(
            ExercisePickerAction.PermissionAnswered(
                isGranted = isGranted,
                canShowRationale = activity.canShowCameraRationale,
            )
        )
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ExercisePickerEvent.NavigateToWorkout -> navigateToWorkout(event.exercise)
            ExercisePickerEvent.RequestCameraPermission -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
            ExercisePickerEvent.OpenAppSettings -> activity?.openAppSettings()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onAction(
            ExercisePickerAction.ScreenResumed(
                isCameraGranted = context.isCameraGranted,
                canShowCameraRationale = activity.canShowCameraRationale,
            )
        )
    }

    ExercisePickerScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
private fun ExercisePickerScreen(
    state: ExercisePickerState,
    onAction: (ExercisePickerAction) -> Unit,
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
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Exercise.entries.forEach { exercise ->
                PrimaryButton(
                    text = stringResource(exercise.labelRes),
                    onClick = dropUnlessResumed {
                        onAction(ExercisePickerAction.ExerciseClicked(exercise))
                    },
                )
            }
        }
        Text(
            text = stringResource(R.string.exercise_picker_camera_disclosure),
            style = RepLensTheme.typography.body,
            color = RepLensTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }

    if (state.isCameraBlockedDialogVisible) {
        RepLensAlertDialog(
            title = stringResource(R.string.exercise_picker_camera_blocked_title),
            message = stringResource(R.string.exercise_picker_camera_blocked_message),
            confirmText = stringResource(R.string.exercise_picker_camera_blocked_confirm),
            onConfirm = { onAction(ExercisePickerAction.OpenSettingsClicked) },
            dismissText = stringResource(R.string.exercise_picker_camera_blocked_dismiss),
            onDismiss = { onAction(ExercisePickerAction.DialogDismissed) },
        )
    }
}

@PreviewLightDark
@Composable
private fun ExercisePickerScreenPreview() {
    RepLensPreview {
        ExercisePickerScreen(
            state = ExercisePickerState(),
            onAction = {},
        )
    }
}
