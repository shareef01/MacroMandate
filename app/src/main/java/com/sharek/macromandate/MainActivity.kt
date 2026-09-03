package com.sharek.macromandate

import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    // Whether the widget's "Log a meal" button launched this instance. Held as
    // Compose state, not a local val read once in onCreate, because
    // onNewIntent (below) needs to update it for an already-running Activity.
    private var openManualEntryPending by mutableStateOf(false)

    // POST_NOTIFICATIONS is not requested here.
    //
    // It used to be asked for in onCreate, on first launch, before the user had
    // seen the app or knew it had reminders at all — a system dialog as the first
    // thing they were shown, for a feature they had not asked for. A permission
    // asked for out of context is a permission that gets denied, and on Android
    // 13+ a second denial is permanent. It is now requested from the reminders
    // toggle in Settings, at the moment the user turns the feature on.
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openManualEntryPending = readOpenManualEntry(intent)
        setContent {
            val terminalTheme by viewModel.terminalTheme.collectAsState()
            MacroMandateTheme(terminalTheme = terminalTheme) {
                MacroMandateApp(
                    viewModel = viewModel,
                    openManualEntryOnLaunch = openManualEntryPending,
                    onManualEntryLaunchConsumed = { openManualEntryPending = false }
                )
            }
        }
    }

    // launchMode="singleTop" (manifest) routes a widget tap while the app is
    // already running here instead of stacking a second Activity instance on
    // top of it — without this, the app briefly had two dashboards on the back
    // stack, one of which knew to open manual entry and one of which didn't.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openManualEntryPending = readOpenManualEntry(intent)
    }

    // The widget's "Log a meal" button used to open the app to the bare
    // dashboard — one more tap away from the action its own label promised.
    // This extra lets it open straight into manual entry.
    private fun readOpenManualEntry(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_OPEN_MANUAL_ENTRY, false) ?: false
}

// Nav labels are single words: three long labels overflowed their items and ran
// into the screen edges on a 1080px display.
sealed class Screen(val route: String, @StringRes val titleRes: Int, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", R.string.nav_today, Icons.Default.Dashboard)
    object Analytics : Screen("analytics", R.string.nav_trends, Icons.Default.Analytics)
    object ControlPanel : Screen("control_panel", R.string.nav_settings, Icons.Default.Settings)
    object MealDetail : Screen("meal_detail/{mealId}", R.string.nav_meal, Icons.Default.Settings)
}

@Composable
fun MacroMandateApp(
    viewModel: MainViewModel,
    openManualEntryOnLaunch: Boolean = false,
    onManualEntryLaunchConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    // The compliance status no longer decides whether the app is usable. It
    // previously short-circuited the whole nav graph: CRISIS replaced every
    // screen with a plea form, and a model's answer to that form could wipe the
    // meal log or lock the app permanently. Someone off their calorie target
    // still gets to reach their own data.
    val complianceStatus by viewModel.complianceStatus.collectAsState()

    val items = remember { listOf(Screen.Dashboard, Screen.Analytics, Screen.ControlPanel) }
    // "Reduce visual effects" in Settings — off by default, since the overlay
    // is already tuned low enough not to need it for most people.
    val reduceVisualEffects by viewModel.reduceVisualEffects.collectAsState()

    Scaffold(
        modifier = Modifier.terminalOverlay(enabled = !reduceVisualEffects),
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
                            icon = {
                                // The label below already names the destination, so
                                // describing the icon too makes TalkBack say it twice.
                                Icon(screen.icon, contentDescription = null)
                            },
                            label = {
                                Text(
                                    stringResource(screen.titleRes),
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
                    openManualEntryOnLaunch = openManualEntryOnLaunch,
                    onManualEntryLaunchConsumed = onManualEntryLaunchConsumed,
                    reduceVisualEffects = reduceVisualEffects,
                    onNavigateToDetail = { mealId ->
                        navController.navigate("meal_detail/$mealId")
                    }
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
