package com.etrisad.zenith.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AppRegistration
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.FilterCenterFocus
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.service.AppGoalOverlayActivity
import com.etrisad.zenith.ui.components.focus.AppPickerBottomSheet
import com.etrisad.zenith.ui.viewmodel.FocusViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGoalTestBottomSheet(
    focusViewModel: FocusViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { UserPreferencesRepository(context) }
    val preferences by repository.userPreferencesFlow.collectAsState(initial = UserPreferences())
    val focusUiState by focusViewModel.uiState.collectAsState()
    
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAppPicker by remember { mutableStateOf(false) }

    val containerColor by animateColorAsState(
        targetValue = if (preferences.expressiveColors) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Debug Goal Overlay",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Select a scenario to test the full screen goal caller UI.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            val options = listOf(
                Triple("Test with Zenith", "Single app scenario using the current app", Icons.Outlined.FilterCenterFocus) to {
                    scope.launch {
                        try {
                            sheetState.hide()
                        } catch (_: Exception) {
                        }
                        AppGoalOverlayActivity.start(context, listOf(context.packageName))
                        onDismiss()
                    }
                    Unit
                },
                Triple("Test with selected app", "Pick an app to test the overlay", Icons.Outlined.AppRegistration) to {
                    showAppPicker = true
                },
                Triple("Test more than 2 Apps", "Multiple apps scenario (Zenith, Settings, and Phone)", Icons.Outlined.Apps) to {
                    scope.launch {
                        try {
                            sheetState.hide()
                        } catch (_: Exception) {
                        }
                        AppGoalOverlayActivity.start(
                            context, 
                            listOf(context.packageName, "com.android.settings", "com.android.phone")
                        )
                        onDismiss()
                    }
                    Unit
                },
                Triple("Test with random app", "Picks a random installed launcher app", Icons.Outlined.BugReport) to {
                    scope.launch {
                        val randomPackage = getRandomInstalledApp(context)
                        try {
                            sheetState.hide()
                        } catch (_: Exception) {
                        }
                        AppGoalOverlayActivity.start(context, listOf(randomPackage))
                        onDismiss()
                    }
                    Unit
                }
            )

            options.forEachIndexed { index, (info, action) ->
                val shape = when {
                    options.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                    index == options.size - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    else -> RoundedCornerShape(4.dp)
                }

                TestOptionItem(
                    title = info.first,
                    description = info.second,
                    icon = info.third,
                    shape = shape,
                    containerColor = containerColor,
                    onClick = action
                )
                
                if (index < options.size - 1) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerBottomSheet(
            uiState = focusUiState,
            title = "Select App to Test",
            onDismiss = { showAppPicker = false },
            onAppSelected = { app ->
                showAppPicker = false
                AppGoalOverlayActivity.start(context, listOf(app.packageName))
                onDismiss()
            },
            onSearchQueryChange = { focusViewModel.onSearchQueryChange(it) }
        )
    }
}

@Composable
private fun TestOptionItem(
    title: String,
    description: String,
    icon: ImageVector,
    shape: androidx.compose.ui.graphics.Shape,
    containerColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun getRandomInstalledApp(context: Context): String {
    val pm = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null)
    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
    val resolvedInfos = pm.queryIntentActivities(mainIntent, 0)
    return if (resolvedInfos.isNotEmpty()) {
        resolvedInfos.random().activityInfo.packageName
    } else {
        context.packageName
    }
}
