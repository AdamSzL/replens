package com.replens.core.testing

import kotlin.time.Clock
import kotlin.time.Instant

/** Mutable so a test can move time between two reads of the same state. */
class FakeClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
}
