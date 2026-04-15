package com.giorgosioak.friddo.ui.screens.server

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.giorgosioak.friddo.data.local.PreferencesKeys
import com.giorgosioak.friddo.service.FridaProcess
import com.giorgosioak.friddo.service.LogType
import com.giorgosioak.friddo.service.ServerState
import com.giorgosioak.friddo.service.ServerStateManager
import com.giorgosioak.friddo.ui.screens.settings.settingsDataStore
import com.giorgosioak.friddo.utils.SystemUtils.hasEnoughStorage
import kotlinx.coroutines.flow.first

@Composable
fun ServerScreen(
    navController: NavController,
    serverViewModel: ServerViewModel = viewModel()
) {
    val context = LocalContext.current
    val serverState by ServerStateManager.serverState.collectAsStateWithLifecycle()
    val activeVersion by serverViewModel.activeVersionTag.collectAsStateWithLifecycle(initialValue = null)
    val serverDetails by serverViewModel.serverDetails.collectAsStateWithLifecycle()
    val uptime by serverViewModel.uptime.collectAsStateWithLifecycle()
    val activeProcesses by serverViewModel.activeProcesses.collectAsStateWithLifecycle(initialValue = emptyList())

    var showNoVersionDialog by remember { mutableStateOf(false) }
    var showRootDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.settingsDataStore.data.first()
        val isFirstLaunch = prefs[PreferencesKeys.Settings.FIRST_LAUNCH] ?: true

        if (isFirstLaunch) {
            showRootDialog = true
            // Mark first launch as complete
            context.settingsDataStore.edit { it[PreferencesKeys.Settings.FIRST_LAUNCH] = false }
        }

        // Always check storage on start
        if (!hasEnoughStorage()) {
            showStorageDialog = true
        }
    }

    if (showNoVersionDialog) {
        AlertDialog(
            onDismissRequest = { showNoVersionDialog = false },
            title = { Text("No Version Selected") },
            text = { Text("Please select a Frida version before starting the server.") },
            confirmButton = {
                TextButton(onClick = {
                    showNoVersionDialog = false
                    navController.navigate("versions")
                }) {
                    Text("Go to Versions")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoVersionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRootDialog) {
        AlertDialog(
            onDismissRequest = { showRootDialog = false },
            icon = { Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Root Access Required") },
            text = { Text("Friddo requires Root privileges to deploy the Frida binary and hook processes. Please grant 'su' access if prompted.") },
            confirmButton = {
                Button(onClick = { showRootDialog = false }) { Text("I Understand") }
            }
        )
    }

    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            icon = { Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Low Storage Space") },
            text = { Text("Your device is very low on storage. Frida might fail to download or execute correctly. Please free up some space.") },
            confirmButton = {
                TextButton(onClick = { showStorageDialog = false }) { Text("Dismiss") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // --- HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Friddo Dashboard",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "One App to Hook Them All",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- STATUS CARD ---
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Monitor Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusIndicator(serverState)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = serverState.displayName().uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            color = when(serverState) {
                                ServerState.RUNNING -> MaterialTheme.colorScheme.primary
                                ServerState.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    if (serverState == ServerState.RUNNING) {
                        Text(
                            text = uptime,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                when (serverState) {
                    ServerState.RUNNING -> {
                        serverDetails?.let { details ->
                            // Grid Monitor Layout
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    CompactMonitorStat(Icons.Default.Hub, "Frida", details.version, Modifier.weight(1f))
                                    CompactMonitorStat(Icons.Default.Memory, "Arch", details.arch, Modifier.weight(1f))
                                    CompactMonitorStat(Icons.Default.Numbers, "PID", details.pid.toString(), Modifier.weight(1f))
                                }
                            }
                        } ?: Text("Initializing parameters...", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        // Show selected binary info or an error state if none selected
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                if (activeVersion != null) {
                                    val detectedVersion = activeVersion?.substringBefore("-") ?: "unknown"
                                    val detectedArch = activeVersion?.substringAfterLast("-") ?: "unknown"
                                    // Extract architecture from the version string if possible,
                                    // or show N/A for dynamic system values
                                    CompactMonitorStat(Icons.Default.Hub,"Selected",detectedVersion,Modifier.weight(1f))
                                    CompactMonitorStat(Icons.Default.Memory,"Arch",detectedArch,Modifier.weight(1f))
                                    CompactMonitorStat(Icons.Default.Numbers,"PID","N/A",Modifier.weight(1f))
                                } else {
                                    // Error/Empty state when no version is picked from the version screen
                                    CompactMonitorStat(icon = Icons.Default.Block,label = "Status",value = "No Binary",modifier = Modifier.weight(1f))
                                    Spacer(Modifier.width(16.dp))
                                    Text(
                                        text = "Select a version to begin",
                                        style = MaterialTheme.typography.bodySmall,modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .weight(2f),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- ACTIVE PROCESSES CARD ---

        Text(
            text = "Active Sessions",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = if (activeProcesses.isEmpty()) Arrangement.Center else Arrangement.Top
            ) {
                if (activeProcesses.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "No active hooks detected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    activeProcesses.forEachIndexed { index, process ->
                        ProcessMonitorItem(
                            process = process,
                            onKillRequest = { pkgId ->
                                // Using the packageId from your FridaProcess object
                                serverViewModel.stopHooking(process.pid)
                                ServerStateManager.addLog(LogType.INFO,"Terminated $pkgId (PID: ${process.pid})")
                                Toast.makeText(context,"Terminated $pkgId (PID: ${process.pid})",Toast.LENGTH_SHORT).show()
                            }
                        )

                        if (index < activeProcesses.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }

        // --- CONTROL BUTTON ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- CONTROL BUTTON (2/3 Width) ---
            Button(
                onClick = {
                    if (serverState == ServerState.RUNNING) {
                        serverViewModel.stopServer()
                    } else if (activeVersion == null) {
                        showNoVersionDialog = true
                    } else {
                        serverViewModel.startServer()
                    }
                },
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(12.dp),
                enabled = serverState != ServerState.STARTING && activeVersion != null,
                colors = if (serverState == ServerState.RUNNING)
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.buttonColors()
            ) {
                if (serverState == ServerState.STARTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        if (serverState == ServerState.RUNNING) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = "Start/Stop Server"
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (serverState == ServerState.RUNNING) "Stop Server" else "Start Server",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // --- RESTART BUTTON (1/3 Width) ---
            // Always visible, but enabled only during RUNNING or ERROR states
            OutlinedButton(
                onClick = { serverViewModel.restartServer() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(12.dp),
                enabled = serverState == ServerState.RUNNING || serverState == ServerState.ERROR,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Restart Server")
                Spacer(Modifier.width(4.dp))
                Text("Restart", maxLines = 1)
            }
        }
    }
}

fun ServerState.displayName(): String {
    return when (this) {
        ServerState.RUNNING -> "Running"
        ServerState.STARTING -> "Starting…"
        ServerState.ERROR -> "Error"
        ServerState.STOPPED -> "Stopped"
    }
}

@Composable
fun CompactMonitorStat(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun StatusIndicator(state: ServerState) {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )

    val color = when (state) {
        ServerState.RUNNING -> MaterialTheme.colorScheme.primary
        ServerState.ERROR -> MaterialTheme.colorScheme.error
        ServerState.STARTING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = Modifier
            .size(14.dp)
            .graphicsLayer(alpha = if (state == ServerState.RUNNING) alpha else 1f)
            .background(color, CircleShape)
    )
}

@Composable
fun ProcessMonitorItem(
    process: FridaProcess,
   onKillRequest: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Android,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = process.packageId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "PID: ${process.pid}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    softWrap = false
                )
                Text(
                    text = " • ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Application process uptime",
                    modifier = Modifier.size(10.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = process.uptime,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    softWrap = false
                )
            }
        }

        // --- 3. Close/Kill Button ---
        OutlinedIconButton(
            onClick = { onKillRequest(process.packageId) },
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            ),
            colors = IconButtonDefaults.outlinedIconButtonColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.05f),
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Stop Hook",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        }
    }
}