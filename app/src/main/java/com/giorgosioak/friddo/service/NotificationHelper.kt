package com.giorgosioak.friddo.service

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import com.giorgosioak.friddo.MainActivity
import com.giorgosioak.friddo.R

object NotificationHelper {
    const val NOTIFICATION_ID = 1001
    private const val CHANNEL_ID = "frida_server_channel"
    private const val REQUEST_OPEN_DASHBOARD = 10
    private const val REQUEST_OPEN_LOGS = 11
    private const val REQUEST_STOP = 12
    private const val REQUEST_RESTART = 13

    fun createNotificationChannel(context: Context) {
        val name = "Frida Server Status"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = "Status and controls for the Frida process"
            setShowBadge(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildBaseNotification(
        context: Context,
        state: ServerState,
        details: ServerDetails?,
        activeProcessCount: Int,
        logs: List<LogEntry>
    ): NotificationCompat.Builder {
        val dashboardPendingIntent = activityPendingIntent(
            context = context,
            route = "server",
            requestCode = REQUEST_OPEN_DASHBOARD
        )
        val logsPendingIntent = activityPendingIntent(
            context = context,
            route = "logs",
            requestCode = REQUEST_OPEN_LOGS
        )
        val stopPendingIntent = servicePendingIntent(
            context = context,
            action = FridaServerService.ACTION_STOP_SERVICE,
            requestCode = REQUEST_STOP
        )
        val restartPendingIntent = servicePendingIntent(
            context = context,
            action = FridaServerService.ACTION_RESTART_SERVICE,
            requestCode = REQUEST_RESTART
        )

        val showChronometer = state == ServerState.RUNNING && details?.startTime?.let { it > 0L } == true

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(titleFor(state))
            .setContentText(contentTextFor(state, details, activeProcessCount, logs))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedTextFor(state, details, activeProcessCount, logs))
            )
            .setSmallIcon(R.drawable.ic_friddo)
            .setOngoing(state == ServerState.RUNNING || state == ServerState.STARTING)
            .setContentIntent(dashboardPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setLocalOnly(true)
            .setShowWhen(showChronometer)
            .setWhen(details?.startTime?.takeIf { showChronometer } ?: System.currentTimeMillis())
            .setUsesChronometer(showChronometer)
            .apply {
                when (state) {
                    ServerState.RUNNING -> {
                        addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
                        addAction(R.drawable.ic_restart, "Restart", restartPendingIntent)
                        addAction(R.drawable.ic_list, "Logs", logsPendingIntent)
                    }
                    ServerState.STARTING -> {
                        addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
                        addAction(R.drawable.ic_list, "Logs", logsPendingIntent)
                    }
                    ServerState.ERROR -> {
                        addAction(R.drawable.ic_restart, "Retry", restartPendingIntent)
                        addAction(R.drawable.ic_list, "Logs", logsPendingIntent)
                    }
                    ServerState.STOPPED -> Unit
                }
            }
    }

    fun buildNotification(
        context: Context,
        state: ServerState,
        details: ServerDetails?,
        activeProcessCount: Int = 0,
        logs: List<LogEntry> = emptyList()
    ): Notification? {
        if (state == ServerState.STOPPED) return null

        return buildBaseNotification(
            context = context,
            state = state,
            details = details,
            activeProcessCount = activeProcessCount,
            logs = logs
        )
            .build()
    }

    fun buildForegroundNotification(
        context: Context,
        state: ServerState
    ): Notification {
        return buildBaseNotification(
            context = context,
            state = state,
            details = null,
            activeProcessCount = 0,
            logs = emptyList()
        )
            .build()
    }

    private fun activityPendingIntent(
        context: Context,
        route: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, route)
        }
        return PendingIntent.getActivity(context, requestCode, intent, pendingIntentFlags())
    }

    private fun servicePendingIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, FridaServerService::class.java).apply {
            this.action = action
            putExtra("action_source", "notification")
        }
        return PendingIntent.getService(context, requestCode, intent, pendingIntentFlags())
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun titleFor(state: ServerState): String =
        when (state) {
            ServerState.RUNNING -> "Frida server running"
            ServerState.STARTING -> "Starting Frida server"
            ServerState.ERROR -> "Frida server needs attention"
            ServerState.STOPPED -> "Frida server stopped"
        }

    private fun contentTextFor(
        state: ServerState,
        details: ServerDetails?,
        activeProcessCount: Int,
        logs: List<LogEntry>
    ): String {
        return when (state) {
            ServerState.RUNNING -> details?.let {
                "Frida ${it.version} (${it.arch}) - ${it.address}:${it.port} - ${sessionText(activeProcessCount)}"
            } ?: "Server is running"
            ServerState.STARTING -> startupStep(logs.lastOrNull())
            ServerState.ERROR -> latestProblem(logs) ?: "Open logs for details"
            ServerState.STOPPED -> "Server stopped"
        }
    }

    private fun expandedTextFor(
        state: ServerState,
        details: ServerDetails?,
        activeProcessCount: Int,
        logs: List<LogEntry>
    ): String {
        return when (state) {
            ServerState.RUNNING -> details?.let {
                listOf(
                    "Version: ${it.version} (${it.arch})",
                    "Listening: ${it.address}:${it.port}",
                    "PID: ${it.pid}",
                    "Active sessions: $activeProcessCount"
                ).joinToString("\n")
            } ?: "Server is running. Open Friddo for details."
            ServerState.STARTING -> startupStep(logs.lastOrNull())
            ServerState.ERROR -> latestProblem(logs)?.let { "Last problem: $it" }
                ?: "Open logs for the latest server output."
            ServerState.STOPPED -> "Server stopped"
        }
    }

    private fun startupStep(entry: LogEntry?): String {
        val message = entry?.message ?: return "Preparing server details..."
        return when {
            message.contains("Checking Root", ignoreCase = true) -> "Checking root access..."
            message.contains("Root granted", ignoreCase = true) -> "Root access granted"
            message.contains("Starting Frida server", ignoreCase = true) -> "Starting server process..."
            message.contains("Binary:", ignoreCase = true) -> "Preparing selected binary..."
            message.contains("Listening on", ignoreCase = true) -> message
            else -> message
        }
    }

    private fun latestProblem(logs: List<LogEntry>): String? =
        logs.lastOrNull { it.type == LogType.ERROR || it.type == LogType.WARN }
            ?.message
            ?.takeIf { it.isNotBlank() }

    private fun sessionText(activeProcessCount: Int): String =
        when (activeProcessCount) {
            0 -> "no sessions"
            1 -> "1 session"
            else -> "$activeProcessCount sessions"
        }
}
