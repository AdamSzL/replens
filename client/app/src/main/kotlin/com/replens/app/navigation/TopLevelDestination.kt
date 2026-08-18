package com.replens.app.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.replens.app.R
import com.replens.core.designsystem.icon.RepLensIcons
import com.replens.feature.history.navigation.HistoryRoute
import com.replens.feature.workout.navigation.WorkoutRoute

enum class TopLevelDestination(
    val route: NavKey,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    History(
        route = HistoryRoute,
        label = R.string.tab_history,
        icon = RepLensIcons.History,
    ),
    Workout(
        route = WorkoutRoute,
        label = R.string.tab_workout,
        icon = RepLensIcons.Exercise,
    ),
}
