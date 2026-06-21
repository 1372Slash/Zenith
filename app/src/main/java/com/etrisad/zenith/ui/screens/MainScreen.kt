package com.etrisad.zenith.ui.screens

import androidx.compose.animation.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.etrisad.zenith.data.preferences.ThemeConfig
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.etrisad.zenith.ui.components.PermissionBottomSheet
import com.etrisad.zenith.ui.components.onboarding.OnboardingStatsBottomSheet
import com.etrisad.zenith.ui.components.onboarding.OnboardingUpdateBottomSheet
import com.etrisad.zenith.ui.components.UserBottomSheet
import com.etrisad.zenith.ui.components.ZenithHeader
import com.etrisad.zenith.ui.components.ConfirmBottomSheet
import com.etrisad.zenith.ui.navigation.Screen
import com.etrisad.zenith.ui.navigation.navItems
import com.etrisad.zenith.ui.screens.focus.FocusScreen
import com.etrisad.zenith.ui.screens.home.HomeScreen
import com.etrisad.zenith.ui.screens.home.UsageStatsScreen
import com.etrisad.zenith.ui.screens.bedtime.BedtimeScreen
import com.etrisad.zenith.ui.screens.graceperiod.GracePeriodScreen
import com.etrisad.zenith.ui.screens.settings.SettingsScreen
import com.etrisad.zenith.ui.viewmodel.FocusViewModel
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import com.etrisad.zenith.ui.viewmodel.BedtimeViewModel
import com.etrisad.zenith.ui.viewmodel.BedtimeViewModelFactory
import com.etrisad.zenith.ui.viewmodel.GracePeriodViewModel
import com.etrisad.zenith.ui.viewmodel.GracePeriodViewModelFactory
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.data.manager.GitHubUpdateManager
import com.etrisad.zenith.data.remote.model.GitHubRelease
import com.etrisad.zenith.ui.components.UpdateBottomSheet
import com.etrisad.zenith.data.preferences.UserPreferences
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    focusViewModel: FocusViewModel,
    shieldRepository: ShieldRepository,
    userPreferencesRepository: UserPreferencesRepository,
    windowSizeClass: WindowSizeClass,
    initialPackageName: String? = null,
    onInitialPackageHandled: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bedtimeViewModel: BedtimeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = BedtimeViewModelFactory(context, userPreferencesRepository, shieldRepository)
    )
    val gracePeriodViewModel: GracePeriodViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = GracePeriodViewModelFactory(userPreferencesRepository)
    )
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentDestination = navBackStackEntry?.destination

    var activeTabRoute by rememberSaveable { mutableStateOf(Screen.Home.route) }
    LaunchedEffect(currentDestination) {
        navItems.find { item ->
            currentDestination?.hierarchy?.any { it.route == item.route } == true
        }?.let {
            activeTabRoute = it.route
        }
    }

    LaunchedEffect(initialPackageName) {
        if (initialPackageName != null) {
            val currentDetailRoute = Screen.AppDetail.createRoute(initialPackageName)
            if (currentRoute != currentDetailRoute) {
                navController.navigate(currentDetailRoute) {
                    val homeRoute = Screen.Home.route
                    if (currentRoute != null &&
                        currentRoute != homeRoute && 
                        !currentRoute.startsWith("app_detail")) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            onInitialPackageHandled()
        }
    }

    val isDeepScreen =
        currentRoute == Screen.UsageStats.route ||
                currentRoute == Screen.Bedtime.route ||
                currentRoute == Screen.GracePeriod.route ||
                currentRoute == Screen.DatabaseDebug.route ||
                currentRoute == Screen.DataRepairment.route ||
                currentRoute == Screen.FontTest.route ||
                currentRoute == Screen.GSFlexCustomizer.route ||
                currentRoute == Screen.SystemUsageDebug.route ||
                currentRoute?.startsWith("settings_category") == true ||
                currentRoute?.startsWith("app_detail") == true

    val enterAlwaysScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val pinnedScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val scrollBehavior = if (isDeepScreen) pinnedScrollBehavior else enterAlwaysScrollBehavior

    LaunchedEffect(currentRoute) {
        if (!isDeepScreen) {
            enterAlwaysScrollBehavior.state.heightOffset = 0f
            enterAlwaysScrollBehavior.state.contentOffset = 0f
        }
    }

    val preferencesResult by userPreferencesRepository.userPreferencesFlow.collectAsState(
        initial = null
    )

    if (preferencesResult == null) return

    val preferences = preferencesResult!!

    val homeUiState by homeViewModel.uiState.collectAsState()
    val focusUiState by focusViewModel.uiState.collectAsState()

    val useNavigationRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    val scope = rememberCoroutineScope()
    var bedtimeSwitchVisible by remember { mutableStateOf(false) }
    var bedtimeSwitchInLayout by remember { mutableStateOf(false) }
    var showPauseSheet by remember { mutableStateOf(false) }
    var gracePeriodSwitchVisible by remember { mutableStateOf(false) }
    var gracePeriodSwitchInLayout by remember { mutableStateOf(false) }
    val performanceBackInterceptor = remember { mutableStateOf<() -> Boolean>({ false }) }

    var showBatchDeleteSheet by remember { mutableStateOf(false) }
    var showBatchPauseSheet by remember { mutableStateOf(false) }

    LaunchedEffect(currentRoute, preferences.bedtimeEnabled) {
        val isBedtimeScreen = currentRoute == Screen.Bedtime.route
        val shouldShow = isBedtimeScreen && preferences.bedtimeEnabled

        if (shouldShow) {
            if (!bedtimeSwitchInLayout) {
                bedtimeSwitchInLayout = true
                delay(1200)
            }
            bedtimeSwitchVisible = true
        } else {
            if (bedtimeSwitchVisible) {
                bedtimeSwitchVisible = false
                delay(800)
            }
            if (bedtimeSwitchInLayout) {
                delay(1500)
                bedtimeSwitchInLayout = false
            }
        }
    }

    LaunchedEffect(currentRoute, preferences.gracePeriodEnabled) {
        val isGracePeriodScreen = currentRoute == Screen.GracePeriod.route
        val shouldShow = isGracePeriodScreen && preferences.gracePeriodEnabled

        if (shouldShow) {
            if (!gracePeriodSwitchInLayout) {
                gracePeriodSwitchInLayout = true
                delay(1200)
            }
            gracePeriodSwitchVisible = true
        } else {
            if (gracePeriodSwitchVisible) {
                gracePeriodSwitchVisible = false
                delay(800)
            }
            if (gracePeriodSwitchInLayout) {
                delay(1500)
                gracePeriodSwitchInLayout = false
            }
        }
    }

    var showPermissionSheet by remember { mutableStateOf(false) }
    var showOnboardingStatsSheet by remember { mutableStateOf(false) }
    var showOnboardingUpdateSheet by remember { mutableStateOf(false) }
    var showUserSheet by remember { mutableStateOf(false) }
    var permissionsMissing by remember { mutableStateOf(false) }

    val updateManager = remember { GitHubUpdateManager(context) }
    var latestRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    var showUpdateSheet by remember { mutableStateOf(false) }

    LaunchedEffect(preferences.checkUpdateOnStart) {
        if (com.etrisad.zenith.BuildConfig.SHOW_UPDATES && preferences.checkUpdateOnStart) {
            val result = updateManager.checkForUpdates()
            if (result is GitHubUpdateManager.UpdateResult.NewUpdate) {
                latestRelease = result.release
                showUpdateSheet = true
            }
        }
    }

    fun updatePermissionsBadge() {
        val hasUsage = com.etrisad.zenith.util.hasUsageStatsPermission(context)
        val hasOvl = android.provider.Settings.canDrawOverlays(context)
        val hasNotif = com.etrisad.zenith.util.hasNotificationPermission(context)
        val hasNotifPolicy = (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).isNotificationPolicyAccessGranted
        val hasAccess = com.etrisad.zenith.util.isAccessibilityServiceEnabled(context)
        val allMain = hasUsage && hasOvl && hasNotif && hasNotifPolicy
        val allRequired = if (preferences.accessibilityRequired) allMain && hasAccess else allMain
        permissionsMissing = !allRequired
    }

    val currentCheckPermissions by rememberUpdatedState {
        val hasUsageStats = com.etrisad.zenith.util.hasUsageStatsPermission(context)
        val hasOverlay = android.provider.Settings.canDrawOverlays(context)
        val hasNotifications = com.etrisad.zenith.util.hasNotificationPermission(context)
        val hasNotificationPolicy = (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).isNotificationPolicyAccessGranted
        val hasAccessibility = com.etrisad.zenith.util.isAccessibilityServiceEnabled(context)

        permissionsMissing = !(hasUsageStats && hasOverlay && hasNotifications && hasNotificationPolicy)

        val mainGranted = hasUsageStats && hasOverlay && hasNotifications && hasNotificationPolicy

        if (!mainGranted) {
            showPermissionSheet = true
        } else {
            scope.launch {
                val latestPrefs = userPreferencesRepository.userPreferencesFlow.first()
                val accessibilityRequiredAndMissing = latestPrefs.accessibilityRequired && !hasAccessibility

                if (accessibilityRequiredAndMissing) {
                    showPermissionSheet = true
                } else {
                    val allOnboardingGranted = hasAccessibility || !latestPrefs.accessibilityRequired

                    if (allOnboardingGranted) {
                        if (!latestPrefs.onboardingStatsCompleted) {
                            showOnboardingStatsSheet = true
                        } else {
                            showOnboardingStatsSheet = false
                            if (!latestPrefs.onboardingUpdateCompleted && com.etrisad.zenith.BuildConfig.SHOW_UPDATES) {
                                showOnboardingUpdateSheet = true
                            }
                        }
                    }
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    
    LaunchedEffect(
        lifecycleOwner,
        preferences.accessibilityRequired,
        preferences.onboardingStatsCompleted,
        preferences.onboardingUpdateCompleted,
        preferences.whitelistInitialized
    ) {
        currentCheckPermissions()

        if (!preferences.whitelistInitialized) {
            val hasUsageStats = com.etrisad.zenith.util.hasUsageStatsPermission(context)
            val hasOverlay = android.provider.Settings.canDrawOverlays(context)
            if (hasUsageStats && hasOverlay) {
                userPreferencesRepository.initializeDefaultWhitelist()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updatePermissionsBadge()
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
                scope.launch { userPreferencesRepository.initializeDefaultWhitelist() }
                updatePermissionsBadge()
            },
            onAllPermissionsGranted = {
                showPermissionSheet = false
                scope.launch { userPreferencesRepository.initializeDefaultWhitelist() }
                currentCheckPermissions()
            }
        )
    }

    if (showOnboardingStatsSheet) {
        OnboardingStatsBottomSheet(
            repository = userPreferencesRepository,
            onDismiss = {
                showOnboardingStatsSheet = false
                if (!preferences.onboardingUpdateCompleted && com.etrisad.zenith.BuildConfig.SHOW_UPDATES) {
                    showOnboardingUpdateSheet = true
                }
            }
        )
    }

    if (showOnboardingUpdateSheet) {
        OnboardingUpdateBottomSheet(
            repository = userPreferencesRepository,
            onDismiss = {
                showOnboardingUpdateSheet = false
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        val showNavRail =
            currentRoute != Screen.UsageStats.route &&
                    currentRoute != Screen.Bedtime.route &&
                    currentRoute != Screen.DatabaseDebug.route &&
                    currentRoute != Screen.DataRepairment.route &&
                    currentRoute != Screen.FontTest.route &&
                    currentRoute != Screen.GSFlexCustomizer.route &&
                    currentRoute != Screen.SystemUsageDebug.route &&
                    currentRoute?.startsWith("settings_category") == false &&
                    currentRoute?.startsWith("app_detail") == false

        if (useNavigationRail) {
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
                        val selected = activeTabRoute == screen.route
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
                    categoryName = navBackStackEntry?.arguments?.getString("category"),
                    onBack = {
                        val intercepted = currentRoute?.startsWith("settings_category") == true &&
                            performanceBackInterceptor.value()
                        if (!intercepted) navController.popBackStack()
                    },
                    navigationIcon = {
                        AnimatedContent(
                            targetState = if (currentRoute == Screen.Focus.route) "focus" else "none",
                            transitionSpec = {
                                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                        scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) +
                                        slideInHorizontally(initialOffsetX = { -it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessLow)))
                                    .togetherWith(
                                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                        scaleOut(targetScale = 0.92f) +
                                        slideOutHorizontally(targetOffsetX = { -it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessLow))
                                    )
                            },
                            label = "LeftActionsContent"
                        ) { state ->
                            if (state == "focus") {
                                val isSelected = focusUiState.isSelectionMode
                                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val scale by animateFloatAsState(
                                    targetValue = if (isPressed) 0.92f else 1f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                                    label = "SelectScale"
                                )

                                IconButton(
                                    onClick = { focusViewModel.toggleSelectionMode() },
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .clip(CircleShape)
                                        .scale(scale),
                                    interactionSource = interactionSource
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Outlined.Close else Icons.Outlined.Checklist,
                                        contentDescription = "Select",
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        Box(contentAlignment = Alignment.CenterEnd) {
                            val isSelectionMode = currentRoute == Screen.Focus.route && focusUiState.isSelectionMode
                            val hasSelection = focusUiState.selectedShields.isNotEmpty() || focusUiState.selectedSchedules.isNotEmpty()

                            AnimatedContent(
                                targetState = when {
                                    isSelectionMode -> "selection"
                                    currentRoute?.startsWith("app_detail") == true -> "app_detail"
                                    !isDeepScreen -> "user"
                                    else -> "none"
                                },
                                transitionSpec = {
                                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                            scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) +
                                            slideInHorizontally(initialOffsetX = { it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessLow)))
                                        .togetherWith(
                                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                            scaleOut(targetScale = 0.92f) +
                                            slideOutHorizontally(targetOffsetX = { it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessLow))
                                        )
                                },
                                label = "RightActionsContent"
                            ) { state ->
                                when (state) {
                                    "selection" -> {
                                        Row(
                                            modifier = Modifier.padding(end = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { showBatchPauseSheet = true },
                                                enabled = focusUiState.selectedShields.isNotEmpty(),
                                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.PauseCircle,
                                                    contentDescription = "Pause Selected",
                                                    tint = if (focusUiState.selectedShields.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                                )
                                            }
                                            IconButton(
                                                onClick = { showBatchDeleteSheet = true },
                                                enabled = hasSelection,
                                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Delete,
                                                    contentDescription = "Delete Selected",
                                                    tint = if (hasSelection) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    }
                                    "app_detail" -> {
                                        IconButton(
                                            onClick = { homeViewModel.openSettingsSheet() },
                                            modifier = Modifier.padding(end = 12.dp).clip(CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = "Edit App Settings",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    "user" -> {
                                        IconButton(
                                            onClick = { showUserSheet = true },
                                            modifier = Modifier.padding(end = 12.dp).clip(CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AccountCircle,
                                                contentDescription = "User Profile"
                                            )
                                        }
                                    }
                                }
                            }

                            if (bedtimeSwitchInLayout) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 16.dp)
                                        .width(52.dp)
                                        .height(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = bedtimeSwitchVisible,
                                        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                scaleIn(initialScale = 0.7f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) +
                                                slideInHorizontally(initialOffsetX = { it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                                        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                scaleOut(targetScale = 0.7f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                slideOutHorizontally(targetOffsetX = { it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                    ) {
                                        Switch(
                                            checked = preferences.bedtimeEnabled,
                                            onCheckedChange = {
                                                if (preferences.bedtimeEnabled) {
                                                    showPauseSheet = true
                                                } else {
                                                    bedtimeViewModel.setBedtimeEnabled(true)
                                                }
                                            },
                                            thumbContent = {
                                                val thumbSize by animateDpAsState(
                                                    targetValue = if (preferences.bedtimeEnabled) 28.dp else 24.dp,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    ),
                                                    label = "thumb_size"
                                                )

                                                val iconColor by animateColorAsState(
                                                    targetValue = if (preferences.bedtimeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                                    label = "switch_icon_color"
                                                )

                                                Box(
                                                    modifier = Modifier.size(thumbSize),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    AnimatedContent(
                                                        targetState = preferences.bedtimeEnabled,
                                                        transitionSpec = {
                                                            (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                                    scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow)))
                                                                .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                                        scaleOut(targetScale = 0.5f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                                                        },
                                                        label = "switch_icon_anim"
                                                    ) { isChecked ->
                                                        Icon(
                                                            imageVector = if (isChecked) Icons.Filled.Check else Icons.Filled.Close,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(if (isChecked) 18.dp else 16.dp),
                                                            tint = iconColor
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            if (gracePeriodSwitchInLayout) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 16.dp)
                                        .width(52.dp)
                                        .height(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = gracePeriodSwitchVisible,
                                        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                scaleIn(initialScale = 0.7f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) +
                                                slideInHorizontally(initialOffsetX = { it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                                        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                scaleOut(targetScale = 0.7f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                slideOutHorizontally(targetOffsetX = { it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                    ) {
                                        Switch(
                                            checked = preferences.gracePeriodEnabled,
                                            onCheckedChange = {
                                                gracePeriodViewModel.setGracePeriodEnabled(!preferences.gracePeriodEnabled)
                                            },
                                            thumbContent = {
                                                val thumbSize by animateDpAsState(
                                                    targetValue = if (preferences.gracePeriodEnabled) 28.dp else 24.dp,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    ),
                                                    label = "thumb_size"
                                                )

                                                val iconColor by animateColorAsState(
                                                    targetValue = if (preferences.gracePeriodEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                                    label = "switch_icon_color"
                                                )

                                                Box(
                                                    modifier = Modifier.size(thumbSize),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    AnimatedContent(
                                                        targetState = preferences.gracePeriodEnabled,
                                                        transitionSpec = {
                                                            (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                                    scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow)))
                                                                .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                                        scaleOut(targetScale = 0.5f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                                                        },
                                                        label = "switch_icon_anim"
                                                    ) { isChecked ->
                                                        Icon(
                                                            imageVector = if (isChecked) Icons.Filled.Check else Icons.Filled.Close,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(if (isChecked) 18.dp else 16.dp),
                                                            tint = iconColor
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            if (showBatchDeleteSheet) {
                ConfirmBottomSheet(
                    onDismiss = { showBatchDeleteSheet = false },
                    onConfirm = {
                        focusViewModel.deleteSelected()
                        showBatchDeleteSheet = false
                    },
                    leverCount = 3,
                    showTimeSelection = false
                )
            }
            if (showBatchPauseSheet) {
                ConfirmBottomSheet(
                    onDismiss = { showBatchPauseSheet = false },
                    onConfirm = { hours ->
                        val minutes = if (hours == null) -1 else hours * 60
                        focusViewModel.pauseSelected(minutes)
                        showBatchPauseSheet = false
                    },
                    leverCount = 5,
                    showTimeSelection = true
                )
            }
            if (showPauseSheet) {
                ConfirmBottomSheet(
                    onDismiss = { showPauseSheet = false },
                    onConfirm = { _ ->
                        bedtimeViewModel.setBedtimeEnabled(false)
                        showPauseSheet = false
                    },
                    leverCount = 10,
                    puzzleTimeoutSeconds = 10,
                    showTimeSelection = false
                )
            }
            if (showUserSheet) {
                UserBottomSheet(
                    userName = preferences.userName,
                    currentStreak = homeUiState.globalCurrentStreak,
                    bestStreak = homeUiState.globalBestStreak,
                    repository = userPreferencesRepository,
                    onDismissRequest = { showUserSheet = false }
                )
            }

    if (showUpdateSheet && latestRelease != null) {
        val isDark = when (preferences.themeConfig) {
            ThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
            ThemeConfig.LIGHT -> false
            ThemeConfig.DARK -> true
        }
        UpdateBottomSheet(
            release = latestRelease!!,
            useExpressiveColors = preferences.expressiveColors,
            isDark = isDark,
            onDismiss = { showUpdateSheet = false },
            onUpdate = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(latestRelease!!.htmlUrl))
                context.startActivity(intent)
                showUpdateSheet = false
            }
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
                                    targetRoute == Screen.GracePeriod.route ||
                                    targetRoute == Screen.DatabaseDebug.route ||
                                    targetRoute == Screen.DataRepairment.route ||
                                    targetRoute == Screen.FontTest.route ||
                                    targetRoute == Screen.GSFlexCustomizer.route ||
                                    targetRoute == Screen.SystemUsageDebug.route ||
                                    targetRoute?.startsWith("settings_category") == true ||
                                    targetRoute?.startsWith("app_detail") == true
                        val isInitialDeep =
                            initialRoute == Screen.UsageStats.route ||
                                    initialRoute == Screen.Bedtime.route ||
                                    initialRoute == Screen.GracePeriod.route ||
                                    initialRoute == Screen.DatabaseDebug.route ||
                                    initialRoute == Screen.DataRepairment.route ||
                                    initialRoute == Screen.FontTest.route ||
                                    initialRoute == Screen.GSFlexCustomizer.route ||
                                    initialRoute == Screen.SystemUsageDebug.route ||
                                    initialRoute?.startsWith("settings_category") == true ||
                                    initialRoute?.startsWith("app_detail") == true

                        val animationSpec = spring<IntOffset>(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )

                        if (isTargetDeep) {
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
                                    targetRoute == Screen.GracePeriod.route ||
                                    targetRoute == Screen.DatabaseDebug.route ||
                                    targetRoute == Screen.DataRepairment.route ||
                                    targetRoute == Screen.FontTest.route ||
                                    targetRoute == Screen.GSFlexCustomizer.route ||
                                    targetRoute == Screen.SystemUsageDebug.route ||
                                    targetRoute?.startsWith("settings_category") == true ||
                                    targetRoute?.startsWith("app_detail") == true

                        val isInitialDeep =
                            initialRoute == Screen.UsageStats.route ||
                                    initialRoute == Screen.Bedtime.route ||
                                    initialRoute == Screen.GracePeriod.route ||
                                    initialRoute == Screen.DatabaseDebug.route ||
                                    initialRoute == Screen.DataRepairment.route ||
                                    initialRoute == Screen.FontTest.route ||
                                    initialRoute == Screen.GSFlexCustomizer.route ||
                                    initialRoute == Screen.SystemUsageDebug.route ||
                                    initialRoute?.startsWith("settings_category") == true ||
                                    initialRoute?.startsWith("app_detail") == true

                        val animationSpec = spring<IntOffset>(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        )

                        if (isTargetDeep || (isInitialDeep && !isTargetDeep)) {
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
                        SettingsScreen(
                            preferencesRepository = userPreferencesRepository,
                            innerPadding = innerPadding,
                            navController = navController,
                            onOpenPermissions = { showPermissionSheet = true },
                            permissionsMissing = permissionsMissing
                        )
                    }
                    composable(Screen.Bedtime.route) {
                        BedtimeScreen(bedtimeViewModel, innerPadding)
                    }
                    composable(Screen.GracePeriod.route) {
                        GracePeriodScreen(gracePeriodViewModel, innerPadding)
                    }
                    composable(Screen.UsageStats.route) {
                        UsageStatsScreen(
                            viewModel = homeViewModel,
                            userPreferencesRepository = userPreferencesRepository,
                            innerPadding = innerPadding,
                            showDatabaseIndicator = preferences.showDatabaseIndicator,
                            onAppClick = { packageName ->
                                navController.navigate(Screen.AppDetail.createRoute(packageName))
                            }
                        )
                    }
                    composable(Screen.DatabaseDebug.route) {
                        com.etrisad.zenith.ui.screens.settings.DatabaseDebugScreen(
                            viewModel = homeViewModel,
                            innerPadding = innerPadding,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.DataRepairment.route) {
                        com.etrisad.zenith.ui.screens.settings.DataRepairmentScreen(
                            viewModel = homeViewModel,
                            innerPadding = innerPadding
                        )
                    }
                    composable(Screen.FontTest.route) {
                        com.etrisad.zenith.ui.screens.home.FontTestScreen(
                            onBack = { navController.popBackStack() },
                            innerPadding = innerPadding
                        )
                    }
                    composable(Screen.GSFlexCustomizer.route) {
                        com.etrisad.zenith.ui.screens.settings.GSFlexCustomizerScreen(
                            repository = userPreferencesRepository,
                            onBack = { navController.popBackStack() },
                            innerPadding = innerPadding
                        )
                    }
                    composable(Screen.SystemUsageDebug.route) {
                        com.etrisad.zenith.ui.screens.settings.SystemUsageDebugScreen(
                            viewModel = homeViewModel,
                            innerPadding = innerPadding,
                            onBack = { navController.popBackStack() }
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
                            userPreferencesRepository = userPreferencesRepository,
                            innerPadding = innerPadding
                        )
                    }
                    composable(
                        route = Screen.SettingsCategory.route,
                        arguments = listOf(androidx.navigation.navArgument("category") {
                            type = androidx.navigation.NavType.StringType
                        })
                    ) { backStackEntry ->
                        val category = backStackEntry.arguments?.getString("category") ?: ""
                        com.etrisad.zenith.ui.screens.settings.SettingsCategoryScreen(
                            category = category,
                            preferencesRepository = userPreferencesRepository,
                            navController = navController,
                            innerPadding = innerPadding,
                            onOpenPermissions = { showPermissionSheet = true },
                            onTriggerOnboardingStats = { showOnboardingStatsSheet = true },
                            onTriggerOnboardingUpdate = { showOnboardingUpdateSheet = true },
                            performanceBackInterceptor = performanceBackInterceptor
                        )
                    }
                }

                if (!useNavigationRail) {
                    val showBottomBar =
                    currentRoute != Screen.UsageStats.route &&
                            currentRoute != Screen.Bedtime.route &&
                            currentRoute != Screen.GracePeriod.route &&
                            currentRoute != Screen.DatabaseDebug.route &&
                            currentRoute != Screen.DataRepairment.route &&
                            currentRoute != Screen.FontTest.route &&
                            currentRoute != Screen.GSFlexCustomizer.route &&
                            currentRoute != Screen.SystemUsageDebug.route &&
                            currentRoute?.startsWith("settings_category") == false &&
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
                                            val selected = activeTabRoute == screen.route
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
                                    navItems.forEach { screen ->
                                        val selected = activeTabRoute == screen.route
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