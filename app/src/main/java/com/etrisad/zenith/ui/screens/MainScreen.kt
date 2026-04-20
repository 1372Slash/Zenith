package com.etrisad.zenith.ui.screens

import androidx.compose.animation.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.etrisad.zenith.ui.components.PermissionBottomSheet
import com.etrisad.zenith.ui.navigation.Screen
import com.etrisad.zenith.ui.navigation.navItems
import com.etrisad.zenith.ui.screens.focus.FocusScreen
import com.etrisad.zenith.ui.screens.home.HomeScreen
import com.etrisad.zenith.ui.screens.home.UsageStatsScreen
import com.etrisad.zenith.ui.screens.settings.SettingsScreen
import com.etrisad.zenith.ui.viewmodel.FocusViewModel
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import com.etrisad.zenith.util.hasAllPermissions

import com.etrisad.zenith.data.preferences.ThemeConfig
import com.etrisad.zenith.data.preferences.UserPreferences

@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    focusViewModel: FocusViewModel,
    userPreferencesRepository: UserPreferencesRepository
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    val preferences by userPreferencesRepository.userPreferencesFlow.collectAsState(
        initial = UserPreferences(ThemeConfig.FOLLOW_SYSTEM, true, false, 0, 60, 30)
    )

    var showPermissionSheet by remember { mutableStateOf(false) }
    
    // Check permissions and update sheet visibility
    fun checkPermissions() {
        val hasUsageStats = com.etrisad.zenith.util.hasUsageStatsPermission(context)
        val hasOverlay = android.provider.Settings.canDrawOverlays(context)
        val hasAccessibility = com.etrisad.zenith.util.isAccessibilityServiceEnabled(context)
        
        val allGranted = hasUsageStats && hasOverlay && (hasAccessibility || preferences.accessibilityDisabled)
        showPermissionSheet = !allGranted
    }

    // Re-check permissions when app resumes or preferences change
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, preferences.accessibilityDisabled) {
        checkPermissions()
    }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showPermissionSheet) {
        PermissionBottomSheet(
            preferencesRepository = userPreferencesRepository,
            onDismissRequest = { 
                showPermissionSheet = false
            },
            onAllPermissionsGranted = {
                showPermissionSheet = false
            }
        )
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            val showBottomBar = currentRoute != Screen.UsageStats.route && currentRoute?.startsWith("app_detail") == false

            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar {
                    val currentDestination = navBackStackEntry?.destination
                    navItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                if (currentDestination?.route != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
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
    ) { innerPadding ->
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val isDeepScreen = currentRoute == Screen.UsageStats.route || currentRoute?.startsWith("app_detail") == true

        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(
                bottom = if (isDeepScreen) 0.dp else innerPadding.calculateBottomPadding(),
                top = innerPadding.calculateTopPadding()
            ),
            enterTransition = {
                val initialRoute = initialState.destination.route
                val targetRoute = targetState.destination.route
                
                val isTargetDeep = targetRoute == Screen.UsageStats.route || targetRoute?.startsWith("app_detail") == true
                val isInitialDeep = initialRoute == Screen.UsageStats.route || initialRoute?.startsWith("app_detail") == true

                val animationSpec = spring<IntOffset>(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )

                if (isTargetDeep && !isInitialDeep) {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = animationSpec) + fadeIn()
                } else {
                    val initialIndex = navItems.indexOfFirst { it.route == initialRoute }
                    val targetIndex = navItems.indexOfFirst { it.route == targetRoute }
                    
                    if (targetIndex > initialIndex) {
                        slideInHorizontally(initialOffsetX = { it }, animationSpec = animationSpec) + fadeIn()
                    } else {
                        slideInHorizontally(initialOffsetX = { -it }, animationSpec = animationSpec) + fadeIn()
                    }
                }
            },
            exitTransition = {
                val initialRoute = initialState.destination.route
                val targetRoute = targetState.destination.route

                val isTargetDeep = targetRoute == Screen.UsageStats.route || targetRoute?.startsWith("app_detail") == true
                
                val animationSpec = spring<IntOffset>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )

                if (isTargetDeep) {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = animationSpec) + fadeOut()
                } else {
                    val initialIndex = navItems.indexOfFirst { it.route == initialRoute }
                    val targetIndex = navItems.indexOfFirst { it.route == targetRoute }

                    if (targetIndex > initialIndex) {
                        slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = animationSpec) + fadeOut()
                    } else {
                        slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = animationSpec) + fadeOut()
                    }
                }
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeIn()
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeOut()
            }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    homeViewModel,
                    userPreferencesRepository,
                    onSeeFullList = { navController.navigate(Screen.UsageStats.route) },
                    onAppClick = { packageName ->
                        navController.navigate(Screen.AppDetail.createRoute(packageName))
                    }
                )
            }
            composable(Screen.Focus.route) {
                FocusScreen(
                    focusViewModel,
                    onAppClick = { packageName ->
                        navController.navigate(Screen.AppDetail.createRoute(packageName))
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(userPreferencesRepository)
            }
            composable(Screen.UsageStats.route) {
                UsageStatsScreen(
                    viewModel = homeViewModel,
                    onBack = { navController.popBackStack() },
                    onAppClick = { packageName ->
                        navController.navigate(Screen.AppDetail.createRoute(packageName))
                    }
                )
            }
            composable(
                route = Screen.AppDetail.route,
                arguments = listOf(androidx.navigation.navArgument("packageName") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
                com.etrisad.zenith.ui.screens.home.AppDetailScreen(
                    packageName = packageName,
                    viewModel = homeViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
