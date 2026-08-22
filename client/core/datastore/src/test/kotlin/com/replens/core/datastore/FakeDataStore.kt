package com.replens.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** Fails both directions with [failure], which is the only thing worth faking here. */
internal class FakeDataStore : DataStore<Preferences> {

    var failure: Throwable? = null

    private val stored = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences>
        get() = failure?.let { error -> flow { throw error } } ?: stored

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        failure?.let { throw it }
        return transform(stored.value).also { stored.value = it }
    }
}
