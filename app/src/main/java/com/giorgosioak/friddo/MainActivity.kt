package com.giorgosioak.friddo

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ManageHistory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.giorgosioak.friddo.data.local.PreferencesKeys
import com.giorgosioak.friddo.ui.screens.settings.SettingsScreen
import com.giorgosioak.friddo.ui.screens.logs.LogsScreen
import com.giorgosioak.friddo.ui.screens.server.ServerScreen
import com.giorgosioak.friddo.ui.screens.versions.VersionsScreen
import com.giorgosioak.friddo.ui.theme.FriddoTheme
import com.giorgosioak.friddo.ui.screens.settings.settingsDataStore
import kotlinx.coroutines.flow.map
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current

            // 1. Observe Theme Setting
            val themeSetting by context.settingsDataStore.data
                .map { it[PreferencesKeys.Settings.THEME] ?: "system" }
                .collectAsState(initial = "system")

            // 2. Observe Language Setting
            val languageSetting by context.settingsDataStore.data
                .map { it[PreferencesKeys.Settings.LANGUAGE] ?: "system" }
                .collectAsState(initial = "system")

            // Handle Language Change (Locale)
            LaunchedEffect(languageSetting) {
                if (languageSetting != "system") {
                    // Use forLanguageTag instead of the deprecated constructor
                    val languageTag = if (languageSetting == "greek") "el" else "en"
                    val locale = Locale.forLanguageTag(languageTag)

                    Locale.setDefault(locale)
                    val resources = context.resources
                    val config = android.content.res.Configuration(resources.configuration)
                    config.setLocale(locale)

                    // Update configuration for the current context
                    context.createConfigurationContext(config)
                    resources.updateConfiguration(config, resources.displayMetrics)
                    context.createConfigurationContext(config)
                }
            }

            // Determine dark theme status
            val useDarkTheme = when (themeSetting) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }

            FriddoTheme(darkTheme = useDarkTheme) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    val colorScheme = MaterialTheme.colorScheme
                    val window = (view.context as Activity).window

                    SideEffect {
                        // 1. Sync Status and Navigation bar colors to theme surface
                        // This prevents the "muddy" or greyed-out bars in Dark Mode.
                        window.statusBarColor = colorScheme.surface.toArgb()
                        window.navigationBarColor = colorScheme.surface.toArgb()

                        val insetsController = WindowCompat.getInsetsController(window, view)

                        // 2. Control Icon contrast for Android 13+
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // If light theme, use dark icons. If dark theme, use light icons.
                            insetsController.isAppearanceLightStatusBars = !useDarkTheme
                            insetsController.isAppearanceLightNavigationBars = !useDarkTheme
                        }
                    }
                }
                FriddoApp()
            }
        }
    }
}

@Composable
fun FriddoApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { FriddoBottomNav(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "server",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("server") { ServerScreen(navController = navController) }
            composable("logs") { LogsScreen() }
            composable("versions") { VersionsScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}

//@Composable
//fun FriddoBottomNav(navController: NavHostController) {
//    val items = listOf(
//        NavigationItem("server", Icons.Default.Terminal, "Dashboard"),
//        NavigationItem("logs", Icons.AutoMirrored.Filled.ReceiptLong, "Logs"),
//        NavigationItem("versions", Icons.Default.ManageHistory, "Versions"),
//        NavigationItem("settings", Icons.Default.Settings, "Settings")
//    )
//
//    NavigationBar {
//        val navBackStackEntry by navController.currentBackStackEntryAsState()
//        val currentRoute = navBackStackEntry?.destination?.route
//
//        items.forEach { item ->
//            NavigationBarItem(
//                selected = currentRoute == item.route,
//                onClick = {
//                    navController.navigate(item.route) {
//                        popUpTo(navController.graph.startDestinationId) { saveState = true }
//                        launchSingleTop = true
//                        restoreState = true
//                    }
//                },
//                label = { Text(item.label) },
//                icon = { Icon(item.icon, contentDescription = item.label) }
//            )
//        }
//    }
//}
//
//data class NavigationItem(val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)


@Composable
fun FriddoBottomNav(navController: NavHostController) {
    val items = listOf(
        NavigationItem("server", Icons.Default.Terminal, "Dashboard"),
        NavigationItem("logs", Icons.AutoMirrored.Filled.ReceiptLong, "Logs"),
        NavigationItem("versions", Icons.Default.ManageHistory, "Versions"),
        NavigationItem("settings", Icons.Default.Settings, "Settings")
    )

    val backgroundColor = MaterialTheme.colorScheme.surface
    val accentColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        NavigationBar(
            containerColor = backgroundColor,
            tonalElevation = 0.dp, // Flat look is more minimal
    //        modifier = Modifier.height(64.dp) // Slimmer height

            windowInsets = NavigationBarDefaults.windowInsets
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            items.forEach { item ->
                val isSelected = currentRoute == item.route

                NavigationBarItem(
                    selected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    // Label is null for a truly minimal look
                    label = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accentColor,
                        unselectedIconColor = unselectedColor,
                        indicatorColor = Color.Transparent
                    ),
                    icon = {
                        Column(
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    //                        verticalArrangement = Arrangement.spacedBy(4.dp)
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(24.dp)
                            )
                            // A small indicator dot instead of text
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 12.dp, height = 3.dp)
                                    .background(
                                        color = if (isSelected) accentColor else Color.Transparent,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    },
                )
            }
        }
    }
}

data class NavigationItem(
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String
)
