package com.replens.feature.workout.ui.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.component.button.PrimaryButton
import com.replens.core.designsystem.preview.RepLensPreview
import com.replens.core.designsystem.theme.RepLensTheme
import com.replens.feature.workout.ui.workout.mapper.actionRes
import com.replens.feature.workout.ui.workout.mapper.messageRes
import com.replens.feature.workout.ui.workout.mapper.titleRes
import com.replens.feature.workout.ui.workout.model.CameraProblem

@Composable
internal fun CameraProblemPanel(
    problem: CameraProblem,
    onOpenSettings: () -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RepLensTheme.colors.background)
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(problem.titleRes),
            style = RepLensTheme.typography.heading,
            color = RepLensTheme.colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(problem.messageRes),
            style = RepLensTheme.typography.body,
            color = RepLensTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(
            text = stringResource(problem.actionRes),
            onClick = when (problem) {
                CameraProblem.PermissionMissing -> onOpenSettings
                CameraProblem.InUse, CameraProblem.Broken -> onGoBack
            },
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun CameraProblemPanelBlockedPreview() {
    RepLensPreview {
        CameraProblemPanel(
            problem = CameraProblem.PermissionMissing,
            onOpenSettings = {},
            onGoBack = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun CameraProblemPanelInUsePreview() {
    RepLensPreview {
        CameraProblemPanel(
            problem = CameraProblem.InUse,
            onOpenSettings = {},
            onGoBack = {},
        )
    }
}
