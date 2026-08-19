package com.replens.app.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

// Routes of the app's own, so the test describes Navigator's rules rather than
// today's destination graph.
private data object TabA : NavKey
private data object TabB : NavKey
private data object Detail : NavKey

private fun navigationState(): NavigationState {
    return NavigationState(
        startRoute = TabA,
        topLevelRoute = mutableStateOf(TabA),
        backStacks = mapOf<NavKey, NavBackStack<NavKey>>(
            TabA to NavBackStack(TabA),
            TabB to NavBackStack(TabB),
        ),
    )
}

class NavigatorTest {

    @Test
    fun `navigating to a top level route switches tab instead of pushing`() {
        val state = navigationState()
        val navigator = Navigator(state)

        navigator.navigate(TabB)

        assertEquals(TabB, state.topLevelRoute)
        assertEquals(listOf(TabA), state.backStacks.getValue(TabA))
        assertEquals(listOf(TabB), state.backStacks.getValue(TabB))
    }

    @Test
    fun `navigating to a regular route pushes onto the selected tab`() {
        val state = navigationState()
        val navigator = Navigator(state)

        navigator.navigate(TabB)
        navigator.navigate(Detail)

        assertEquals(listOf(TabB, Detail), state.backStacks.getValue(TabB))
        // The other tab is untouched — the point of a stack per tab.
        assertEquals(listOf(TabA), state.backStacks.getValue(TabA))
    }

    @Test
    fun `going back from a deep stack pops without leaving the tab`() {
        val state = navigationState()
        val navigator = Navigator(state)
        navigator.navigate(TabB)
        navigator.navigate(Detail)

        navigator.goBack()

        assertEquals(TabB, state.topLevelRoute)
        assertEquals(listOf(TabB), state.backStacks.getValue(TabB))
    }

    @Test
    fun `going back from a secondary tab root returns to the start route`() {
        val state = navigationState()
        val navigator = Navigator(state)
        navigator.navigate(TabB)

        navigator.goBack()

        assertEquals(TabA, state.topLevelRoute)
        // Exiting a tab leaves its stack alone, so returning to it resumes.
        assertEquals(listOf(TabB), state.backStacks.getValue(TabB))
    }

    @Test
    fun `a tab keeps its stack while another tab is selected`() {
        val state = navigationState()
        val navigator = Navigator(state)
        navigator.navigate(TabB)
        navigator.navigate(Detail)
        navigator.goBack()
        navigator.goBack()

        navigator.navigate(TabB)

        assertEquals(TabB, state.topLevelRoute)
        assertEquals(listOf(TabB), state.backStacks.getValue(TabB))
    }
}
