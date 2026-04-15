package com.giorgosioak.friddo.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.giorgosioak.friddo.data.repository.VersionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class FridaTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var versionRepository: VersionRepository

    override fun onCreate() {
        super.onCreate()
        versionRepository = VersionRepository(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()

        ServerStateManager.serverState.onEach { state ->
            val tile = qsTile ?: return@onEach

            when (state) {
                ServerState.RUNNING -> {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "Frida: Running"
                }
                ServerState.STARTING -> {
                    // This creates a "dimmed active" or "busy" look on most Android versions
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "Starting..."
                }
                ServerState.STOPPED -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "Frida: Stopped"
                }
                ServerState.ERROR -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "Frida: Error"
                }
            }

            tile.updateTile()
        }.launchIn(serviceScope)
    }

    override fun onClick() {
        super.onClick()

        val currentState = ServerStateManager.serverState.value

        if (currentState == ServerState.STOPPED || currentState == ServerState.ERROR) {
            // Use a coroutine to fetch the binary path from DataStore/Repository
            serviceScope.launch {
                val activeTag = versionRepository.getActiveVersionTag()
                val installedVersions = versionRepository.listInstalledVersions()
                val binaryPath = installedVersions.find { it.tag == activeTag }?.path

                if (binaryPath != null) {
                    val intent = Intent(this@FridaTileService, FridaServerService::class.java).apply {
                        action = FridaServerService.ACTION_START_SERVICE
                        putExtra("binary_path", binaryPath)
                    }
                    startForegroundService(intent)
                } else {
                    // Fallback if no version is selected
                    qsTile.label = "Select Version"
                    qsTile.updateTile()
                }
            }
        } else {
            // STOP logic
            val intent = Intent(this, FridaServerService::class.java).apply {
                action = FridaServerService.ACTION_STOP_SERVICE
            }
            startService(intent)
        }
    }

    override fun onStopListening() {
        // Important: Stop observing when the shade is closed to save battery
        serviceScope.coroutineContext[Job]?.cancelChildren()
        super.onStopListening()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
