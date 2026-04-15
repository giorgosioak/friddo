package com.giorgosioak.friddo.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// Dedicated DataStore for the server session
val Context.sessionDataStore by preferencesDataStore(name = "friddo_session")

class ServerSessionStore(private val context: Context) {

    data class SessionData(
        val pid: Int,
        val version: String,
        val arch: String,
        val startTime: Long
    )

    suspend fun saveSession(pid: Int, version: String, arch: String, startTime: Long) {
        context.sessionDataStore.edit { prefs ->
            prefs[PreferencesKeys.Session.PID] = pid
            prefs[PreferencesKeys.Session.VERSION] = version
            prefs[PreferencesKeys.Session.ARCH] = arch
            prefs[PreferencesKeys.Session.START_TIME] = startTime
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.Session.PID)
            prefs.remove(PreferencesKeys.Session.VERSION)
            prefs.remove(PreferencesKeys.Session.ARCH)
            prefs.remove(PreferencesKeys.Session.START_TIME)
        }
    }

    suspend fun getSession(): SessionData? {
        val prefs = context.sessionDataStore.data.first()
        return SessionData(
            pid = prefs[PreferencesKeys.Session.PID] ?: return null,
            version = prefs[PreferencesKeys.Session.VERSION] ?: return null,
            arch = prefs[PreferencesKeys.Session.ARCH] ?: return null,
            startTime = prefs[PreferencesKeys.Session.START_TIME] ?: 0L
        )
    }
}
