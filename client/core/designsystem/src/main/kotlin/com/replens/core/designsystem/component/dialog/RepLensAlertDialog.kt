package com.replens.core.designsystem.component.dialog

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.component.button.RepLensTextButton
import com.replens.core.designsystem.preview.RepLensPreview
import com.replens.core.designsystem.theme.RepLensTheme

private val DialogShape = RoundedCornerShape(16.dp)

@Composable
fun RepLensAlertDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            RepLensTextButton(
                text = confirmText,
                onClick = onConfirm,
            )
        },
        modifier = modifier,
        dismissButton = {
            RepLensTextButton(
                text = dismissText,
                onClick = onDismiss,
                color = RepLensTheme.colors.onSurfaceMuted,
            )
        },
        title = {
            Text(
                text = title,
                style = RepLensTheme.typography.heading,
            )
        },
        text = {
            Text(
                text = message,
                style = RepLensTheme.typography.body,
            )
        },
        shape = DialogShape,
        containerColor = RepLensTheme.colors.surface,
        titleContentColor = RepLensTheme.colors.onSurface,
        textContentColor = RepLensTheme.colors.onSurfaceMuted,
        tonalElevation = 0.dp,
    )
}

@Preview
@Composable
private fun RepLensAlertDialogPreview() {
    RepLensPreview {
        RepLensAlertDialog(
            title = "Camera access is off",
            message = "RepLens needs the camera to watch your form. Turn it on in " +
                "Settings to start a workout.",
            confirmText = "Open settings",
            onConfirm = {},
            dismissText = "Not now",
            onDismiss = {},
        )
    }
}
