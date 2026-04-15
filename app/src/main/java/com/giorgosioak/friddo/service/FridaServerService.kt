package com.giorgosioak.friddo.service

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.giorgosioak.friddo.data.local.DefaultValues
import com.giorgosioak.friddo.data.local.ServerSessionStore
import com.giorgosioak.friddo.data.local.PreferencesKeys
import com.giorgosioak.friddo.ui.screens.settings.settingsDataStore
import com.giorgosioak.friddo.utils.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

class FridaServerService() : LifecycleService() {

    companion object {
        const val TAG = "FridaServerService"

        // Actions used internally by FridaServerManager
        const val ACTION_START_SERVICE = "com.giorgosioak.friddo.service.START"
        const val ACTION_RECONNECT_SERVICE = "com.giorgosioak.friddo.service.RECONNECT"
        const val ACTION_STOP_SERVICE = "com.giorgosioak.friddo.service.STOP"
        const val ACTION_RESTART_SERVICE = "com.giorgosioak.friddo.service.RESTART"
    }

    private var serverProcess: Process? = null
    private var serverPid: Int? = null
    private var serverJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)

        // Reactive Notification: Automatically updates when ServerStateManager state changes
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                ServerStateManager.serverState,
                ServerStateManager.serverDetails
            ) { state, details ->
                NotificationHelper.buildNotification(
                    context = this@FridaServerService,
                    state = state,
                    details = details
                )
            }.collect { notification ->
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                if (notification == null) {
                    manager.cancel(NotificationHelper.NOTIFICATION_ID)
                } else {
                    manager.notify(NotificationHelper.NOTIFICATION_ID, notification)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        lifecycleScope.launch {
            val prefs = settingsDataStore.data.first()

            val binaryPath = DefaultValues.binaryPath(this@FridaServerService)
            val currentAddress = prefs[PreferencesKeys.Settings.SERVER_ADDRESS] ?: DefaultValues.ADDRESS
            val currentPort = prefs[PreferencesKeys.Settings.SERVER_PORT] ?: DefaultValues.PORT
            val actionSource = intent?.getStringExtra("action_source") ?: "system"

            when (intent?.action) {
                ACTION_START_SERVICE -> {
                    ServerStateManager.updateState(ServerState.STARTING)
                    ServerStateManager.addLog(LogType.INFO,"Initializing service...")

                    val initialNotification = NotificationHelper.buildForegroundNotification(
                        this@FridaServerService,
                        ServerState.STARTING
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(NotificationHelper.NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else {
                        startForeground(NotificationHelper.NOTIFICATION_ID, initialNotification)
                    }

                    ServerStateManager.addLog(LogType.INFO,"Checking Root access...")
                    val hasRoot = withContext(Dispatchers.IO) { RootUtils.checkRootPermission() }

                    if (hasRoot) {
                        ServerStateManager.addLog(LogType.SUCCESS,"Root granted.")
                        startServer(binaryPath, currentAddress, currentPort, actionSource)
                    } else {
                        ServerStateManager.updateState(ServerState.ERROR)
                        ServerStateManager.addLog(LogType.ERROR,"Root access denied.")
                        this@FridaServerService.stopForeground(STOP_FOREGROUND_REMOVE)
                        this@FridaServerService.stopSelf()
                    }
                }

                ACTION_RECONNECT_SERVICE -> {
                    val initialNotification = NotificationHelper.buildNotification(
                        this@FridaServerService,
                        ServerState.RUNNING,
                        ServerStateManager.serverDetails.value
                    ) ?: NotificationHelper.buildForegroundNotification(
                        this@FridaServerService,
                        ServerState.RUNNING
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(NotificationHelper.NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else {
                        startForeground(NotificationHelper.NOTIFICATION_ID, initialNotification)
                    }

                    serverJob?.cancel()
                    serverJob = lifecycleScope.launch(Dispatchers.IO) {
                        while (isActive) {
                            val activeProcesses = fetchActiveFridaProcesses()
                            ServerStateManager.updateProcesses(activeProcesses)
                            kotlinx.coroutines.delay(1000)
                        }
                    }
                    ServerStateManager.addLog(LogType.INFO,"Reconnected to existing Frida session.")
                }

                ACTION_STOP_SERVICE -> {
                        stopServer(actionSource)
                }

                ACTION_RESTART_SERVICE -> {
                        ServerStateManager.addLog(LogType.INFO,"Restarting Frida server...")
                        cleanupServerProcess()
                        kotlinx.coroutines.delay(800)
                        startServer(binaryPath, currentAddress, currentPort, actionSource)
                }
            }
        }
        return START_STICKY
    }

    private fun killAnyLingeringFrida() {
        runRootCommand("pkill -9 frida-server")
        runRootCommand("killall -9 frida-server")
    }

    private suspend fun startServer(
        binaryPath: String,
        serverAddress: String,
        serverPort: String,
        source: String
    ) {
        if (binaryPath.isBlank()) {
            ServerStateManager.updateState(ServerState.ERROR)
            ServerStateManager.addLog(LogType.ERROR,"Cannot start: No version path found.")
            return
        }

        ServerStateManager.updateState(ServerState.STARTING)
        ServerStateManager.addLog(LogType.INFO,"Starting Frida server...")
        ServerStateManager.addLog(LogType.INFO,"Binary: $binaryPath")
        ServerStateManager.addLog(LogType.INFO,"Listening on $serverAddress:$serverPort")

        withContext(Dispatchers.IO) {
            try {
                val file = File(binaryPath)
                if (!file.exists()) {
                    ServerStateManager.addLog(LogType.ERROR,"Binary file not found at: $binaryPath")
                    ServerStateManager.addLog(LogType.INFO,"Hint: Please go to the Versions screen and download a server first.")
                    ServerStateManager.updateState(ServerState.ERROR)
                    return@withContext
                }

                killAnyLingeringFrida()
                val chmodResult = runRootCommand("chmod 755 \"$binaryPath\" && echo \"success\"")
                if (!chmodResult.contains("success")) {
                    ServerStateManager.addLog(LogType.ERROR,"Failed to set permissions on binary.")
                }

                val rootCommand = "echo \$\$ && exec \"$binaryPath\" -l $serverAddress:$serverPort"
                val process = ProcessBuilder("su", "-c", rootCommand).start()

                val reader = process.inputStream.bufferedReader()
                val pidString = withContext(Dispatchers.IO) { reader.readLine() }
                val pid = pidString?.trim()?.toIntOrNull() ?: -1

                if (pid == -1) {
                    ServerStateManager.updateState(ServerState.ERROR)
                    ServerStateManager.addLog(LogType.ERROR,"Failed to capture Root PID")
                    process.destroy()
                    return@withContext
                }

                this@FridaServerService.serverProcess = process
                this@FridaServerService.serverPid = pid

                val fullTag = getActiveVersionTag()
                val detectedVersion = fullTag?.substringBefore("-") ?: "unknown"
                val detectedArch = fullTag?.substringAfterLast("-") ?: "unknown"
                val calculatedStartTime =  System.currentTimeMillis()

                lifecycleScope.launch(Dispatchers.IO) {
                    ServerSessionStore(this@FridaServerService).saveSession(
                        pid = pid,
                        version = detectedVersion,
                        arch = detectedArch,
                        startTime = calculatedStartTime
                    )
                }

                ServerStateManager.setServerDetails(
                    ServerDetails(
                        pid = pid,
                        address = serverAddress,
                        port = serverPort,
                        version = detectedVersion,
                        arch = detectedArch,
                        startTime = calculatedStartTime
                    )
                )

                ServerStateManager.updateState(ServerState.RUNNING)
                ServerStateManager.addLog(LogType.SUCCESS,"Frida server started (PID: $pid)")

                // Assign the background tasks to serverJob so they can be cancelled in onDestroy
                serverJob = lifecycleScope.launch(Dispatchers.IO) {
                    // 1. Launch the log reader in a separate job
                    val loggerJob = launch { captureProcessOutput(process) }

                    launch {
                        while (isActive) {
                            val activeProcs = fetchActiveFridaProcesses()
                            ServerStateManager.updateProcesses(activeProcs)
                            kotlinx.coroutines.delay(1000)
                        }
                    }


                    // 2. Wait for process to exit
                    val exitCode = process.waitFor()

                    kotlinx.coroutines.delay(500)
                    loggerJob.cancel() // Stop reading logs if process died

                    if (ServerStateManager.serverState.value == ServerState.RUNNING) {
                        ServerStateManager.addLog(LogType.INFO,"Process exited with code: $exitCode")
                        ServerStateManager.updateState(ServerState.STOPPED)
                        this@FridaServerService.stopSelf()
                    }
                }

            } catch (t: Throwable) {
                Log.e(TAG, "Server start failed", t)
                ServerStateManager.updateState(ServerState.ERROR)
                ServerStateManager.addLog(LogType.ERROR,"Critical failure: ${t.localizedMessage}")
            }
        }
    }

    private suspend fun getActiveVersionTag(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = settingsDataStore.data.first()
                val activeFridaVersion = prefs[PreferencesKeys.Settings.ACTIVE_FRIDA_VERSION]

                Log.d("FridaServerService", "DataStore Raw Value: $activeFridaVersion")
                activeFridaVersion
            } catch (e: Exception) {
                Log.e("FridaServerService", "Failed to read DataStore", e)
                null
            }
        }
    }

    private fun fetchActiveFridaProcesses(): List<FridaProcess> {
        return try {
            // Optimization: Use 'grep -l' to find all PIDs with frida-agent in one pass
            // Then only fetch metadata for those specific PIDs.
            val script = """PIDS=$(grep -l "frida-agent" /proc/*/maps 2>/dev/null | cut -d/ -f3)
                for pid in ${'$'}PIDS; do
                    cmd=$(cat /proc/${'$'}pid/cmdline 2>/dev/null | tr '\0' '\n' | head -n 1)
                    stat=$(cat /proc/${'$'}pid/stat 2>/dev/null)
                    echo "${'$'}pid|${'$'}cmd|${'$'}stat"
                done
            """.trimIndent()

            val output = runRootCommand(script).split("\n").filter { it.isNotBlank() }

            // Get system constants for uptime calculation
            val scClkTck = try {
                android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK)
            } catch (_: Exception) { 100L }
            val uptimeSeconds = android.os.SystemClock.elapsedRealtime() / 1000

            output.mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 3) return@mapNotNull null

                val pid = parts[0].toIntOrNull() ?: return@mapNotNull null
                val packageId = parts[1].trim()
                val statFields = parts[2].split(Regex("\\s+"))

                // Filter out frida-server itself and empty package names
                if (packageId.isNotEmpty() && !packageId.contains("frida-server")) {
                    val uptime = if (statFields.size >= 22) {
                        val startTimeTicks = statFields[21].toLongOrNull() ?: 0L
                        val elapsedSeconds = uptimeSeconds - (startTimeTicks / scClkTck)
                        formatDuration(maxOf(0, elapsedSeconds))
                    } else "00:00"

                    FridaProcess(
                        name = packageId.substringAfterLast("."),
                        pid = pid,
                        packageId = packageId,
                        uptime = uptime
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fetch processes failed", e)
            emptyList()
        }
    }

    // Helper to keep the formatting logic clean
    private fun formatDuration(seconds: Long): String {
        val s = seconds % 60
        val m = (seconds / 60) % 60
        val h = seconds / 3600
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

//    private fun getProcessUptime(pid: Int): String {return try {
//        // 1. Read the raw stat file for the process
//        // Field 22 (index 21) is the 'starttime' in clock ticks since boot
//        val statContent = runRootCommand("cat /proc/$pid/stat").trim()
//        val stats = statContent.split(Regex("\\s+"))
//
//        if (stats.size < 22) return "00:00"
//
//        val startTimeTicks = stats[21].toLongOrNull() ?: return "00:00"
//
//        // 2. Get System properties to convert ticks to seconds
//        // Most Android systems use 100 ticks per second
//        val scClkTck = try {
//            android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK)
//        } catch (e: Exception) {
//            100L
//        }
//
//        // 3. Get current system uptime in seconds
//        val uptimeMillis = android.os.SystemClock.elapsedRealtime()
//        val uptimeSeconds = uptimeMillis / 1000
//
//        // 4. Calculate elapsed seconds
//        val startTimeSeconds = startTimeTicks / scClkTck
//        val elapsedSeconds = uptimeSeconds - startTimeSeconds
//
//        // 5. Format the duration
//        val seconds = elapsedSeconds % 60
//        val minutes = (elapsedSeconds / 60) % 60
//        val hours = elapsedSeconds / 3600
//
//        if (hours > 0) {
//            "%02d:%02d:%02d".format(hours, minutes, seconds)
//        } else {
//            "%02d:%02d".format(minutes, seconds)
//        }
//    } catch (e: Exception) {
//        Log.e("FridaServerService", "Uptime calculation failed for PID $pid", e)
//        "00:00"
//    }
//    }

    private suspend fun stopServer(source: String) {
        ServerStateManager.addLog(LogType.INFO,"Stopping service...")
        cleanupServerProcess()
        ServerStateManager.addLog(LogType.INFO,"Frida server stopped.")
        this@FridaServerService.stopSelf()
    }

    private suspend fun cleanupServerProcess() {
        withContext(Dispatchers.IO) {
            try {
                // 1. Kill the specific PID if we have it
                serverPid?.let { pid ->
                    Log.i(TAG, "Killing Frida server (PID=$pid)")
                    runRootCommand("kill -15 $pid") // TERM
                }

                // 2. Kill any other lingering frida processes
                killAnyLingeringFrida()

                // 3. Destroy the process object
                serverProcess?.destroy()
                serverProcess = null
                serverPid = null

                // 4. Cancel the log-reading and exit-waiting jobs
                serverJob?.cancel()
                serverJob = null

                ServerStateManager.updateState(ServerState.STOPPED)
            } catch (t: Throwable) {
                Log.e(TAG, "Error during cleanup", t)
            }
        }
    }

    private suspend fun captureProcessOutput(process: Process) {
        withContext(Dispatchers.IO) {
            // Read Standard Output AND Standard Error
            val reader = process.inputStream.bufferedReader()
            val errorReader = process.errorStream.bufferedReader()

            launch {
                errorReader.forEachLine { line ->
                    ServerStateManager.addLog(LogType.ERROR,"FRIDA: $line")
                }
            }

            reader.forEachLine { line ->
                ServerStateManager.addLog(LogType.INFO,"FRIDA: $line")
            }
        }
    }

    private fun runRootCommand(command: String): String {
        return try {
            val process = runRootCommandForProcess(command)
                ?: throw IOException("Failed to execute root command")

            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.appendLine(line)
                }
            }
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            Log.e(TAG, "runRootCommand failed", e)
            ""
        }
    }

    private fun runRootCommandForProcess(command: String): Process? {
        return try {
            // Use an array to ensure 'su' correctly receives '-c' and the full command string
            Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        } catch (t: Throwable) {
            Log.e(TAG, "runRootCommandForProcess failed", t)
            null
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        killAnyLingeringFrida()
        lifecycleScope.launch(Dispatchers.IO) {
            ServerSessionStore(this@FridaServerService).clearSession()
        }
        // Just clean up the process objects directly here
        serverJob?.cancel()
        serverProcess?.destroy()
        serverProcess = null
        ServerStateManager.updateState(ServerState.STOPPED)
        super.onDestroy()
    }
}
