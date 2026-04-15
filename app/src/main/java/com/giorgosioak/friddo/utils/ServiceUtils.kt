package com.giorgosioak.friddo.utils

import android.content.Context
import android.content.Intent

/**
 * Starts a foreground service or normal service depending on the device's API level.
 */
fun Context.startFriddoService(intent: Intent) {
    this.startForegroundService(intent)
}
