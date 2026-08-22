package com.replens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.replens.app.navigation.Navigator
import com.replens.app.navigation.RepLensBottomNavigation
import com.replens.app.navigation.TopLevelDestination
import com.replens.app.navigation.rememberNavigationState
import com.replens.app.navigation.rememberRepLensNavigationSceneDecoratorStrategy
import com.replens.core.designsystem.theme.RepLensTheme
import com.replens.core.ui.FadeContentTransform
import com.replens.feature.history.navigation.HistoryRoute
import com.replens.feature.history.navigation.WorkoutSummaryRoute
import com.replens.feature.history.navigation.historyEntries
import com.replens.feature.workout.navigation.WorkoutRoute
import com.replens.feature.workout.navigation.workoutEntries
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RepLensTheme {
                RepLensNavDisplay()
            }
        }
    }
}

@Composable
private fun RepLensNavDisplay() {
    SharedTransitionLayout {
        val navigationState = rememberNavigationState(
            startRoute = HistoryRoute,
            topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet(),
        )
        val navigator = remember(navigationState) { Navigator(navigationState) }

        val navDecorator = rememberRepLensNavigationSceneDecoratorStrategy(
            navBar = {
                RepLensBottomNavigation(
                    selectedRoute = navigator.topLevelRoute,
                    onDestinationClick = navigator::navigate,
                )
            },
            sharedTransitionScope = this,
        )

        NavDisplay(
            entries = navigationState.toDecoratedEntries(
                entryProvider {
                    workoutEntries(
                        navigateToWorkout = { navigator.navigate(WorkoutRoute(it)) },
                        navigateToSummary = { navigator.navigate(WorkoutSummaryRoute(it)) },
                        onBack = navigator::goBack,
                    )
                    historyEntries(
                        onBack = navigator::goBack,
                        onWorkoutClick = { navigator.navigate(WorkoutSummaryRoute(it)) },
                    )
                }
            ),
            sceneDecoratorStrategies = listOf(navDecorator),
            sharedTransitionScope = this,
            transitionSpec = { FadeContentTransform },
            popTransitionSpec = { FadeContentTransform },
            onBack = navigator::goBack,
        )
    }
}
