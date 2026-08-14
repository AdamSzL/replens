package com.replens.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Collects one-shot events — navigation, a message shown once — from a ViewModel,
 * calling [onEvent] on the main thread. Belongs in a Root composable, where each
 * event maps to a navigation callback.
 *
 * **The source has to buffer.** Collection stops below STARTED, so whatever the
 * ViewModel sends while the screen is backgrounded must wait somewhere: a
 * `Channel(Channel.BUFFERED)` exposed as `receiveAsFlow()`. A bare `SharedFlow`
 * discards those events instead, leaving no trace of the ones that went missing.
 *
 * Two lines not to tidy away. `Dispatchers.Main.immediate` runs the callback on
 * the current frame; scheduling it instead loses events dispatched during
 * teardown, which is the user backgrounding the app mid-navigation.
 * `rememberUpdatedState` is what keeps [LaunchedEffect] keyed on the flow alone —
 * keying it on [onEvent] would re-subscribe whenever the lambda changes identity,
 * and capturing the lambda once would silently keep calling a stale one.
 */
@Composable
fun <T> ObserveAsEvents(flow: Flow<T>, onEvent: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEvent by rememberUpdatedState(onEvent)

    LaunchedEffect(flow, lifecycleOwner.lifecycle) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                flow.collect { currentOnEvent(it) }
            }
        }
    }
}
