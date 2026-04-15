package com.giorgosioak.friddo.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferencesKeys {

    // --- User Settings ---
    object Settings {
        val AUTO_START = booleanPreferencesKey("auto_start_server")
        val PERSISTENT_LOGS = booleanPreferencesKey("persistent_logs")
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val SERVER_ADDRESS = stringPreferencesKey("listen_address")
        val SERVER_PORT = stringPreferencesKey("listen_port")
        val ACTIVE_FRIDA_VERSION = stringPreferencesKey("active_frida_version")
        val FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
    }

    // --- Session Data ---
    object Session {
        val PID = intPreferencesKey("active_pid")
        val VERSION = stringPreferencesKey("active_version")
        val ARCH = stringPreferencesKey("active_arch")
        val START_TIME = longPreferencesKey("start_time")
    }
}
