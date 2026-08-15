package com.replens.core.testing

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
 * Unconfined rather than Standard because `viewModelScope` is
 * `Dispatchers.Main.immediate`, which skips dispatch entirely when it is already
 * on the main thread — so a `launch` really does start running before the calling
 * line returns, and Standard would model a queue production does not have.
 *
 * What that does **not** do is collapse a suspension that would really suspend.
 * A repository reaching disk leaves the screen on its loading state, so a fake
 * that returns without suspending is the thing that diverges — which is what the
 * `inFlight` gates on [FakeWorkoutRepository] are for, not the dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
