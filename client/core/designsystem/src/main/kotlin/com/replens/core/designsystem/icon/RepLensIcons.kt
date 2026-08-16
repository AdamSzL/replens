package com.replens.core.designsystem.icon

import androidx.annotation.DrawableRes
import com.replens.core.designsystem.R

/**
 * Every icon the app uses, named once so feature modules never import a foreign
 * `R`. Vectors come from Material Symbols; there is no `material-icons-*`
 * dependency on purpose.
 *
 * Ids rather than `ImageVector`: they also work outside composition, where a
 * `@Composable` getter cannot go. Call sites use `painterResource`.
 */
object RepLensIcons {

    @DrawableRes
    val ArrowBack = R.drawable.ic_arrow_back

    @DrawableRes
    val CameraSwitch = R.drawable.ic_camera_switch
}
