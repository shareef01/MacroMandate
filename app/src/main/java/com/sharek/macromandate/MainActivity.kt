package com.sharek.macromandate

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sharek.macromandate.ui.AnalyticsScreen
import com.sharek.macromandate.ui.ControlPanelScreen
import com.sharek.macromandate.ui.HistoryScreen
import com.sharek.macromandate.ui.MainScreen
import com.sharek.macromandate.ui.MealDetailScreen
import com.sharek.macromandate.ui.theme.MacroMandateTheme
import com.sharek.macromandate.ui.theme.TerminalDesign
import com.sharek.macromandate.ui.theme.crtFilter
import com.sharek.macromandate.viewmodel.MainViewModel
import com.sharek.macromandate.widget.EXTRA_OPEN_MANUAL_ENTRY

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private var manualEntryRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWidgetIntent(intent)
        setContent {
            val terminalTheme by viewModel.terminalTheme.collectAsStateWithLifecycle()
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

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Today", Icons.Default.Dashboard)
    object History : Screen("history", "History", Icons.Default.History)
    object Analytics : Screen("analytics", "Trends", Icons.Default.Analytics)
    object ControlPanel : Screen("control_panel", "Settings", Icons.Default.Settings)
    object MealDetail : Screen("meal_detail/{mealId}", "Meal", Icons.Default.Settings)
}

@Composable
fun MacroMandateApp(
    viewModel: MainViewModel,
    manualEntryRequested: Boolean,
    onManualEntryOpened: () -> Unit
) {
    val crtEffectsEnabled by viewModel.crtEffectsEnabled.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val tokens = TerminalDesign.tokens
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(manualEntryRequested, currentRoute) {
        if (manualEntryRequested && currentRoute != null && currentRoute != Screen.Dashboard.route) {
            navController.navigate(Screen.Dashboard.route) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val topLevelScreens = remember {
        listOf(Screen.Dashboard, Screen.History, Screen.Analytics, Screen.ControlPanel)
    }
    val showBottomBar = topLevelScreens.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .crtFilter(crtEffectsEnabled),
        containerColor = tokens.background,
        contentColor = tokens.primaryText,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (showBottomBar) {
                Column {
                    HorizontalDivider(thickness = 1.dp, color = tokens.border)
                    NavigationBar(
                        containerColor = tokens.surface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        topLevelScreens.forEach { screen ->
                            val isSelected = currentRoute == screen.route
                            NavigationBarItem(
                                icon = {
                                    // Null contentDescription avoids double announcement with label below
                                    Icon(screen.icon, contentDescription = null)
                                },
                                label = {
                                    Text(
                                        text = screen.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                },
                                selected = isSelected,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = tokens.primaryAccent,
                                    selectedTextColor = tokens.primaryAccent,
                                    unselectedIconColor = tokens.secondaryText,
                                    unselectedTextColor = tokens.secondaryText,
                                    indicatorColor = tokens.primaryAccent.copy(alpha = 0.16f)
                                ),
                                onClick = {
                                    if (!isSelected) {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val navigateToDetail: (String) -> Unit = { mealId ->
            if (mealId.isNotBlank() &&
                navBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false
            ) {
                navController.navigate("meal_detail/" + android.net.Uri.encode(mealId)) {
                    launchSingleTop = true
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = navigateToDetail,
                    onNavigateToHistory = {
                        navController.navigate(Screen.History.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    manualEntryRequested = manualEntryRequested,
                    onManualEntryOpened = onManualEntryOpened
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = navigateToDetail
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
                val mealId = backStackEntry.arguments?.getString("mealId").orEmpty()
                MealDetailScreen(
                    viewModel = viewModel,
                    mealId = mealId,
                    onBack = {
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        }
                    }
                )
            }
        }
    }
}
