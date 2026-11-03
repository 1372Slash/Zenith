package com.etrisad.zenith.ui.screens.settings.redirectshield

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.focus.MultiAppPickerBottomSheet
import com.etrisad.zenith.ui.viewmodel.AppInfo
import com.etrisad.zenith.ui.viewmodel.FocusUiState
import com.etrisad.zenith.ui.viewmodel.PickerTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RedirectShieldAppRole(val title: String) {
    SHIELDED("Shielded Apps"),
    WHITELISTED("Whitelisted Apps"),
    TRIGGER("Trigger Apps");

    fun selectedIn(preferences: UserPreferences): Set<String> = when (this) {
        SHIELDED -> preferences.redirectShieldApps
        WHITELISTED -> preferences.redirectShieldWhitelistedApps
        TRIGGER -> preferences.redirectShieldTriggerApps
    }

    fun conflictingIn(preferences: UserPreferences): Set<String> = when (this) {
        SHIELDED -> preferences.redirectShieldWhitelistedApps
        WHITELISTED -> preferences.redirectShieldApps
        TRIGGER -> emptySet()
    }
}

@Composable
fun RedirectShieldAppPickerSheet(
    role: RedirectShieldAppRole,
    preferencesRepository: UserPreferencesRepository,
    onDismiss: () -> Unit
) {
    val preferences by preferencesRepository.userPreferencesFlow.collectAsState(initial = null)
    val snapshot = preferences
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    var query by remember(role) { mutableStateOf("") }
    var selection by remember(role, snapshot != null) {
        mutableStateOf(snapshot?.let { role.selectedIn(it) } ?: emptySet())
    }
    var otherSelection by remember(role, snapshot != null) {
        mutableStateOf(snapshot?.let { role.conflictingIn(it) } ?: emptySet())
    }

    val visibleApps = remember(apps, query) {
        val search = query.trim()
        if (search.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.appName.contains(search, ignoreCase = true) ||
                    it.packageName.contains(search, ignoreCase = true)
            }
        }
    }

    val uiState = remember(visibleApps, query, selection) {
        FocusUiState(
            installedApps = visibleApps,
            topApps = emptyList(),
            searchQuery = query,
            selectedAppsForSchedule = selection,
            pickerTab = PickerTab.APPS
        )
    }

    fun persist(next: Set<String>) {
        if (next == selection) return
        val added = next - selection
        selection = next
        scope.launch {
            when (role) {
                RedirectShieldAppRole.SHIELDED -> {
                    preferencesRepository.setRedirectShieldApps(next)
                    val cleaned = otherSelection - added
                    if (cleaned != otherSelection) {
                        otherSelection = cleaned
                        preferencesRepository.setRedirectShieldWhitelistedApps(cleaned)
                    }
                }

                RedirectShieldAppRole.WHITELISTED -> {
                    preferencesRepository.setRedirectShieldWhitelistedApps(next)
                    val cleaned = otherSelection - added
                    if (cleaned != otherSelection) {
                        otherSelection = cleaned
                        preferencesRepository.setRedirectShieldApps(cleaned)
                    }
                }

                RedirectShieldAppRole.TRIGGER -> {
                    preferencesRepository.setRedirectShieldTriggerApps(next)
                }
            }
        }
    }

    MultiAppPickerBottomSheet(
        uiState = uiState,
        onDismiss = onDismiss,
        onAppToggled = { packageName ->
            persist(
                if (packageName in selection) {
                    selection - packageName
                } else {
                    selection + packageName
                }
            )
        },
        onConfirm = onDismiss,
        onSearchQueryChange = { query = it },
        showTabs = false,
        title = role.title
    )
}

private fun loadLaunchableApps(context: Context): List<AppInfo> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val resolved = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    } catch (_: Exception) {
        emptyList()
    }

    return resolved.mapNotNull { resolveInfo ->
        val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
        val label = runCatching { resolveInfo.loadLabel(packageManager).toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: packageName
        AppInfo(packageName = packageName, appName = label)
    }
        .distinctBy { it.packageName }
        .sortedWith(
            compareBy<AppInfo> { it.appName.lowercase() }
                .thenBy { it.packageName }
        )
}
