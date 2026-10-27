package com.etrisad.zenith.ui.screens.settings

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.etrisad.zenith.admin.TamperPin
import com.etrisad.zenith.admin.ZenithDeviceAdminReceiver
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import kotlinx.coroutines.launch

private enum class TamperPinMode { SETUP, VERIFY }

private enum class TamperAction { ENABLE, DISABLE, CHANGE_PIN }
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TamperProtectionSettings(
    preferences: UserPreferences,
    innerPadding: PaddingValues,
    preferencesRepository: UserPreferencesRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dpm = remember { context.getSystemService(DevicePolicyManager::class.java) }
    val adminComponent = remember { ZenithDeviceAdminReceiver.componentName(context) }
    var adminActive by remember { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(false) }
    var pinSheetMode by remember { mutableStateOf<TamperPinMode?>(null) }
    var pendingAction by remember { mutableStateOf<TamperAction?>(null) }

    val adminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        adminActive = dpm.isAdminActive(adminComponent)
        if (adminActive) {
            coroutineScope.launch { preferencesRepository.setTamperProtectionEnabled(true) }
        }
    }

    fun requestAdminActivation() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Zenith needs device administrator status so it cannot be uninstalled or silenced without your PIN."
            )
        }
        adminLauncher.launch(intent)
    }

    fun refreshAdminState() {
        adminActive = dpm.isAdminActive(adminComponent)
    }
    fun openSystemAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                Toast.makeText(context, "Could not open Android settings", Toast.LENGTH_SHORT).show()
            }
    }

    LaunchedEffect(Unit) {
        adminActive = dpm.isAdminActive(adminComponent)
        hasPin = preferencesRepository.hasTamperPin()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshAdminState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val protectionActive = preferences.tamperProtectionEnabled && adminActive
    val adminRevoked = preferences.tamperProtectionEnabled && !adminActive

    fun onEnableClick() {
        if (!hasPin) {
            pendingAction = TamperAction.ENABLE
            pinSheetMode = TamperPinMode.SETUP
        } else {
            requestAdminActivation()
        }
    }

    fun onTurnOffClick() {
        if (preferences.tamperLockToLockdown && preferences.isInLockdown()) {
            Toast.makeText(
                context,
                "Protection can only be turned off outside Lockdown hours",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            pendingAction = TamperAction.DISABLE
            pinSheetMode = TamperPinMode.VERIFY
        }
    }

    fun onChangePinClick() {
        if (protectionActive) {
            pendingAction = TamperAction.CHANGE_PIN
            pinSheetMode = TamperPinMode.VERIFY
        } else {
            pendingAction = null
            pinSheetMode = TamperPinMode.SETUP
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TamperStatusCard(
                active = protectionActive,
                revoked = adminRevoked
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "How it works",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Unlike Lockdown, this works at the Android level: Android asks you " +
                            "to deactivate Zenith before it can be uninstalled, and Zenith warns " +
                            "you here if those rights are removed. Your PIN is required to turn " +
                            "protection off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Zenith cannot block a factory reset or a change made in Android " +
                            "settings, it can only warn you afterwards.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "If something goes wrong",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Forgotten PIN or a stuck screen? Open Zenith in Android settings " +
                            "and switch off the device administrator there. Zenith will notice, " +
                            "and you can turn protection off and pick a new PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = preferences.tamperProtectionEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingsToggle(
                        title = "Only outside Lockdown hours",
                        description = "Optional. When on, protection can only be turned off outside your Lockdown window",
                        checked = preferences.tamperLockToLockdown,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferencesRepository.setTamperLockToLockdown(enabled)
                            }
                        },
                        icon = Icons.Outlined.Lock
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when {
                adminRevoked -> Column(modifier = Modifier.fillMaxWidth()) {
                    ZenithButton(
                        onClick = { requestAdminActivation() },
                        text = "Re-activate Device Administrator",
                        icon = Icons.Outlined.AdminPanelSettings,
                        type = ZenithButtonType.Filled,
                        size = ZenithButtonSize.Large,
                        fillMaxWidth = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ZenithButton(
                        onClick = {
                            coroutineScope.launch {
                                preferencesRepository.setTamperProtectionEnabled(false)
                            }
                            Toast.makeText(context, "Protection turned off", Toast.LENGTH_SHORT).show()
                        },
                        text = "Turn Off Protection",
                        type = ZenithButtonType.Text,
                        size = ZenithButtonSize.Large,
                        contentColor = MaterialTheme.colorScheme.error,
                        fillMaxWidth = true
                    )
                }

                !protectionActive -> ZenithButton(
                    onClick = { onEnableClick() },
                    text = "Enable Tamper Protection",
                    icon = Icons.Outlined.AdminPanelSettings,
                    type = ZenithButtonType.Filled,
                    size = ZenithButtonSize.Large,
                    fillMaxWidth = true
                )

                else -> ZenithButton(
                    onClick = { onTurnOffClick() },
                    text = "Turn Off Protection",
                    icon = Icons.Outlined.Lock,
                    type = ZenithButtonType.Filled,
                    size = ZenithButtonSize.Large,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    fillMaxWidth = true
                )
            }

            if (hasPin) {
                Spacer(modifier = Modifier.height(12.dp))
                ZenithButton(
                    onClick = { onChangePinClick() },
                    text = "Change PIN",
                    type = ZenithButtonType.Text,
                    size = ZenithButtonSize.Large,
                    fillMaxWidth = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            ZenithButton(
                onClick = { openSystemAppSettings() },
                text = "Open Zenith in Android Settings",
                type = ZenithButtonType.Text,
                size = ZenithButtonSize.Large,
                fillMaxWidth = true
            )
        }
    }

    pinSheetMode?.let { mode ->
        TamperPinSheet(
            mode = mode,
            onDismiss = {
                pinSheetMode = null
                pendingAction = null
            },
            onSubmit = { pin ->
                when (mode) {
                    TamperPinMode.SETUP -> {
                        preferencesRepository.setTamperPin(pin)
                        hasPin = true
                        val action = pendingAction
                        pinSheetMode = null
                        pendingAction = null
                        if (action == TamperAction.ENABLE) requestAdminActivation()
                        null
                    }

                    TamperPinMode.VERIFY -> {
                        if (!preferencesRepository.verifyTamperPin(pin)) {
                            "Incorrect PIN"
                        } else when (pendingAction) {
                            TamperAction.DISABLE -> {
                                if (adminActive) dpm.removeActiveAdmin(adminComponent)
                                refreshAdminState()
                                preferencesRepository.setTamperProtectionEnabled(false)
                                pinSheetMode = null
                                pendingAction = null
                                null
                            }

                            TamperAction.CHANGE_PIN -> {
                                pendingAction = null
                                pinSheetMode = TamperPinMode.SETUP
                                null
                            }

                            else -> {
                                pinSheetMode = null
                                pendingAction = null
                                null
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun TamperStatusCard(active: Boolean, revoked: Boolean) {
    val targetColor = when {
        active -> MaterialTheme.colorScheme.primary
        revoked -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusColor by animateColorAsState(targetValue = targetColor, label = "statusColor")
    val chipColor = when {
        active -> MaterialTheme.colorScheme.primaryContainer
        revoked -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val title = when {
        active -> "Protection active"
        revoked -> "Administrator rights were revoked"
        else -> "Protection is off"
    }
    val description = when {
        active -> "Android asks you to deactivate Zenith before it can be uninstalled."

        revoked -> "Zenith was deactivated outside the app. Re-activate to restore protection."

        else -> "Zenith can be uninstalled like any other app."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(chipColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (active || revoked) Icons.Outlined.AdminPanelSettings else Icons.Outlined.Info,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TamperPinSheet(
    mode: TamperPinMode,
    onDismiss: () -> Unit,
    onSubmit: suspend (String) -> String?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(mode) {
        pin = ""
        confirm = ""
        error = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (mode == TamperPinMode.SETUP) "Set your protection PIN" else "Enter your PIN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (mode == TamperPinMode.SETUP) {
                    "You will need this PIN to turn protection off."
                } else {
                    "Enter your PIN to continue."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { value ->
                    pin = value.filter { it.isDigit() }.take(8)
                    error = null
                },
                label = { Text("PIN") },
                placeholder = { Text("4-8 digits") },
                singleLine = true,
                isError = error != null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (mode == TamperPinMode.SETUP) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { value ->
                        confirm = value.filter { it.isDigit() }.take(8)
                        error = null
                    },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    isError = error != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            error?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            ZenithButton(
                onClick = {
                    scope.launch {
                        busy = true
                        val localError = when {
                            !TamperPin.isValid(pin) -> "PIN must be 4-8 digits"
                            mode == TamperPinMode.SETUP && pin != confirm -> "PINs do not match"
                            else -> null
                        }
                        val submitError = localError ?: onSubmit(pin)
                        if (submitError != null) error = submitError
                        busy = false
                    }
                },
                text = if (mode == TamperPinMode.SETUP) "Save PIN" else "Confirm",
                type = ZenithButtonType.Filled,
                size = ZenithButtonSize.Large,
                fillMaxWidth = true,
                enabled = !busy,
                isLoading = busy
            )
        }
    }
}
