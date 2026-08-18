package com.replens.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.replens.feature.workout.navigation.workoutEntries
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RepLensTheme {
                CameraPermissionGate()
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
                        navigateToSummary = { navigator.navigate(WorkoutSummaryRoute(it)) },
                        navigateToHistory = { navigator.navigate(HistoryRoute) },
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

@Composable
private fun CameraPermissionGate() {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    if (granted) {
        RepLensNavDisplay()
    } else {
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.CAMERA) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("RepLens uses the camera to watch your form and count your reps.")
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera access")
            }
        }
    }
}
