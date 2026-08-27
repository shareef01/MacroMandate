package com.sharek.macromandate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sharek.macromandate.notification.NotificationManagerHelper
import com.sharek.macromandate.ui.AnalyticsScreen
import com.sharek.macromandate.ui.ControlPanelScreen
import com.sharek.macromandate.ui.LeniencyPleaScreen
import com.sharek.macromandate.ui.PermanentLockdownScreen
import com.sharek.macromandate.ui.MainScreen
import com.sharek.macromandate.ui.MealDetailScreen
import com.sharek.macromandate.ui.terminalOverlay
import com.sharek.macromandate.ui.theme.MacroMandateTheme
import com.sharek.macromandate.viewmodel.ComplianceStatus
import com.sharek.macromandate.viewmodel.MainViewModel
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavType
import androidx.navigation.navArgument

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    // POST_NOTIFICATIONS is required on Android 13+ for the enforcement and HUD
    // notifications to be visible at all; request it once at launch.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                NotificationManagerHelper.createNotificationChannel(this)
            }
        }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        NotificationManagerHelper.createNotificationChannel(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        setContent {
            val terminalTheme by viewModel.terminalTheme.collectAsState()
            MacroMandateTheme(terminalTheme = terminalTheme) {
                MacroMandateApp(viewModel = viewModel)
            }
        }
    }
}

// Nav labels are single words: three long labels overflowed their items and ran
// into the screen edges on a 1080px display.
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Today", Icons.Default.Dashboard)
    object Analytics : Screen("analytics", "Trends", Icons.Default.Analytics)
    object ControlPanel : Screen("control_panel", "Settings", Icons.Default.Settings)
    object MealDetail : Screen("meal_detail/{mealId}", "Meal", Icons.Default.Settings) // Icon is placeholder
}

@Composable
fun MacroMandateApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val complianceStatus by viewModel.complianceStatus.collectAsState()
    
    if (complianceStatus == ComplianceStatus.LOCKED) {
        PermanentLockdownScreen(viewModel = viewModel)
        return
    }

    if (complianceStatus == ComplianceStatus.CRISIS) {
        LeniencyPleaScreen(
            viewModel = viewModel,
            onLeniencyGranted = { /* Handled by State observation */ }
        )
        return
    }

    val items = remember { listOf(Screen.Dashboard, Screen.Analytics, Screen.ControlPanel) }

    Scaffold(
        modifier = Modifier.terminalOverlay(complianceStatus),
        contentWindowInsets = WindowInsets(0.dp), // Handle insets manually in screens
        bottomBar = {
            if (currentRoute != Screen.Dashboard.route && currentRoute != Screen.Analytics.route && currentRoute != Screen.ControlPanel.route) {
                // Hide bottom bar on detail screen
            } else {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                    modifier = Modifier,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = {
                                Text(
                                    screen.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            },
                            selected = currentRoute == screen.route,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                MainScreen(viewModel = viewModel, onNavigateToDetail = { mealId ->
                    navController.navigate("meal_detail/$mealId")
                })
            }
            composable(Screen.Analytics.route) {
                AnalyticsScreen(viewModel = viewModel)
            }
            composable(Screen.ControlPanel.route) {
                ControlPanelScreen(viewModel = viewModel)
            }
            composable(
                route = Screen.MealDetail.route,
                arguments = listOf(navArgument("mealId") { type = NavType.StringType })
            ) { backStackEntry ->
                val mealId = backStackEntry.arguments?.getString("mealId") ?: ""
                MealDetailScreen(
                    viewModel = viewModel,
                    mealId = mealId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
