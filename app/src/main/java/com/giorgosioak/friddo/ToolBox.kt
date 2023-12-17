package com.giorgosioak.friddo

import com.giorgosioak.friddo.model.Release
import java.io.BufferedReader
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

    fun getReleasesFromGithubApi(): Array<Release> {
        var releases: Array<Release> = emptyArray()

        return releases;
    }
}