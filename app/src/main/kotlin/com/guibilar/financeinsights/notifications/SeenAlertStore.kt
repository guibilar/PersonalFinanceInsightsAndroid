package com.guibilar.financeinsights.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.guibilar.financeinsights.core.today.SeenAlerts
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/** The phone's file of the alert ids that it already saw (T68). One per process. */
private val Context.seenAlertData: DataStore<Preferences> by preferencesDataStore("alert-notifications")

/**
 * The alert ids of the last poll, and for which account. Nothing stored (after install, sign-out, or a turn-off)
 * makes the next poll a first poll, which notifies nothing.
 */
class SeenAlertStore(private val data: DataStore<Preferences>) {
    /** What the last poll saw; null when nothing is stored, or when the file cannot be read. */
    suspend fun read(): SeenAlerts? {
        val stored = data.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()
        val owner = stored[OWNER] ?: return null
        return SeenAlerts(owner, stored[IDS].orEmpty())
    }

    suspend fun write(seen: SeenAlerts) {
        data.edit {
            it[OWNER] = seen.owner
            it[IDS] = seen.alertIds
        }
    }

    suspend fun clear() {
        data.edit { it.clear() }
    }

    companion object {
        private val OWNER = stringPreferencesKey("owner")
        private val IDS = stringSetPreferencesKey("alertIds")

        fun of(context: Context) = SeenAlertStore(context.applicationContext.seenAlertData)
    }
}
