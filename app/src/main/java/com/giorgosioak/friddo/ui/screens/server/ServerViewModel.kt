package com.giorgosioak.friddo.ui.screens.server

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.giorgosioak.friddo.data.local.DefaultValues
import com.giorgosioak.friddo.data.local.PreferencesKeys
import com.giorgosioak.friddo.data.local.ServerSessionStore
import com.giorgosioak.friddo.ui.screens.settings.settingsDataStore
import com.giorgosioak.friddo.data.repository.VersionRepository
import com.giorgosioak.friddo.service.FridaServerService
import com.giorgosioak.friddo.service.LogType
import com.giorgosioak.friddo.service.ServerDetails
import com.giorgosioak.friddo.service.ServerState
import com.giorgosioak.friddo.service.ServerStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ServerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VersionRepository(application)

    val activeProcesses = ServerStateManager.activeProcesses
    val activeVersionTag: Flow<String?> = repository.activeVersionFlow
    val serverDetails = ServerStateManager.serverDetails

    val uptime: StateFlow<String> = flow {
        while (true) {
            emit(ServerStateManager.getFormattedUptime())
            delay(1000)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "00:00"
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val sessionStore = ServerSessionStore(getApplication())
            val session = sessionStore.getSession()

            if (session != null) {
                val (savedPid, savedVer, savedArch, savedStartTime) = session

                // Validate: check if a process with that PID is actually frida-server
                val isFrida = try {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "ps -p $savedPid -o comm="))
                        .inputStream.bufferedReader().readText().trim()
                        .contains("frida-server")
                } catch (_: Exception) { false }

                if (isFrida) {
                    ServerStateManager.updateState(ServerState.RUNNING)
                    ServerStateManager.setServerDetails(
                        ServerDetails(pid = savedPid, version = savedVer, arch = savedArch, startTime = savedStartTime)
                    )
                    reconnectServer(source = "session_restore")
                } else {
                    // CLEANUP: The process is gone, remove stale data
                    sessionStore.clearSession()
                }
            }  else {
                checkAutoStart()
            }
        }
    }

    private suspend fun checkAutoStart() {
        val context = getApplication<Application>()
        val prefs = context.settingsDataStore.data.first()
        val isAutoStartEnabled = prefs[PreferencesKeys.Settings.AUTO_START] ?: DefaultValues.AUTO_START

        if (isAutoStartEnabled) {
            val activeVersion = repository.activeVersionFlow.firstOrNull()


            if (activeVersion == null) {
                val msg = "Auto-start enabled but no Frida binary is active/downloaded. Skipping..."
                ServerStateManager.addLog(LogType.INFO,msg)
                Log.w("ServerViewModel", msg)
                return
            }

            if (ServerStateManager.serverState.value != ServerState.RUNNING) {
                val msg = "Auto-starting Frida server with version: $activeVersion"
                ServerStateManager.addLog(LogType.INFO,msg)
                Log.d("ServerViewModel", msg)
                startServer(source = "auto_start")
            }
        }
    }

    // --- ACTIONS ---
    fun startServer(source: String = "ui") {
        val intent = Intent(getApplication(), FridaServerService::class.java).apply {
            action = FridaServerService.ACTION_START_SERVICE
            putExtra("action_source", source)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun reconnectServer(source: String = "ui") {
        val intent = Intent(getApplication(), FridaServerService::class.java).apply {
            action = FridaServerService.ACTION_RECONNECT_SERVICE
            putExtra("action_source", source)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun stopServer(source: String = "ui") {
        val intent = Intent(getApplication(), FridaServerService::class.java).apply {
            action = FridaServerService.ACTION_STOP_SERVICE
            putExtra("action_source", source)
        }
        getApplication<Application>().startService(intent)
    }

    fun restartServer(source: String = "ui") {
        val intent = Intent(getApplication(), FridaServerService::class.java).apply {
            action = FridaServerService.ACTION_RESTART_SERVICE
            putExtra("action_source", source)
        }
        getApplication<Application>().startService(intent)
    }

    fun stopHooking(pid: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Forcefully kill the process by PID
                val command = "kill -9 $pid"
                // Assuming you have a helper to run root commands
                runRootCommand(command)

                // The polling loop in FridaServerService will
                // automatically detect the process is gone and update the UI
            } catch (e: Exception) {
                Log.e("ServerViewModel", "Failed to stop hook for PID $pid", e)
            }
        }
    }

    private fun runRootCommand(command: String) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = process.outputStream
            os.write((command + "\n").toByteArray())
            os.write("exit\n".toByteArray())
            os.flush()
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
