package com.giorgosioak.friddo.ui.screens.versions

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.*
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.giorgosioak.friddo.data.repository.InstalledVersion
import com.giorgosioak.friddo.data.repository.RemoteRelease
import com.giorgosioak.friddo.data.repository.VersionRepository
import com.giorgosioak.friddo.service.ServerState
import com.giorgosioak.friddo.service.ServerStateManager
import com.giorgosioak.friddo.service.ServerStateManager.serverDetails
import com.giorgosioak.friddo.utils.rememberConnectivityState
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Helper map to bridge human-readable names to Frida asset naming conventions
private val abiOptionsMap = mapOf(
    "arm64 (64-bit)" to "arm64",
    "arm (32-bit)" to "arm",
    "x86_64 (64-bit)" to "x86_64",
    "x86 (32-bit)" to "x86"
)

// Helper function to convert system ABI to human-readable format
fun getFridaAbiFormat(abi: String): String {
    return when {
        abi.startsWith("arm64") || abi.contains("aarch64") -> "arm64 (64-bit)"
        abi.startsWith("arm") -> "arm (32-bit)"
        abi.contains("64") -> "x86_64 (64-bit)"
        abi.contains("86") -> "x86 (32-bit)"
        else -> "arm64 (64-bit)"
    }
}

fun isReleasedRecently(publishedAt: String?): Boolean {
    if (publishedAt == null) return false
    return try {
        val releaseDate = ZonedDateTime.parse(publishedAt, DateTimeFormatter.ISO_DATE_TIME)
        val now = ZonedDateTime.now()
        val daysBetween = ChronoUnit.DAYS.between(releaseDate, now)
        daysBetween in 0..7
    } catch (_: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionsScreen() {
    val context = LocalContext.current
    val repo = remember { VersionRepository(context) }
    val scope = rememberCoroutineScope()

    var installed by remember { mutableStateOf<List<InstalledVersion>>(emptyList()) }
    var releases by remember { mutableStateOf<List<RemoteRelease>>(emptyList()) }
    var cachedReleases by remember { mutableStateOf<List<RemoteRelease>?>(null) }
    val isOnline by rememberConnectivityState(context)
    var isFetchingReleases by remember { mutableStateOf(false) }
    var activeTag by remember { mutableStateOf<String?>(null) }
    var selectedReleaseForDownload by remember { mutableStateOf<RemoteRelease?>(null) }
    val serverState by ServerStateManager.serverState.collectAsState()

    var abiOverride by remember { mutableStateOf("") }
    var abiDropdownExpanded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    // State for delete confirmation
    var versionToDelete by remember { mutableStateOf<InstalledVersion?>(null) }
    var showServerRunningDeleteBlocked by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()
    var showReleasesSheet by remember { mutableStateOf(false) }

    val latestRelease = cachedReleases?.firstOrNull()
    val showNewVersionAlert = remember(cachedReleases, installed) {
        val isRecent = isReleasedRecently(latestRelease?.publishedAt)
        val isNotInstalled = installed.none { it.tag == latestRelease?.tag }
        isRecent && isNotInstalled
    }

    fun refreshInstalled() {
        scope.launch {
            installed = repo.listInstalledVersions().sortedByDescending { it.publishedAt }
            activeTag = repo.getActiveVersionTag()
        }
    }

    LaunchedEffect(isOnline) {
        if (isOnline && (cachedReleases == null || repo.shouldRefreshReleaseCache())) {
            scope.launch {
                isFetchingReleases = true
                val result = repo.fetchReleases()
                if (result.isNotEmpty()) {
                    cachedReleases = result
                    if (releases.isEmpty()) {
                        releases = result
                    }
                }
                isFetchingReleases = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshInstalled()
        abiOverride = getFridaAbiFormat(Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64")

        scope.launch {
            val cached = repo.getCachedReleases()
            if (cached.isNotEmpty()) {
                cachedReleases = cached
                releases = cached
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Versions",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Manage Frida Server Versions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Force refresh icon
            OutlinedIconButton(
                onClick = {
                    if (!isOnline) {
                        Toast.makeText(context, "No internet connection", Toast.LENGTH_SHORT).show()
                        return@OutlinedIconButton
                    } else {
                        scope.launch {
                            isFetchingReleases = true
                            cachedReleases = repo.fetchReleases(forceRefresh = true)
                            releases = cachedReleases.orEmpty()
                            isFetchingReleases = false
                            Toast.makeText(context, "Releases updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = isOnline && !isFetchingReleases,
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
                    Icons.Default.Sync,
                    contentDescription = "Refresh",
//                    modifier = Modifier.size(20.dp)
                    tint = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }

        AnimatedVisibility(
            visible = !isOnline,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "You're offline. Some features are limited.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isOnline && showNewVersionAlert && latestRelease != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "New Version Available!",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Frida ${latestRelease?.tag} was released recently.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // --- 2. DOWNLOAD MANAGEMENT CARD ---
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Get Frida Server",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))

                // New Row to put Dropdown and Button side-by-side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. ABI Dropdown
                    ExposedDropdownMenuBox(
                        expanded = abiDropdownExpanded,
                        onExpandedChange = { abiDropdownExpanded = !abiDropdownExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = abiOverride,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Target Architecture") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = abiDropdownExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )

                        ExposedDropdownMenu(
                            expanded = abiDropdownExpanded,
                            onDismissRequest = { abiDropdownExpanded = false }
                        ) {
                            abiOptionsMap.keys.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        abiOverride = selectionOption
                                        abiDropdownExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }

                    // 2. The Download/Fetch Button (Compact version on the right)
                    Button(
                        onClick = {
                            if (!isOnline) {
                                Toast.makeText(context, "Internet required", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            cachedReleases?.takeIf { it.isNotEmpty() }?.let { cached ->
                                releases = cached
                                showReleasesSheet = true
                                return@Button
                            }

                            scope.launch {
                                isFetchingReleases = true
                                val result = repo.fetchReleases()
                                if (result.isNotEmpty()) {
                                    cachedReleases = result
                                    releases = result
                                    showReleasesSheet = true
                                }
                                isFetchingReleases = false
                            }
                        },
                        modifier = Modifier
                            .height(56.dp)
                            .width(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = isOnline && !isFetchingReleases && !isDownloading,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        if (isFetchingReleases) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            val icon =
                                if (cachedReleases != null) Icons.Default.Download else Icons.Default.Sync
                            Icon(icon, contentDescription = "Fetch")
                        }
                    }
                }
            }
        }

        // --- 3. INSTALLED VERSIONS CARD ---
        Text(
            text = "Installed on Device",
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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = if (installed.isEmpty()) Arrangement.Center else Arrangement.Top
            ) {
                if (installed.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "No versions installed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    installed.forEachIndexed { index, version ->
                        InstalledVersionItem(
                            version = version,
                            isActive = activeTag == "${version.tag}-${version.arch}",
                            onSetActive = {
                                scope.launch {
                                    repo.setActiveVersion(version)
                                    activeTag = "${version.tag}-${version.arch}"
                                    refreshInstalled()
                                    Toast.makeText(
                                        context,
                                        "Set ${version.tag} as active",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onDelete = {
                                val details = serverDetails.value
                                val isActuallyRunning = details != null &&
                                        details.version == version.tag &&
                                        details.arch == version.arch
                                val isServerBusy = serverState == ServerState.RUNNING || serverState == ServerState.STARTING

                                if (isActuallyRunning && isServerBusy) {
                                    showServerRunningDeleteBlocked = true
                                } else {
                                    versionToDelete = version
                                }
                            }
                        )

                        if (index < installed.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                        }
                    }
                }
            }
        }
    }

    // --- Modal Bottom Sheet for Releases ---
    if (showReleasesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showReleasesSheet = false },
            sheetState = sheetState,
        ) {
            Text(
                "Select a Release",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider()
            LazyColumn(modifier = Modifier.padding(bottom = 32.dp)) {
                items(releases) { release ->
                    val internalAbi = abiOptionsMap[abiOverride] ?: "android-arm64"
                    val matchingAsset = release.assets.find {
                        it.name.contains("frida-server", ignoreCase = true) &&
                        it.name.contains(internalAbi)
                    }

                    ListItem(
                        headlineContent = { Text(release.tag, fontWeight = FontWeight.Bold) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                // Show description if it exists
//                                if (release.name.isNotBlank() && release.name != release.tag) {
//                                    Text(release.name, maxLines = 1)
//                                }

                                // 2. Display Date and Size
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = release.publishedAtMedium,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        " • ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = matchingAsset?.formattedSize ?: "Unknown size",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    showReleasesSheet = false
                                    selectedReleaseForDownload = release
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    // --- Delete Confirmation Dialog ---
    if (showServerRunningDeleteBlocked) {
        AlertDialog(
            onDismissRequest = { showServerRunningDeleteBlocked = false },
            title = { Text("Version in Use") },
            text = {
                val runningVersion = serverDetails.value?.version ?: "Unknown"
                val runningArch = serverDetails.value?.arch ?: "Unknown"
                Text(
                    text = "You are trying to delete Frida $runningVersion ($runningArch).\n\n" +
                            "This specific version is currently executing as a system process. " +
                            "To uninstall it, you must stop the server first.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showServerRunningDeleteBlocked = false }) {
                    Text("Got it")
                }
            }
        )
    }

    versionToDelete?.let { version ->
        AlertDialog(
            onDismissRequest = { versionToDelete = null },
            title = { Text("Delete Version") },
            text = { Text("Are you sure you want to delete Frida ${version.tag} (${version.arch})?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (repo.deleteInstalled(version)) {
                                refreshInstalled()
                                Toast.makeText(context, "Deleted ${version.tag}", Toast.LENGTH_SHORT).show()
                            }
                            if (installed.size == 1) {
                                repo.deleteFridaServer()
                            }
                            versionToDelete = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { versionToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // --- Download Confirmation Dialog ---
    selectedReleaseForDownload?.let { release ->
        val internalAbi = abiOptionsMap[abiOverride] ?: "android-arm64"
        val matchingAsset = release.assets.find {
            it.name.contains("frida-server", ignoreCase = true) &&
                    it.name.contains(internalAbi)
        }
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            onDismissRequest = { if (!isDownloading) selectedReleaseForDownload = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Install ${release.tag}")
                }
            },

            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 1. Target Row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SettingsSuggest,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Target Architecture",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = abiOverride,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        // 2. Binary Detail Row
                        matchingAsset?.let { asset ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Binary File",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = asset.name,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Size: ${asset.formattedSize}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // 3. Changelog / Release Notes
                    if (release.changelog.isNotBlank()) {
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        val scrollState = rememberScrollState()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Release Notes",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Box(modifier = Modifier
                                .heightIn(max = 120.dp)
                                .verticalScroll(scrollState)
                            ) {
                                Text(
                                    text = release.changelog,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 4. Progress Indicator
                    if (isDownloading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isDownloading,
                    onClick = {
                        scope.launch {
                            isDownloading = true
                            try {
                                val fridaFormat = abiOptionsMap[abiOverride] ?: "arm64"

                                val alreadyInstalled = installed.any {
                                    it.tag == release.tag && it.arch == fridaFormat
                                }
                                if (alreadyInstalled) {
                                    Toast.makeText(context, "Already installed!", Toast.LENGTH_SHORT).show()
                                    selectedReleaseForDownload = null
                                    return@launch
                                }

                                val installedVersion = repo.downloadAndInstall(release, fridaFormat)
                                if (installedVersion != null) {
                                    refreshInstalled()
                                    val bundle = android.os.Bundle().apply {
                                        putString("version_tag", installedVersion.tag)
                                        putString("version_arch", installedVersion.arch)
                                    }
                                    Toast.makeText(context, "Successfully installed!", Toast.LENGTH_SHORT).show()
                                    selectedReleaseForDownload = null
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Installation failed: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isDownloading = false
                            }
                        }
                    }
                ) {
                    Text(if (isDownloading) "Installing..." else "Confirm")
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { selectedReleaseForDownload = null }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
fun InstalledVersionItem(
    version: InstalledVersion,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onDelete: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val icon = if (isActive) Icons.Default.Verified else Icons.Default.Inventory2
    val iconTint =
        if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor =
        if (isActive) primaryColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    ListItem(
        modifier = if(!isActive) { Modifier.clickable(onClick = onSetActive) } else { Modifier },
        colors = ListItemDefaults.colors(Color.Transparent),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        containerColor,
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconTint
                )
            }
        },
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Version Tag - The most important info
                Text(
                    text = "v${version.tag}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) primaryColor else MaterialTheme.colorScheme.onSurface
                )

                // Architecture Badge - Secondary info
                Surface(
                    color = if (isActive) primaryColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant.copy(
                        alpha = 0.3f
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = version.arch,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Event,
                    contentDescription = "Release date",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = version.publishedAtMedium,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = " • ",
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = version.formattedSize,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        },
        trailingContent = {
            OutlinedIconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                ),
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    )
}
