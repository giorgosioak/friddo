package com.giorgosioak.friddo.utils

import android.os.Environment
import android.os.StatFs

object SystemUtils {
    /**
     * Checks if there is enough internal storage (e.g., > 50MB) for Frida operations.
     */
    fun hasEnoughStorage(): Boolean {
        val stat = StatFs(Environment.getDataDirectory().path)
        val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
        val megabytesAvailable = bytesAvailable / (1024 * 1024)

        // Threshold of 50MB; adjust as needed for Frida binaries
        return megabytesAvailable > 50
    }
}