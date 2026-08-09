package com.replens.core.exercise

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Feeds a session a continuous stream at a fixed frame rate, so a test reads as
 * durations rather than frame counts — which is the same thing the machine
 * measures in.
 *
 * 20 fps by default: slower than production on purpose, to keep it obvious that
 * nothing here depends on the pipeline's actual rate.
 */
private class Driver(
    val session: SetSession,
    private val frameMillis: Long = 50L,
) {
    private var at = 0L

    fun feed(check: SetupCheck, forMillis: Long): SessionState {
        val until = at + forMillis
        while (at <= until) {
            session.onFrame(check, at)
            at += frameMillis
        }
        return session.state
    }

    /** Time passing with no frames delivered at all. */
    fun stall(forMillis: Long) {
        at += forMillis
    }
}

private fun session() = SetSession(countIn = 3.seconds, settleFor = 500.milliseconds)

private fun activeSession(): Driver {
    val driver = Driver(session())
    driver.session.start()
    driver.feed(SetupCheck.READY, forMillis = 500)
    driver.feed(SetupCheck.READY, forMillis = 3_000)
    check(driver.session.state == SessionState.Active) {
        "expected Active, was ${driver.session.state}"
    }
    return driver
}

class SetSessionTest {

    @Test
    fun `frames alone never start a set`() {
        val driver = Driver(session())
        driver.feed(SetupCheck.READY, forMillis = 10_000)
        assertEquals(SessionState.Idle, driver.session.state)
    }

    @Test
    fun `start waits rather than counting in`() {
        assertEquals(SessionState.Waiting(SetupCheck.NO_POSE), session().start())
    }

    @Test
    fun `the waiting reason follows the setup check`() {
        val driver = Driver(session())
        driver.session.start()
        assertEquals(
            SessionState.Waiting(SetupCheck.TOO_CLOSE),
            driver.feed(SetupCheck.TOO_CLOSE, forMillis = 200),
        )
        assertEquals(
            SessionState.Waiting(SetupCheck.LEGS_NOT_VISIBLE),
            driver.feed(SetupCheck.LEGS_NOT_VISIBLE, forMillis = 200),
        )
    }

    @Test
    fun `readiness must be sustained before counting in`() {
        val driver = Driver(session())
        driver.session.start()
        assertEquals(
            SessionState.Waiting(SetupCheck.READY),
            driver.feed(SetupCheck.READY, forMillis = 300),
        )
        assertEquals(
            SessionState.CountingIn(3),
            driver.feed(SetupCheck.READY, forMillis = 300),
        )
    }

    @Test
    fun `losing position mid-settle discards the time already banked`() {
        val driver = Driver(session())
        driver.session.start()
        driver.feed(SetupCheck.READY, forMillis = 400)
        driver.feed(SetupCheck.TOO_CLOSE, forMillis = 100)
        // Ready again, but only briefly — the earlier 400 ms must not carry over.
        assertEquals(
            SessionState.Waiting(SetupCheck.READY),
            driver.feed(SetupCheck.READY, forMillis = 300),
        )
    }

    @Test
    fun `the count-in runs down to active`() {
        val driver = Driver(session())
        driver.session.start()
        assertEquals(SessionState.CountingIn(3), driver.feed(SetupCheck.READY, forMillis = 500))
        assertEquals(SessionState.CountingIn(2), driver.feed(SetupCheck.READY, forMillis = 1_000))
        assertEquals(SessionState.CountingIn(1), driver.feed(SetupCheck.READY, forMillis = 1_000))
        assertEquals(SessionState.Active, driver.feed(SetupCheck.READY, forMillis = 1_000))
    }

    /**
     * The bug this replaces counted 14 *frames*, so half a second at 27 fps became
     * a full second at 15. Two rates, one behavior.
     */
    @Test
    fun `the settle time does not depend on the frame rate`() {
        for (frameMillis in listOf(20L, 50L, 125L)) {
            val driver = Driver(session(), frameMillis = frameMillis)
            driver.session.start()
            assertEquals(
                "at ${1000 / frameMillis} fps",
                SessionState.Waiting(SetupCheck.READY),
                driver.feed(SetupCheck.READY, forMillis = 300),
            )
            assertEquals(
                "at ${1000 / frameMillis} fps",
                SessionState.CountingIn(3),
                driver.feed(SetupCheck.READY, forMillis = 300),
            )
        }
    }

    @Test
    fun `leaving position mid-count restarts from the top`() {
        val driver = Driver(session())
        driver.session.start()
        driver.feed(SetupCheck.READY, forMillis = 500)
        assertEquals(SessionState.CountingIn(1), driver.feed(SetupCheck.READY, forMillis = 2_000))

        assertEquals(
            SessionState.Waiting(SetupCheck.TOO_CLOSE),
            driver.feed(SetupCheck.TOO_CLOSE, forMillis = 100),
        )
        // Back in position: three again, not one.
        assertEquals(SessionState.CountingIn(3), driver.feed(SetupCheck.READY, forMillis = 500))
        assertEquals(SessionState.CountingIn(1), driver.feed(SetupCheck.READY, forMillis = 2_000))
    }

    /**
     * The machine only sees the frames it is given, so without this a stalled
     * camera between two ready frames would read as sustained readiness.
     */
    @Test
    fun `a stalled stream does not count as readiness`() {
        val driver = Driver(session())
        driver.session.start()
        driver.feed(SetupCheck.READY, forMillis = 0)
        driver.stall(60_000)
        assertEquals(
            SessionState.Waiting(SetupCheck.READY),
            driver.feed(SetupCheck.READY, forMillis = 0),
        )
    }

    @Test
    fun `a stall during the count-in aborts it`() {
        val driver = Driver(session())
        driver.session.start()
        assertEquals(SessionState.CountingIn(3), driver.feed(SetupCheck.READY, forMillis = 500))

        driver.stall(5_000)
        assertEquals(
            SessionState.Waiting(SetupCheck.READY),
            driver.feed(SetupCheck.READY, forMillis = 0),
        )
    }

    /**
     * A backwards jump would otherwise strand `readySinceMillis` in the future and
     * the settle check could never pass again.
     */
    @Test
    fun `a timestamp jumping backwards does not strand the wait`() {
        val session = session()
        session.start()
        session.onFrame(SetupCheck.READY, 10_000)
        session.onFrame(SetupCheck.READY, 10_100)

        // The stream restarts on a lower time base, as a lens change could.
        var at = 0L
        repeat(20) {
            session.onFrame(SetupCheck.READY, at)
            at += 50
        }
        assertEquals(SessionState.CountingIn(3), session.state)
    }

    @Test
    fun `frames do not disturb an active set`() {
        val driver = activeSession()
        assertEquals(SessionState.Active, driver.feed(SetupCheck.TOO_CLOSE, forMillis = 5_000))
        driver.stall(60_000)
        assertEquals(SessionState.Active, driver.feed(SetupCheck.NO_POSE, forMillis = 1_000))
    }

    @Test
    fun `finish then dismiss returns to idle`() {
        val session = activeSession().session
        assertEquals(SessionState.Finished, session.finish())
        assertEquals(SessionState.Idle, session.dismiss())
    }

    @Test
    fun `going again from finished waits for position afresh`() {
        val driver = activeSession()
        driver.session.finish()
        assertEquals(SessionState.Waiting(SetupCheck.READY), driver.session.start())
        assertEquals(SessionState.CountingIn(3), driver.feed(SetupCheck.READY, forMillis = 500))
    }

    @Test
    fun `cancel abandons a count-in`() {
        val driver = Driver(session())
        driver.session.start()
        assertEquals(SessionState.CountingIn(3), driver.feed(SetupCheck.READY, forMillis = 500))
        assertEquals(SessionState.Idle, driver.session.cancel())
        // Idle ignores frames, so it stays put.
        assertEquals(SessionState.Idle, driver.feed(SetupCheck.READY, forMillis = 5_000))
    }

    @Test
    fun `cancel does nothing to an active set`() {
        assertEquals(SessionState.Active, activeSession().session.cancel())
    }

    @Test
    fun `finish does nothing before a set is running`() {
        val session = session()
        session.start()
        assertEquals(SessionState.Waiting(SetupCheck.NO_POSE), session.finish())
    }
}
