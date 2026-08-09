package com.replens.feature.workout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.component.button.OverlayPrimaryButton
import com.replens.core.designsystem.component.button.OverlaySecondaryButton
import com.replens.core.designsystem.theme.RepLensTheme
import com.replens.core.exercise.SessionState
import com.replens.core.exercise.SetupCheck
import com.replens.feature.workout.R
import com.replens.feature.workout.ui.WorkoutAction

/**
 * The one control cluster the screen shows at a time, chosen by [SessionState].
 * Every arm is anchored to the same place, so the button under the user's thumb
 * does not jump as a set progresses.
 */
@Composable
internal fun SessionControls(
    session: SessionState,
    repCount: Int,
    onAction: (WorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (session) {
            SessionState.Idle -> OverlayPrimaryButton(
                text = stringResource(R.string.workout_start_set),
                onClick = { onAction(WorkoutAction.StartClicked) },
            )
            is SessionState.Waiting -> {
                OverlayMessage(stringResource(session.reason.messageRes))
                OverlaySecondaryButton(
                    text = stringResource(R.string.workout_cancel),
                    onClick = { onAction(WorkoutAction.CancelClicked) },
                )
            }
            is SessionState.CountingIn -> {
                Text(
                    text = session.secondsLeft.toString(),
                    style = RepLensTheme.typography.display,
                    color = RepLensTheme.colors.onOverlay,
                )
                OverlaySecondaryButton(
                    text = stringResource(R.string.workout_cancel),
                    onClick = { onAction(WorkoutAction.CancelClicked) },
                )
            }
            SessionState.Active -> OverlaySecondaryButton(
                text = stringResource(R.string.workout_finish),
                onClick = { onAction(WorkoutAction.FinishClicked) },
            )
            SessionState.Finished -> SetSummary(repCount = repCount, onAction = onAction)
        }
    }
}

/**
 * The reason is chosen here rather than in the ViewModel: each arm maps to one
 * fixed resource, so nothing is being *selected between* at runtime and there is
 * no need for a resolved string in state. That changes when TTS speaks the same
 * line and it has to be resolvable without a composition.
 */
private val SetupCheck.messageRes: Int
    get() = when (this) {
        SetupCheck.READY -> R.string.workout_setup_hold_still
        SetupCheck.NO_POSE -> R.string.workout_setup_no_pose
        SetupCheck.TOO_CLOSE -> R.string.workout_setup_too_close
        SetupCheck.LEGS_NOT_VISIBLE -> R.string.workout_setup_legs_not_visible
    }

@Composable
private fun OverlayMessage(text: String) {
    Text(
        text = text,
        style = RepLensTheme.typography.title,
        color = RepLensTheme.colors.onOverlay,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(RepLensTheme.colors.overlayScrim)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Composable
private fun SetSummary(
    repCount: Int,
    onAction: (WorkoutAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(RepLensTheme.colors.overlaySurface)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = repCount.toString(),
            style = RepLensTheme.typography.display,
            color = RepLensTheme.colors.onOverlay,
        )
        Text(
            text = stringResource(R.string.workout_reps_squat),
            style = RepLensTheme.typography.label,
            color = RepLensTheme.colors.onOverlayMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverlaySecondaryButton(
                text = stringResource(R.string.workout_done),
                onClick = { onAction(WorkoutAction.DoneClicked) },
            )
            OverlayPrimaryButton(
                text = stringResource(R.string.workout_go_again),
                onClick = { onAction(WorkoutAction.GoAgainClicked) },
            )
        }
    }
}
@Preview
@Composable
private fun SessionControlsIdlePreview() {
    RepLensTheme {
        SessionControls(session = SessionState.Idle, repCount = 0, onAction = {})
    }
}
@Preview
@Composable
private fun SessionControlsWaitingPreview() {
    RepLensTheme {
        SessionControls(
            session = SessionState.Waiting(SetupCheck.LEGS_NOT_VISIBLE),
            repCount = 0,
            onAction = {},
        )
    }
}
@Preview
@Composable
private fun SessionControlsCountingInPreview() {
    RepLensTheme {
        SessionControls(session = SessionState.CountingIn(3), repCount = 0, onAction = {})
    }
}
@Preview
@Composable
private fun SessionControlsFinishedPreview() {
    RepLensTheme {
        SessionControls(session = SessionState.Finished, repCount = 12, onAction = {})
    }
}
