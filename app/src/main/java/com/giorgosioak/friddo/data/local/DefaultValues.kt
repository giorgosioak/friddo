package com.giorgosioak.friddo.data.local

import android.content.Context

object DefaultValues {
    const val PORT = "27042"
    const val ADDRESS = "127.0.0.1"

    // Feature Defaults
    const val AUTO_START = false
    const val PERSISTENT_LOGS = true
    const val THEME = "system"
    const val LANGUAGE = "system"

    /**
     * Returns the absolute path to the Frida server binary.
     * We use [java.io.File.separator] to ensure path consistency.
     */
    fun binaryPath(context: Context): String {
        val friddoDir = context.filesDir.resolve("friddo")
        if (!friddoDir.exists()) {
            friddoDir.mkdirs()
        }
        return friddoDir.resolve("frida-server").absolutePath
    }
}