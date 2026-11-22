package com.sharek.macromandate

import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sharek.macromandate.ui.theme.crtFilter
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.sharek.macromandate.ui.AnalyticsScreen
import com.sharek.macromandate.ui.ControlPanelScreen
import com.sharek.macromandate.ui.MainScreen
import com.sharek.macromandate.ui.MealDetailScreen
import com.sharek.macromandate.ui.terminalOverlay
import com.sharek.macromandate.ui.theme.MacroMandateTheme
import com.sharek.macromandate.viewmodel.MainViewModel
import com.sharek.macromandate.widget.EXTRA_OPEN_MANUAL_ENTRY
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavType
import androidx.navigation.navArgument

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private var manualEntryRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWidgetIntent(intent)
        setContent {
            val terminalTheme by viewModel.terminalTheme.collectAsState()
            MacroMandateTheme(terminalTheme = terminalTheme) {
                MacroMandateApp(
                    viewModel = viewModel,
                    manualEntryRequested = manualEntryRequested,
                    onManualEntryOpened = { manualEntryRequested = false }
                )
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_MANUAL_ENTRY, false) == true) {
            intent.removeExtra(EXTRA_OPEN_MANUAL_ENTRY)
            manualEntryRequested = true
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
fun MacroMandateApp(
    viewModel: MainViewModel,
    manualEntryRequested: Boolean,
    onManualEntryOpened: () -> Unit
) {
    val crtEffectsEnabled by viewModel.crtEffectsEnabled.collectAsState()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val complianceStatus by viewModel.complianceStatus.collectAsState()
    
    LaunchedEffect(manualEntryRequested, currentRoute) {
        if (manualEntryRequested && currentRoute != null && currentRoute != Screen.Dashboard.route) {
            navController.popBackStack(Screen.Dashboard.route, inclusive = false)
        }
    }

    val items = remember { listOf(Screen.Dashboard, Screen.Analytics, Screen.ControlPanel) }

    Scaffold(
        modifier = Modifier.terminalOverlay(complianceStatus).crtFilter(crtEffectsEnabled),
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
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = { mealId ->
                        navController.navigate("meal_detail/" + android.net.Uri.encode(mealId))
                    },
                    manualEntryRequested = manualEntryRequested,
                    onManualEntryOpened = onManualEntryOpened
                )
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
