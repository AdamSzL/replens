package com.replens.core.designsystem.component.button

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.replens.core.designsystem.theme.RepLensTheme

/**
 * The action a screen exists for, drawn over the camera feed.
 *
 * Identical to [PrimaryButton] in dark mode and lighter in light mode, which is
 * the whole difference between them: **a camera feed under a scrim is a dark
 * surface permanently**, so overlay colors are the dark-theme values frozen.
 * [PrimaryButton] inverts with the theme because our own background does; the
 * room behind this one does not care what theme is set.
 *
 * Reads 9.96:1 against a dark room and 8.65:1 on its label.
 */
@Composable
fun OverlayPrimaryButton(
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
            containerColor = RepLensTheme.colors.overlayAccent,
            contentColor = RepLensTheme.colors.onOverlayAccent,
        ),
    ) {
        Text(
            text = text,
            style = RepLensButtonDefaults.textStyle,
        )
    }
}

// Mid-gray stands in for the camera feed: previewing overlay colors on a
// flat white background says nothing about what they sit on.
@Preview(showBackground = true, backgroundColor = 0xFF6E7378)
@Composable
private fun OverlayPrimaryButtonPreview() {
    RepLensTheme {
        OverlayPrimaryButton(
            text = "Start set",
            onClick = {},
        )
    }
}
