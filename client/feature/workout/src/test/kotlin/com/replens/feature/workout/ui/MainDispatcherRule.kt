package com.replens.feature.workout.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * `viewModelScope` is `Dispatchers.Main`, which does not exist on the JVM.
 *
 * Unconfined rather than Standard so a frame emitted by the test is processed
 * before the next line runs — the ViewModel's pipeline is synchronous per frame,
 * and pretending otherwise would only mean an `advanceUntilIdle()` after every
 * emission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MainDispatcherRule : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
