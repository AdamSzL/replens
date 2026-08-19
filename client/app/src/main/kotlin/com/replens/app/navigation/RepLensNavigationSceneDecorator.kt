package com.replens.app.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneDecoratorStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.replens.core.ui.TOP_LEVEL_NAV_METADATA_KEY

private const val BOTTOM_BAR_SHARED_KEY = "replens_bottom_bar"

internal class RepLensNavigationSceneDecoratorStrategy<T : Any>(
    private val navBar: @Composable () -> Unit,
    private val sharedTransitionScope: SharedTransitionScope,
) : SceneDecoratorStrategy<T> {

    override fun SceneDecoratorStrategyScope<T>.decorateScene(scene: Scene<T>): Scene<T> {
        return RepLensNavigationScene(
            scene = scene,
            navBar = navBar,
            sharedTransitionScope = sharedTransitionScope,
        )
    }
}

@Composable
internal fun rememberRepLensNavigationSceneDecoratorStrategy(
    navBar: @Composable () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
): RepLensNavigationSceneDecoratorStrategy<NavKey> {
    val currentNavBar by rememberUpdatedState(navBar)
    val stableNavBar = remember { @Composable { currentNavBar() } }

    return remember(sharedTransitionScope) {
        RepLensNavigationSceneDecoratorStrategy(
            navBar = stableNavBar,
            sharedTransitionScope = sharedTransitionScope,
        )
    }
}

private class RepLensNavigationScene<T : Any>(
    private val scene: Scene<T>,
    private val navBar: @Composable () -> Unit,
    private val sharedTransitionScope: SharedTransitionScope,
) : Scene<T> by scene {

    override val key: Any = scene::class to scene.key

    override val content: @Composable () -> Unit = {
        val isTopLevel = scene.entries.any { entry ->
            entry.metadata.containsKey(TOP_LEVEL_NAV_METADATA_KEY)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isTopLevel) {
                            Modifier.consumeWindowInsets(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                            )
                        } else {
                            Modifier
                        }
                    ),
            ) {
                scene.content()
            }
            if (isTopLevel) {
                BottomBarSlot(
                    navBar = navBar,
                    sharedTransitionScope = sharedTransitionScope,
                )
            }
        }
    }
}

@Composable
private fun BottomBarSlot(
    navBar: @Composable () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
) {
    with(sharedTransitionScope) {
        Box(
            modifier = Modifier.sharedElement(
                rememberSharedContentState(BOTTOM_BAR_SHARED_KEY),
                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            ),
        ) {
            navBar()
        }
    }
}
