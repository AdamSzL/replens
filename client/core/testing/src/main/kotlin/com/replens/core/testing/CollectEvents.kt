package com.replens.core.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Fills as events arrive, so a test can assert that none did. Unconfined
 * explicitly: [backgroundScope] inherits `runTest`'s *standard* dispatcher, so a
 * plain launch would leave the events buffered and unread until the scheduler
 * next ran — and these assertions are about the moment an event appears.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> TestScope.collectEvents(events: Flow<T>): List<T> {
    val collected = mutableListOf<T>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        events.toList(collected)
    }
    return collected
}
