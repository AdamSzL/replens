package com.replens.core.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PreferencesDataSourceTest {

    private val dataStore = FakeDataStore()
    private val preferences = PreferencesDataSource(dataStore)

    private val key = booleanPreferencesKey("flag")

    @Test
    fun `a written value reads back`() = runTest {
        preferences.write(key, true)

        assertTrue(preferences.read(key, default = false))
    }

    @Test
    fun `an unset key reads as the default`() = runTest {
        assertTrue(preferences.read(key, default = true))
    }

    /** An unreadable file and an unset key are the same answer, given once at the call site. */
    @Test
    fun `an unreadable file reads as the default`() = runTest {
        dataStore.failure = IOException()

        assertTrue(preferences.read(key, default = true))
    }

    /** Absorbing IO is a policy about the disk, not a blanket catch. */
    @Test(expected = IllegalStateException::class)
    fun `a failure that is not IO propagates`() = runTest {
        dataStore.failure = IllegalStateException()

        preferences.read(key, default = false)
    }

    @Test
    fun `a write that reaches disk reports success`() = runTest {
        assertTrue(preferences.write(key, true))
    }

    /** The one thing the caller has to be told, because only it knows what was lost. */
    @Test
    fun `a write that fails reports it rather than throwing`() = runTest {
        dataStore.failure = IOException()

        assertFalse(preferences.write(key, true))
    }
}
