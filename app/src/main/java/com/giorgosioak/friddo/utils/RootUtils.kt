package com.giorgosioak.friddo.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object RootUtils {
    private const val TAG = "RootUtils"

    /**
     * Checks if the device is rooted and if the user has granted SU permissions.
     * Calling this triggers the system "Grant Root" popup if not already granted.
     */
    suspend fun checkRootPermission(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Running 'id' is the standard way to check for root.
                // If successful, it returns a string containing 'uid=0(root)'.
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readLine()
                val exitCode = process.waitFor()

                Log.d(TAG, "Root check output: $output, exitCode: $exitCode")

                // Return true if exit code is 0 and output confirms root status
                exitCode == 0 && output != null && (output.contains("uid=0") || output.contains("root"))
            } catch (e: Exception) {
                Log.e(TAG, "Root check failed (Device likely not rooted): ${e.message}")
                false
            }
        }
    }
}