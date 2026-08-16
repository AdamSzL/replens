package com.replens.core.designsystem.component.button

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.replens.core.designsystem.icon.RepLensIcons
import com.replens.core.designsystem.theme.RepLensTheme

@Composable
fun RepLensIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = RepLensTheme.colors.onBackground,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = tint,
        ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

@Preview
@Composable
private fun RepLensIconButtonLightThemePreview() {
    RepLensTheme(darkTheme = false) {
        RepLensIconButton(
            icon = RepLensIcons.ArrowBack,
            contentDescription = "Back",
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun RepLensIconButtonDarkThemePreview() {
    RepLensTheme(darkTheme = true) {
        RepLensIconButton(
            icon = RepLensIcons.ArrowBack,
            contentDescription = "Back",
            onClick = {},
        )
    }
}
