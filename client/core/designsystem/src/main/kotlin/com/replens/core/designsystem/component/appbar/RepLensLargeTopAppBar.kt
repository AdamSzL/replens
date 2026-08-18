package com.replens.core.designsystem.component.appbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
fun RepLensLargeTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.topAppBarContainer(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = RepLensTheme.typography.title,
            color = RepLensTheme.colors.onBackground,
            modifier = Modifier.padding(start = 12.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        actions()
    }
}

@Preview
@Composable
private fun RepLensLargeTopAppBarLightThemePreview() {
    RepLensTheme(darkTheme = false) {
        RepLensLargeTopAppBar(
            title = "History",
            modifier = Modifier.background(RepLensTheme.colors.background),
        ) {
            RepLensIconButton(
                icon = RepLensIcons.Settings,
                contentDescription = "Settings",
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun RepLensLargeTopAppBarDarkThemePreview() {
    RepLensTheme(darkTheme = true) {
        RepLensLargeTopAppBar(
            title = "History",
            modifier = Modifier.background(RepLensTheme.colors.background),
        ) {
            RepLensIconButton(
                icon = RepLensIcons.Settings,
                contentDescription = "Settings",
                onClick = {},
            )
        }
    }
}
