package com.replens.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject

/**
 * Reads and writes single preferences, and owns what an [IOException] means — the
 * other half of the corruption handler this module already installs.
 *
 * The two directions answer it differently on purpose. A read cannot fail in a way
 * the caller has not already answered, since [read] is handed the value to stand in
 * for "no value". A failed write has no such answer: reverting a switch, ignoring
 * it, or telling the user are all correct somewhere, so it is reported instead.
 */
class PreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** [default] covers both an unset key and an unreadable file. */
    suspend fun <T> read(key: Preferences.Key<T>, default: T): T {
        return dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()[key] ?: default
    }

    /** False when the value did not reach disk; what that costs is the caller's to know. */
    suspend fun <T> write(key: Preferences.Key<T>, value: T): Boolean {
        return try {
            dataStore.edit { preferences -> preferences[key] = value }
            true
        } catch (_: IOException) {
            false
        }
    }
}
