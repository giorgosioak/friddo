package com.giorgosioak.friddo.service

import android.util.Log
import com.giorgosioak.friddo.data.local.DefaultValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ServerState {
    STOPPED, STARTING, RUNNING, ERROR
}

data class ServerDetails(
    val pid: Int,
    val address: String = DefaultValues.ADDRESS,
    val port: String = DefaultValues.PORT,
    val version: String = "unknown",
    val arch: String = "unknown",
    val startTime: Long = 0L
)

enum class LogType { INFO, ERROR, SUCCESS, WARN, SYSTEM }

data class LogEntry(
    val timestamp: String,
    val message: String,
    val type: LogType
)

data class FridaProcess(
    val name: String,
    val pid: Int,
    val packageId: String,
    val uptime: String = "00:00"
)

object ServerStateManager {

    private const val TAG = "ServerStateManager"

    // Use Dispatchers.Default for the manager scope to handle mapping off the Main thread
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ----------------- 1. PRIMARY SERVER STATE -----------------

    private val _serverState = MutableStateFlow(ServerState.STOPPED)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    val isRunning: StateFlow<Boolean> = _serverState
        .map { it == ServerState.RUNNING || it == ServerState.STARTING }
        .stateIn(
            scope = managerScope,
            started = SharingStarted.Eagerly, // Keep alive as long as process is alive
            initialValue = false
        )

    fun updateState(newState: ServerState) {
        _serverState.update { newState } // Thread-safe update
        Log.d(TAG, "State transition: $newState")

        if (newState == ServerState.STOPPED || newState == ServerState.ERROR) {
            _serverDetails.update { null }
            serverStartTimeMillis.update { 0L }
        }
    }

    // ----------------- 2. SERVER METADATA & UPTIME -----------------

    private val _serverDetails = MutableStateFlow<ServerDetails?>(null)
    val serverDetails: StateFlow<ServerDetails?> = _serverDetails.asStateFlow()

    // Fixed: Using MutableStateFlow for the start time to ensure thread-safe uptime calculation
    private val serverStartTimeMillis = MutableStateFlow(0L)

    private val _activeProcesses = MutableStateFlow<List<FridaProcess>>(emptyList())
    val activeProcesses: StateFlow<List<FridaProcess>> = _activeProcesses.asStateFlow()

    fun updateProcesses(processes: List<FridaProcess>) {
        _activeProcesses.value = processes
    }

    fun setServerDetails(details: ServerDetails?) {
        _serverDetails.update { details }

        if (details != null) {
            // Only set start time if it's currently 0 (start of a session)
            serverStartTimeMillis.update { current ->
                when {
                    details.startTime > 0L -> details.startTime
                    current == 0L -> System.currentTimeMillis()
                    else -> current
                }
            }
        } else {
            serverStartTimeMillis.update { 0L }
        }
    }

    fun getFormattedUptime(): String {
        val startTime = serverStartTimeMillis.value
        if (startTime == 0L) return "00:00"

        val diff = System.currentTimeMillis() - startTime
        val seconds = (diff / 1000) % 60
        val minutes = (diff / (1000 * 60)) % 60
        val hours = (diff / (1000 * 60 * 60))

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    // ----------------- 3. LIVE LOGGING -----------------

    private val _serverLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val serverLogs: StateFlow<List<LogEntry>> = _serverLogs.asStateFlow()

    // Re-use formatter to avoid creating new objects every log entry
    private val logDateFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun addLog(type: LogType, message: String) {
        val timestamp = logDateFormatter.format(Date())

        // Split by lines to ensure the "Side Indicator" in the UI looks correct for every line
        val lines = message.lines().filter { it.isNotBlank() }

        _serverLogs.update { currentLogs ->
            val newEntries = lines.map { line ->
                LogEntry(
                    timestamp = timestamp,
                    message = line.trim(),
                    type = type
                )
            }
            val updatedList = currentLogs + newEntries
            // Rigor: Keep memory footprint stable
            if (updatedList.size > 500) updatedList.takeLast(500) else updatedList
        }

        Log.v(TAG, "[${type.name}]: $message")
    }

    fun addLog(rawMessage: String) {
        val trimmed = rawMessage.trim()

        // Regex to identify tags like [ERROR], [INFO], [SUCCESS] at the start of a string
        val tagMatch = Regex("^\\[(ERROR|WARN|SUCCESS|INFO|SYSTEM)\\]", RegexOption.IGNORE_CASE)
            .find(trimmed)

        val type = when (tagMatch?.groupValues?.get(1)?.uppercase()) {
            "ERROR"   -> LogType.ERROR
            "WARN"    -> LogType.WARN
            "SUCCESS" -> LogType.SUCCESS
            "INFO"    -> LogType.INFO
            else      -> LogType.SYSTEM
        }

        // Strip the tag from the message for a cleaner UI
        val cleanMessage = if (tagMatch != null) {
            trimmed.removePrefix(tagMatch.value).trim()
        } else {
            trimmed
        }

        addLog(type, cleanMessage)
    }

    fun clearLogs() {
        _serverLogs.update { emptyList() }
    }
}