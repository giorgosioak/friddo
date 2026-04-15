package com.giorgosioak.friddo.service

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import com.giorgosioak.friddo.MainActivity
import com.giorgosioak.friddo.R

object NotificationHelper {
    const val NOTIFICATION_ID = 1001
    private const val CHANNEL_ID = "frida_server_channel"

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
        state: ServerState
    ): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java)

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

        // Stop Action
        val stopIntent = Intent(context, FridaServerService::class.java).apply {
            action = FridaServerService.ACTION_STOP_SERVICE
            putExtra("action_source", "notification")
        }
        val stopPending = PendingIntent.getService(context, 1, stopIntent, flags)

        // Restart Action
        val restartIntent = Intent(context, FridaServerService::class.java).apply {
            action = FridaServerService.ACTION_RESTART_SERVICE
            putExtra("action_source", "notification")
        }
        val restartPending = PendingIntent.getService(context, 2, restartIntent, flags)

        val statusText = when (state) {
            ServerState.RUNNING -> "Server Running"
            ServerState.STARTING -> "Starting..."
            ServerState.ERROR -> "Critical Error"
            else -> "Server Stopped"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(statusText)
            .setSmallIcon(R.drawable.ic_friddo)
            .setOngoing(state == ServerState.RUNNING || state == ServerState.STARTING)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .apply {
                if (state == ServerState.RUNNING) {
                    addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
                    addAction(android.R.drawable.ic_menu_rotate, "Restart", restartPending)
                } else if (state == ServerState.ERROR) {
                    addAction(android.R.drawable.ic_menu_rotate, "Retry", restartPending)
                }
            }
    }

    fun buildNotification(
        context: Context,
        state: ServerState,
        details: ServerDetails?
    ): Notification? {
        if (details == null) return null

        val contentText = "Version: ${details.version} (${details.arch})"

        return buildBaseNotification(context, state)
            .setContentText(contentText)
            .build()
    }

    fun buildForegroundNotification(
        context: Context,
        state: ServerState
    ): Notification {
        return buildBaseNotification(context, state)
            .setContentText("Preparing server details...")
            .build()
    }
}
