package com.replens.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavKey
import com.replens.core.designsystem.component.navigationbar.RepLensNavigationBar
import com.replens.core.designsystem.component.navigationbar.RepLensNavigationBarItem
import com.replens.core.designsystem.theme.RepLensTheme
import com.replens.feature.history.navigation.HistoryRoute

@Composable
fun RepLensBottomNavigation(
    selectedRoute: NavKey,
    onDestinationClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    RepLensNavigationBar(modifier = modifier) {
        TopLevelDestination.entries.forEach { destination ->
            RepLensNavigationBarItem(
                icon = destination.icon,
                label = stringResource(destination.label),
                selected = destination.route == selectedRoute,
                onClick = { onDestinationClick(destination.route) },
            )
        }
    }
}

@Preview
@Composable
private fun RepLensBottomNavigationLightThemePreview() {
    RepLensTheme(darkTheme = false) {
        RepLensBottomNavigation(
            selectedRoute = HistoryRoute,
            onDestinationClick = {},
        )
    }
}

@Preview
@Composable
private fun RepLensBottomNavigationDarkThemePreview() {
    RepLensTheme(darkTheme = true) {
        RepLensBottomNavigation(
            selectedRoute = HistoryRoute,
            onDestinationClick = {},
        )
    }
}
