package com.replens.core.designsystem.component.navigationbar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.replens.core.designsystem.icon.RepLensIcons
import com.replens.core.designsystem.preview.RepLensPreview
import com.replens.core.designsystem.theme.RepLensTheme

@Composable
fun RepLensNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = RepLensTheme.colors.surface,
        contentColor = RepLensTheme.colors.onSurface,
        content = content,
    )
}

@Composable
fun RowScope.RepLensNavigationBarItem(
    @DrawableRes icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
            )
        },
        modifier = modifier,
        label = {
            Text(
                text = label,
                style = RepLensTheme.typography.label,
            )
        },
        colors = NavigationBarItemColors(
            selectedIconColor = RepLensTheme.colors.onAccent,
            selectedTextColor = RepLensTheme.colors.onSurface,
            selectedIndicatorColor = RepLensTheme.colors.accent,
            unselectedIconColor = RepLensTheme.colors.onSurfaceMuted,
            unselectedTextColor = RepLensTheme.colors.onSurfaceMuted,
            disabledIconColor = RepLensTheme.colors.onSurfaceMuted,
            disabledTextColor = RepLensTheme.colors.onSurfaceMuted,
        ),
    )
}

@PreviewLightDark
@Composable
private fun RepLensNavigationBarPreview() {
    RepLensPreview {
        RepLensNavigationBar {
            RepLensNavigationBarItem(
                icon = RepLensIcons.History,
                label = "History",
                selected = true,
                onClick = {},
            )
            RepLensNavigationBarItem(
                icon = RepLensIcons.Exercise,
                label = "Workout",
                selected = false,
                onClick = {},
            )
        }
    }
}
