package com.giorgosioak.friddo

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class ToolBox {

    companion object {
        fun isFridaServerRunning(): Boolean {
            try {
                val process = Runtime.getRuntime().exec("ps")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?

                while (true) {
                    line = reader.readLine() ?: break // Exit the loop if line is null
                    if (line.contains("frida-server")) {
                        return true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }

    }
}