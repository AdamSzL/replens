package com.replens.core.designsystem.component.button

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.replens.core.designsystem.theme.RepLensTheme

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RepLensButtonDefaults.shape,
        contentPadding = RepLensButtonDefaults.contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = RepLensTheme.colors.accent,
            contentColor = RepLensTheme.colors.onAccent,
        ),
    ) {
        Text(
            text = text,
            style = RepLensButtonDefaults.textStyle,
        )
    }
}

@Preview
@Composable
private fun PrimaryButtonLightThemePreview() {
    RepLensTheme(darkTheme = false) {
        PrimaryButton(
            text = "Save workout",
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun PrimaryButtonDarkThemePreview() {
    RepLensTheme(darkTheme = true) {
        PrimaryButton(
            text = "Save workout",
            onClick = {},
        )
    }
}
