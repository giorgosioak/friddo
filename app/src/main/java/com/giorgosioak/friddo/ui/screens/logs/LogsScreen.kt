package com.giorgosioak.friddo.ui.screens.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giorgosioak.friddo.data.local.DefaultValues
import com.giorgosioak.friddo.data.local.PreferencesKeys
import com.giorgosioak.friddo.ui.screens.settings.settingsDataStore
import com.giorgosioak.friddo.service.LogEntry
import com.giorgosioak.friddo.service.LogType
import com.giorgosioak.friddo.service.ServerStateManager
import kotlinx.coroutines.flow.map

@Composable
fun LogsScreen() {
    val entries by ServerStateManager.serverLogs.collectAsStateWithLifecycle()
    val isRunning by ServerStateManager.isRunning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dataStore = context.settingsDataStore

    val persistentLogs by remember {
        dataStore.data.map { prefs ->
            prefs[PreferencesKeys.Settings.PERSISTENT_LOGS] ?: DefaultValues.PERSISTENT_LOGS
        }
    }.collectAsState(initial = true)

    LaunchedEffect(isRunning, persistentLogs) {
        if (!persistentLogs && !isRunning) {
            ServerStateManager.clearLogs()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Server Logs",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRunning) StatusPulse()
                    Text(
                        text = if (isRunning) "Server status: Active" else "Server status: Stopped / Inactive",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedIconButton(
                    onClick = {
                        if (entries.isNotEmpty()) {
                            val allLogs =
                                entries.joinToString("\n") { "[${it.timestamp}] [${it.type}] ${it.message}" }
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Friddo Logs", allLogs)
                            clipboard.setPrimaryClip(clip)
                        } else {
                            Toast.makeText(
                                context,
                                "Nothing to copy",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    colors = IconButtonDefaults.outlinedIconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy All Logs",
                        modifier = Modifier.size(20.dp)
                    )
                }

                OutlinedIconButton(
                    onClick = {
                        ServerStateManager.clearLogs()
                        Toast.makeText(
                            context,
                            "Cleared Logs",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    colors = IconButtonDefaults.outlinedIconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.DeleteSweep, "Clear Logs")
                }
            }
        }

        LogStreamComponent(entries = entries)
    }
}

@Composable
fun LogStreamComponent(entries: List<LogEntry>) {
    val scrollState = rememberScrollState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (entries.isEmpty()) {
                Text(
                    text = "No activity detected...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            entries.forEach { entry ->
                LogItem(entry)
            }
        }
    }
}

@Composable
fun LogItem(entry: LogEntry) {
    // 1. Map the LogType to a high-saturation accent color for the side-bar
    val accentColor = when (entry.type) {
        LogType.ERROR -> MaterialTheme.colorScheme.error
        LogType.SUCCESS -> Color(0xFF4CAF50) // Technical Green
        LogType.WARN -> Color(0xFFFFA000)    // Technical Orange
        LogType.INFO -> MaterialTheme.colorScheme.primary
        LogType.SYSTEM -> MaterialTheme.colorScheme.secondary
    }

    // 2. Subtle background for the card (very low alpha)
    val cardBgColor = when (entry.type) {
        LogType.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
    }

    // Main Card Container
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = cardBgColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), // Forces bar to match text height
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 3. THE SIDE INDICATOR LINE
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp) // Rigor: 4dp consistent width
                    .background(accentColor)
            )

            // 4. CONTENT AREA
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.message,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    )
                }

                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun StatusPulse() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .size(8.dp)
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = alpha), CircleShape)
    )
}