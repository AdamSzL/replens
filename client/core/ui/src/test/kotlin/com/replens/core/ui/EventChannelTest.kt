package com.replens.core.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventChannelTest {

    @Test
    fun `an event sent before collection is delivered`() = runTest {
        val eventChannel = EventChannel<String>(this)

        eventChannel.send("event")
        advanceUntilIdle()

        assertEquals("event", eventChannel.events.take(1).toList().single())
    }

    @Test
    fun `events are delivered in send order`() = runTest {
        val eventChannel = EventChannel<Int>(this)

        eventChannel.send(1)
        eventChannel.send(2)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), eventChannel.events.take(2).toList())
    }
}
