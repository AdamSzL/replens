package com.replens.core.designsystem.component.button

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.replens.core.designsystem.preview.RepLensPreview
import com.replens.core.designsystem.theme.RepLensTheme

@Composable
fun RepLensTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = RepLensTheme.colors.accent,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        shape = RepLensButtonDefaults.shape,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) {
        Text(
            text = text,
            style = RepLensButtonDefaults.textStyle,
        )
    }
}

@PreviewLightDark
@Composable
private fun RepLensTextButtonPreview() {
    RepLensPreview {
        RepLensTextButton(
            text = "Open settings",
            onClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun RepLensTextButtonMutedPreview() {
    RepLensPreview {
        RepLensTextButton(
            text = "Not now",
            onClick = {},
            color = RepLensTheme.colors.onSurfaceMuted,
        )
    }
}
