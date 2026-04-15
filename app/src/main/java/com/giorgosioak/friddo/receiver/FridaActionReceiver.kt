package com.giorgosioak.friddo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.giorgosioak.friddo.MainActivity
import com.giorgosioak.friddo.service.FridaServerService
import com.giorgosioak.friddo.utils.startFriddoService

/**
 * Handles quick actions from FridaServerService notifications:
 * - Start server
 * - Stop server
 * - Restart server
 * - View logs
 */
class FridaActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FridaActionReceiver"

        const val ACTION_START = "com.giorgosioak.friddo.ACTION_START"
        const val ACTION_STOP = "com.giorgosioak.friddo.ACTION_STOP"
        const val ACTION_RESTART = "com.giorgosioak.friddo.ACTION_RESTART"
        const val ACTION_VIEW_LOGS = "com.giorgosioak.friddo.ACTION_VIEW_LOGS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START -> {
                Log.i(TAG, "Notification action: Start server")
                val serviceIntent = Intent(context, FridaServerService::class.java).apply {
                    action = FridaServerService.ACTION_START_SERVICE
                }
                context.startFriddoService(serviceIntent)
            }

            ACTION_STOP -> {
                Log.i(TAG, "Notification action: Stop server")
                val serviceIntent = Intent(context, FridaServerService::class.java).apply {
                    action = FridaServerService.ACTION_STOP_SERVICE
                }
                context.startFriddoService(serviceIntent)
            }

            ACTION_RESTART -> {
                Log.i(TAG, "Notification action: Restart server")
                val serviceIntent = Intent(context, FridaServerService::class.java).apply {
                    action = FridaServerService.ACTION_RESTART_SERVICE
                }
                context.startFriddoService(serviceIntent)
            }

            ACTION_VIEW_LOGS -> {
                Log.i(TAG, "Notification action: View logs")
                val logsIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("navigate_to", "logs")
                }
                context.startActivity(logsIntent)
            }

            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
    }
}
