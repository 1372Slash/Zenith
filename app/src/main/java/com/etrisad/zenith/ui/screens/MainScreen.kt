package com.etrisad.zenith.ui.screens

import androidx.compose.animation.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.ui.text.font.FontWeight
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
import com.etrisad.zenith.ui.components.UserBottomSheet
import com.etrisad.zenith.ui.components.ZenithHeader
import com.etrisad.zenith.ui.navigation.Screen
import com.etrisad.zenith.ui.navigation.navItems
import com.etrisad.zenith.ui.screens.focus.FocusScreen
import com.etrisad.zenith.ui.screens.home.HomeScreen
import com.etrisad.zenith.ui.screens.home.UsageStatsScreen
import com.etrisad.zenith.ui.screens.bedtime.BedtimeScreen
import com.etrisad.zenith.ui.screens.settings.SettingsScreen
import com.etrisad.zenith.ui.viewmodel.FocusViewModel
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import com.etrisad.zenith.ui.viewmodel.BedtimeViewModel
import com.etrisad.zenith.ui.viewmodel.BedtimeViewModelFactory

import com.etrisad.zenith.data.preferences.UserPreferences
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    focusViewModel: FocusViewModel,
    userPreferencesRepository: UserPreferencesRepository,
    windowSizeClass: WindowSizeClass
) {
    val bedtimeViewModel: BedtimeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = BedtimeViewModelFactory(userPreferencesRepository)
    )
    val navController = rememberNavController()
    val context = LocalContext.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDeepScreen =
        currentRoute == Screen.UsageStats.route || 
        currentRoute == Screen.Bedtime.route ||
        currentRoute?.startsWith("app_detail") == true

    val enterAlwaysScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val pinnedScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val scrollBehavior = if (isDeepScreen) pinnedScrollBehavior else enterAlwaysScrollBehavior

    val preferences by userPreferencesRepository.userPreferencesFlow.collectAsState(
        initial = UserPreferences()
    )

    val useNavigationRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    var showPermissionSheet by remember { mutableStateOf(false) }
    var showUserSheet by remember { mutableStateOf(false) }

    fun checkPermissions() {
        val hasUsageStats = com.etrisad.zenith.util.hasUsageStatsPermission(context)
        val hasOverlay = android.provider.Settings.canDrawOverlays(context)
        val hasAccessibility = com.etrisad.zenith.util.isAccessibilityServiceEnabled(context)

        val allGranted =
            hasUsageStats && hasOverlay && (hasAccessibility || preferences.accessibilityDisabled)
        showPermissionSheet = !allGranted
    }

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

    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        if (useNavigationRail) {
            val showNavRail =
                currentRoute != Screen.UsageStats.route && 
                currentRoute != Screen.Bedtime.route &&
                currentRoute?.startsWith("app_detail") == false

            AnimatedVisibility(
                visible = showNavRail,
                enter = slideInHorizontally(initialOffsetX = { -it }) + expandHorizontally(expandFrom = Alignment.Start),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + shrinkHorizontally(shrinkTowards = Alignment.Start)
            ) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    header = {
                    }
                ) {
                    val currentDestination = navBackStackEntry?.destination
                    navItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationRailItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
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

        Scaffold(
            modifier = Modifier
                .weight(1f)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                ZenithHeader(
                    currentRoute = currentRoute,
                    scrollBehavior = scrollBehavior,
                    isNavRailVisible = useNavigationRail && !isDeepScreen,
                    userName = preferences.userName,
                    onBack = { navController.popBackStack() },
                    actions = {
                        AnimatedVisibility(
                            visible = !isDeepScreen,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            IconButton(onClick = { showUserSheet = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountCircle,
                                    contentDescription = "User Profile"
                                )
                            }
                        }
                        
                        AnimatedVisibility(
                            visible = currentRoute == Screen.Bedtime.route && preferences.bedtimeEnabled,
                            enter = fadeIn(animationSpec = tween(600)) + scaleIn(initialScale = 0.8f, animationSpec = tween(600)) + expandHorizontally(expandFrom = Alignment.End, animationSpec = spring(stiffness = Spring.StiffnessLow)),
                            exit = fadeOut(animationSpec = tween(600)) + scaleOut(targetScale = 0.8f, animationSpec = tween(600)) + shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = spring(stiffness = Spring.StiffnessLow))
                        ) {
                            Switch(
                                checked = preferences.bedtimeEnabled,
                                onCheckedChange = { bedtimeViewModel.setBedtimeEnabled(it) },
                                modifier = Modifier.padding(end = 16.dp),
                                thumbContent = {
                                    Icon(
                                        imageVector = Icons.Default.Bedtime,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            if (showUserSheet) {
                UserBottomSheet(
                    userName = preferences.userName,
                    repository = userPreferencesRepository,
                    onDismissRequest = { showUserSheet = false }
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        val initialRoute = initialState.destination.route
                        val targetRoute = targetState.destination.route

                        val isTargetDeep =
                            targetRoute == Screen.UsageStats.route || 
                            targetRoute == Screen.Bedtime.route ||
                            targetRoute?.startsWith("app_detail") == true
                        val isInitialDeep =
                            initialRoute == Screen.UsageStats.route || 
                            initialRoute == Screen.Bedtime.route ||
                            initialRoute?.startsWith("app_detail") == true

                        val animationSpec = spring<IntOffset>(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )

                        if (isTargetDeep && !isInitialDeep) {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = animationSpec
                            ) + fadeIn()
                        } else {
                            val initialIndex = navItems.indexOfFirst { it.route == initialRoute }
                            val targetIndex = navItems.indexOfFirst { it.route == targetRoute }

                            if (targetIndex > initialIndex) {
                                slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = animationSpec
                                ) + fadeIn()
                            } else {
                                slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = animationSpec
                                ) + fadeIn()
                            }
                        }
                    },
                    exitTransition = {
                        val initialRoute = initialState.destination.route
                        val targetRoute = targetState.destination.route

                        val isTargetDeep =
                            targetRoute == Screen.UsageStats.route || 
                            targetRoute == Screen.Bedtime.route ||
                            targetRoute?.startsWith("app_detail") == true

                        val animationSpec = spring<IntOffset>(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        )

                        if (isTargetDeep) {
                            slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = animationSpec
                            ) + fadeOut()
                        } else {
                            val initialIndex = navItems.indexOfFirst { it.route == initialRoute }
                            val targetIndex = navItems.indexOfFirst { it.route == targetRoute }

                            if (targetIndex > initialIndex) {
                                slideOutHorizontally(
                                    targetOffsetX = { -it / 3 },
                                    animationSpec = animationSpec
                                ) + fadeOut()
                            } else {
                                slideOutHorizontally(
                                    targetOffsetX = { it / 3 },
                                    animationSpec = animationSpec
                                ) + fadeOut()
                            }
                        }
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { -it / 3 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) + fadeIn()
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) + fadeOut()
                    }
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(
                            homeViewModel,
                            userPreferencesRepository,
                            innerPadding,
                            onSeeFullList = { navController.navigate(Screen.UsageStats.route) },
                            onAppClick = { packageName ->
                                navController.navigate(Screen.AppDetail.createRoute(packageName))
                            },
                            onBedtimeClick = { navController.navigate(Screen.Bedtime.route) }
                        )
                    }
                    composable(Screen.Focus.route) {
                        FocusScreen(
                            focusViewModel,
                            innerPadding,
                            onAppClick = { packageName ->
                                navController.navigate(Screen.AppDetail.createRoute(packageName))
                            }
                        )
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(userPreferencesRepository, innerPadding)
                    }
                    composable(Screen.Bedtime.route) {
                        BedtimeScreen(bedtimeViewModel, innerPadding)
                    }
                    composable(Screen.UsageStats.route) {
                        UsageStatsScreen(
                            viewModel = homeViewModel,
                            innerPadding = innerPadding,
                            onAppClick = { packageName ->
                                navController.navigate(Screen.AppDetail.createRoute(packageName))
                            }
                        )
                    }
                    composable(
                        route = Screen.AppDetail.route,
                        arguments = listOf(androidx.navigation.navArgument("packageName") {
                            type = androidx.navigation.NavType.StringType
                        })
                    ) { backStackEntry ->
                        val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
                        com.etrisad.zenith.ui.screens.home.AppDetailScreen(
                            packageName = packageName,
                            viewModel = homeViewModel,
                            innerPadding = innerPadding
                        )
                    }
                }

                if (!useNavigationRail) {
                    val showBottomBar =
                        currentRoute != Screen.UsageStats.route && 
                        currentRoute != Screen.Bedtime.route &&
                        currentRoute?.startsWith("app_detail") == false

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showBottomBar,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = slideInVertically(initialOffsetY = { it }) + expandVertically(expandFrom = Alignment.Top),
                        exit = slideOutVertically(targetOffsetY = { it }) + shrinkVertically(shrinkTowards = Alignment.Top)
                    ) {
                        AnimatedContent(
                            targetState = preferences.floatingTabBarEnabled,
                            transitionSpec = {
                                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                        scaleIn(initialScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessLow)))
                                    .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                            scaleOut(targetScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessLow)))
                            },
                            label = "TabBarTransition"
                        ) { isFloating ->
                            if (isFloating) {
                                val currentDestination = navBackStackEntry?.destination
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 36.dp),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    HorizontalFloatingToolbar(
                                        expanded = true,
                                        modifier = Modifier
                                            .animateContentSize()
                                            .height(68.dp),
                                        shape = RoundedCornerShape(100),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                                            toolbarContainerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ) {
                                        navItems.forEach { screen ->
                                            val selected =
                                                currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                            ShortNavigationBarItem(
                                                selected = selected,
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
                                                },
                                                icon = {
                                                    val extraHeight by animateDpAsState(
                                                        targetValue = if (selected) 16.dp else 0.dp,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                            stiffness = Spring.StiffnessLow
                                                        ),
                                                        label = "indicatorHeight"
                                                    )
                                                    Box(
                                                        modifier = Modifier.height(26.dp + extraHeight),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                                            contentDescription = screen.title,
                                                            modifier = Modifier.size(26.dp)
                                                        )
                                                    }
                                                },
                                                label = {
                                                    androidx.compose.animation.AnimatedVisibility(
                                                        visible = selected,
                                                        enter = fadeIn() + slideInHorizontally(
                                                            initialOffsetX = { -15 },
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                                stiffness = Spring.StiffnessLow
                                                            )
                                                        ) + expandHorizontally(expandFrom = Alignment.Start),
                                                        exit = fadeOut() + slideOutHorizontally(
                                                            targetOffsetX = { -15 }) + shrinkHorizontally(
                                                            shrinkTowards = Alignment.Start
                                                        )
                                                    ) {
                                                        Text(
                                                            text = screen.title,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            maxLines = 1,
                                                            modifier = Modifier.padding(start = 2.dp, end = 4.dp)
                                                        )
                                                    }
                                                },
                                                iconPosition = NavigationItemIconPosition.Start,
                                                colors = ShortNavigationBarItemDefaults.colors(
                                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                                    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                                    selectedIndicatorColor = MaterialTheme.colorScheme.primary,
                                                    unselectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    unselectedTextColor = Color.Transparent
                                                ),
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp)
                                                    .fillMaxHeight()
                                            )
                                        }
                                    }
                                }
                            } else {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    val currentDestination = navBackStackEntry?.destination
                                    navItems.forEach { screen ->
                                        val selected =
                                            currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                        NavigationBarItem(
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                                    contentDescription = null
                                                )
                                            },
                                            label = { Text(screen.title) },
                                            selected = selected,
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
                    }
                }
            }
        }
    }
}
