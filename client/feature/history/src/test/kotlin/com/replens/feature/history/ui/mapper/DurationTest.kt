package com.replens.feature.history.ui.mapper

import com.replens.core.text.UiText
import com.replens.feature.history.R
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DurationTest {

    @Test
    fun `under a minute reads in seconds`() {
        assertEquals(UiText.Resource(R.string.duration_seconds, 45), 45.seconds.toUiText())
    }

    @Test
    fun `under an hour reads in minutes`() {
        assertEquals(UiText.Resource(R.string.duration_minutes, 24), 24.minutes.toUiText())
    }

    @Test
    fun `an hour and over reads in both`() {
        val expected = UiText.Resource(R.string.duration_hours, 1, 24)

        assertEquals(expected, (1.hours + 24.minutes).toUiText())
    }

    /**
     * Truncating, not rounding. Coarse is fine in a summary; landing on the wrong
     * side of a minute is not, which is why the seconds arm covers 59 s rather
     * than letting it round up to "1 min".
     */
    @Test
    fun `part units are truncated, and the last second is still seconds`() {
        assertEquals(UiText.Resource(R.string.duration_minutes, 1), 119.seconds.toUiText())
        assertEquals(UiText.Resource(R.string.duration_seconds, 59), 59.seconds.toUiText())
    }

    /** Zero is a legal reading: two sets can start and end inside the same second. */
    @Test
    fun `zero reads in seconds`() {
        assertEquals(UiText.Resource(R.string.duration_seconds, 0), 0.seconds.toUiText())
    }

    /**
     * The minutes component, not the total — 61 minutes past the hour would be a
     * clock nobody can read.
     */
    @Test
    fun `the minutes part of an hour resets`() {
        assertEquals(UiText.Resource(R.string.duration_hours, 2, 5), (125.minutes).toUiText())
    }
}
