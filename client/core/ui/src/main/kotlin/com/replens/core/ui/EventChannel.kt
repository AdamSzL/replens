package com.replens.core.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Sends one-shot UI events through a buffered channel owned by [scope].
 *
 * This type owns only event transport. It deliberately does not own screen state
 * or action dispatch. [events] is a single-consumer stream: concurrent collectors
 * divide events between them rather than each receiving every event.
 */
class EventChannel<E>(private val scope: CoroutineScope) {

    private val channel = Channel<E>(Channel.BUFFERED)

    val events: Flow<E> = channel.receiveAsFlow()

    fun send(event: E) {
        scope.launch {
            channel.send(event)
        }
    }
}
