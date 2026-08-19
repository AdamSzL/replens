package com.replens.core.designsystem.component.appbar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.component.button.RepLensIconButton
import com.replens.core.designsystem.icon.RepLensIcons
import com.replens.core.designsystem.preview.RepLensPreview
import com.replens.core.designsystem.theme.RepLensTheme

@Composable
fun RepLensLargeTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.topAppBarContainer(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = RepLensTheme.typography.title,
            color = RepLensTheme.colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        actions()
    }
}

@PreviewLightDark
@Composable
private fun RepLensLargeTopAppBarPreview() {
    RepLensPreview {
        RepLensLargeTopAppBar(title = "History") {
            RepLensIconButton(
                icon = RepLensIcons.Settings,
                contentDescription = "Settings",
                onClick = {},
            )
        }
    }
}

@Preview(fontScale = 2f)
@Composable
private fun RepLensLargeTopAppBarLongTitlePreview() {
    RepLensPreview {
        RepLensLargeTopAppBar(title = "Workout history and statistics") {
            RepLensIconButton(
                icon = RepLensIcons.Settings,
                contentDescription = "Settings",
                onClick = {},
            )
        }
    }
}
