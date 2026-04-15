package com.giorgosioak.friddo.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.giorgosioak.friddo.data.local.DefaultValues
import com.giorgosioak.friddo.data.local.PreferencesKeys
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// DataStore extension
val Context.settingsDataStore by preferencesDataStore(name = "friddo_settings")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = context.settingsDataStore
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val appVersion = packageInfo.versionName ?: "Unknown"
    val defaultPath = remember(context) { DefaultValues.binaryPath(context) }
    val uriHandler = LocalUriHandler.current

    val settings by remember {
        dataStore.data.map { prefs ->
            SettingsState(
                autoStart = prefs[PreferencesKeys.Settings.AUTO_START] ?: DefaultValues.AUTO_START,
                persistentLogs = prefs[PreferencesKeys.Settings.PERSISTENT_LOGS] ?: DefaultValues.PERSISTENT_LOGS,
                theme = prefs[PreferencesKeys.Settings.THEME] ?: DefaultValues.THEME,
                language = prefs[PreferencesKeys.Settings.LANGUAGE] ?: DefaultValues.LANGUAGE,
                listenAddress = prefs[PreferencesKeys.Settings.SERVER_ADDRESS] ?: DefaultValues.ADDRESS,
                port = prefs[PreferencesKeys.Settings.SERVER_PORT] ?: DefaultValues.PORT,
                binaryPath = prefs[PreferencesKeys.Settings.BINARY_PATH] ?: defaultPath
            )
        }
    }.collectAsState(initial = SettingsState(binaryPath = defaultPath))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Manage Friddo and Frida server settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --- SERVER CONFIGURATION SECTION ---
        SettingsGroup(title = "Server Configuration") {
            SettingSwitchItem(
                title = "Auto-start server",
                subtitle = "Start server automatically when app opens",
                checked = settings.autoStart,
                onCheckedChange = { isChecked ->
                    scope.launch { dataStore.edit { it[PreferencesKeys.Settings.AUTO_START] = isChecked } }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

            // IP Address Setting
            SettingTextFieldItem(
                title = "IP Address",
                subtitle = "Choose which network interface the server listens on (Default: ${DefaultValues.ADDRESS} for private use)",
                value = settings.listenAddress,
                defaultValue = DefaultValues.ADDRESS,
                onValueChange = { newValue ->
                    scope.launch { dataStore.edit { it[PreferencesKeys.Settings.SERVER_ADDRESS] = newValue } }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

            // Port Setting
            SettingTextFieldItem(
                title = "Port",
                subtitle = "The communication gateway for Frida tools (Default: ${DefaultValues.PORT})",
                value = settings.port,
                defaultValue = DefaultValues.PORT,
                keyboardType = KeyboardType.Number,
                onValueChange = { newValue ->
                    scope.launch { dataStore.edit { it[PreferencesKeys.Settings.SERVER_PORT] = newValue } }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

            // Binary Path Setting
            SettingTextFieldItem(
                title = "Binary Path",
                subtitle = "The absolute location of the frida-server executable on your device storage",
                value = settings.binaryPath,
                defaultValue = DefaultValues.binaryPath(context),
                onValueChange = { newValue ->
                    scope.launch { dataStore.edit { it[PreferencesKeys.Settings.BINARY_PATH] = newValue } }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

            SettingSwitchItem(
                title = "Persistent logs",
                subtitle = "Keep logs even after server stops",
                checked = settings.persistentLogs,
                onCheckedChange = { isChecked ->
                    scope.launch { dataStore.edit { it[PreferencesKeys.Settings.PERSISTENT_LOGS] = isChecked } }
                }
            )
        }

        // --- APPEARANCE & LOCALIZATION ---
        SettingsGroup(title = "Appearance") {
//        SettingsGroup(title = "Appearance & Language") {
            SettingDropdownItem(
                icon = Icons.Default.Palette,
                title = "Theme",
                selected = settings.theme.replaceFirstChar { it.uppercase() },
                options = listOf("System", "Light", "Dark"),
                onSelect = { choice ->
                    scope.launch { dataStore.edit { it[PreferencesKeys.Settings.THEME] = choice.lowercase() } }
                }
            )
//            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
//            SettingDropdownItem(
//                icon = Icons.Default.Language,
//                title = "Language",
//                selected = settings.language.replaceFirstChar { it.uppercase() },
//                options = listOf("System", "English", "Greek"),
//                onSelect = { choice ->
//                    scope.launch { dataStore.edit { it[PreferencesKeys.Settings.LANGUAGE] = choice.lowercase() } }
//                }
//            )
        }

        // --- APP INFO SECTION ---
        SettingsGroup(title = "App Info") {
            ListItem(
                modifier = Modifier.padding(vertical = 8.dp),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Text("About Friddo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                },
                supportingContent = {
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Friddo is a specialized utility for Android power users and security researchers. It automates the management of Frida servers, handling downloads, versioning, and execution with root privileges.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        Text(
                            text = "To learn more about the instrumentation toolkit that powers this backend, visit the official documentation:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            onClick = { uriHandler.openUri("https://frida.re/") },
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "frida.re official website",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                // ... rest of your items (Version, Source Code, etc.)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

            SettingInfoItem(
                title = "Version",
                value = "v$appVersion",
                icon = Icons.Default.Code
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

            SettingInfoItem(
                title = "Source Code",
                value = "https://github.com/giorgosioak/friddo",
                icon = Icons.Default.Star,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/giorgosioak/friddo")
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

            SettingInfoItem(
                title = "License",
                value = "GNU GPLv3",
                icon = Icons.Default.Gavel,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/giorgosioak/friddo/blob/main/LICENSE")
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

            SettingInfoItem(
                title = "System Access",
                value = "This app requires Root access to manage and execute the Frida server on your system.",
                icon = Icons.Default.LockOpen
            )
        }
    }
}

@Composable
private fun SettingTextFieldItem(
    title: String,
    subtitle: String,
    value: String,
    defaultValue: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var tempValue by remember(showDialog) { mutableStateOf(value) }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = {
            Text(
                text = value.ifBlank { subtitle },
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Box(
                modifier = Modifier.fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit $title",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    enabled = tempValue.isNotBlank(),
                    onClick = { onValueChange(tempValue); showDialog = false }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { tempValue = defaultValue }) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            title = {
                Text(title, style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    if (title.contains("IP Address", ignoreCase = true)) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AddressTip("127.0.0.1", "Localhost (Private)")
                                AddressTip("0.0.0.0", "All Interfaces (Public)")
                                AddressTip("192.168.x.x", "Specific Interface")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = tempValue,
                        onValueChange = { tempValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Value") },
                        placeholder = { Text(defaultValue) },
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (tempValue.isNotBlank()) {
                                    onValueChange(tempValue)
                                    showDialog = false
                                }
                            }
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                    )
                }
            }
        )
    }
}

@Composable
private fun AddressTip(ip: String, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.widthIn(100.dp)
        ) {
            Text(
                text = ip,
                modifier = Modifier.padding(vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            content = content
        )
    }
}

@Composable
fun SettingSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .toggleable(
                value = checked,
                onValueChange = { onCheckedChange(it) },
                role = Role.Switch
            ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },

        supportingContent = {
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },

        trailingContent = {
            Box(
                modifier = Modifier.fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Switch(
                    checked = checked,
                    onCheckedChange = null
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingDropdownItem(
    icon: ImageVector,
    title: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min) // Essential for vertical centering in Box
            .clickable { expanded = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        trailingContent = {
            Box(
                modifier = Modifier.fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    Surface(
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = selected,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.widthIn(min = 140.dp)
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, style = MaterialTheme.typography.bodyLarge) },
                                onClick = {
                                    onSelect(option)
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun SettingInfoItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Text(
                text = value,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            if (icon != null) {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp) // Updated to 24dp to match other items
                    )
                }
            }
        }
    )
}



private data class SettingsState(
    val autoStart: Boolean = false,
    val persistentLogs: Boolean = true,
    val theme: String = "system",
    val language: String = "system",
    val listenAddress: String = DefaultValues.ADDRESS,
    val port: String = DefaultValues.PORT,
    val binaryPath: String = ""
)
