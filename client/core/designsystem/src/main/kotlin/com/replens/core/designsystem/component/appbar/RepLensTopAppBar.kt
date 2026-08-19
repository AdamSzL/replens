package com.replens.core.designsystem.component.appbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.component.button.RepLensIconButton
import com.replens.core.designsystem.icon.RepLensIcons
import com.replens.core.designsystem.theme.RepLensTheme

@Composable
fun RepLensTopAppBar(
    title: String,
    onBack: () -> Unit,
    backContentDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.topAppBarContainer(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RepLensIconButton(
            icon = RepLensIcons.ArrowBack,
            contentDescription = backContentDescription,
            onClick = onBack,
        )
        Text(
            text = title,
            style = RepLensTheme.typography.heading,
            color = RepLensTheme.colors.onBackground,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Preview
@Composable
private fun RepLensTopAppBarLightThemePreview() {
    RepLensTheme(darkTheme = false) {
        RepLensTopAppBar(
            title = "Workout",
            onBack = {},
            backContentDescription = "Back",
            modifier = Modifier.background(RepLensTheme.colors.background),
        )
    }
}

@Preview
@Composable
private fun RepLensTopAppBarDarkThemePreview() {
    RepLensTheme(darkTheme = true) {
        RepLensTopAppBar(
            title = "Workout",
            onBack = {},
            backContentDescription = "Back",
            modifier = Modifier.background(RepLensTheme.colors.background),
        )
    }
}
