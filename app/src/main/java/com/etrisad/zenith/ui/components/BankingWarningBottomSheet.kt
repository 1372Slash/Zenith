package com.etrisad.zenith.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.service.SharedMonitoringState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankingWarningBottomSheet(
    onDismiss: () -> Unit,
    onProceed: () -> Unit,
    onDontShowAgain: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var understandChecked by remember { mutableStateOf(false) }
    var dontShowAgainChecked by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val installedBankingApps = remember {
        val allInstalled = context.packageManager.getInstalledApplications(0)
            .map { it.packageName }
            .toSet()
        SharedMonitoringState.FINANCIAL_APPS
            .filter { it in allInstalled }
            .mapNotNull { pkg ->
                try {
                    val info = context.packageManager.getApplicationInfo(pkg, 0)
                    val label = context.packageManager.getApplicationLabel(info).toString()
                    Pair(pkg, label)
                } catch (_: Exception) {
                    null
                }
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Before You Continue",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Zenith is safe. ") }
                    append("All data is ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("processed entirely on your device") }
                    append(" and ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("never leaves it") }
                    append(". The accessibility service is ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("only used to detect which app is currently open") }
                    append(" nothing else.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Some banking apps block access when a third-party accessibility service is detected. This notice helps you avoid being locked out.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "What you need to do",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "1.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = buildAnnotatedString {
                                append("After enabling Zenith, ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("disable it before opening any mobile banking app") }
                                append(".")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "2.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = buildAnnotatedString {
                                append("If a banking app ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("blocks your access") }
                                append(" or shows a security warning, go to ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Settings > Accessibility > Zenith") }
                                append(" and turn it off.")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "3.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = buildAnnotatedString {
                                append("You can safely ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("re-enable Zenith afterwards") }
                                append(" no data was collected or shared during the time it was off.")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Why does this happen?") }
                            append(" Some banking apps detect third-party accessibility services as a security measure. This is unrelated to Zenith. The same block happens with any accessibility service.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            if (installedBankingApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Banking apps detected on your device:") }
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        installedBankingApps.forEach { (pkg, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = pkg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val surfaceColor by animateColorAsState(
                targetValue = if (showError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
                label = "errorBg"
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = surfaceColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = understandChecked,
                        onCheckedChange = {
                            understandChecked = it
                            showError = false
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("I have read and understand the above. I accept the risks of using Zenith alongside my banking apps.")
                            append(" ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)) { append("*") }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            AnimatedVisibility(
                visible = showError,
                enter = fadeIn(animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f)) + slideInVertically { it / 2 },
                exit = fadeOut(animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f)) + slideOutVertically { it / 2 }
            ) {
                Text(
                    text = "Please check this box before proceeding",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = dontShowAgainChecked,
                    onCheckedChange = { dontShowAgainChecked = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Don't show this again",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ZenithGroupedButton(size = ZenithButtonSize.Large) {
                ZenithButtonWeighted(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    text = "Back",
                    type = ZenithButtonType.Outlined,
                    isLast = false,
                    size = ZenithButtonSize.ExtraLarge
                )
                ZenithButtonWeighted(
                    onClick = {
                        showError = false
                        if (dontShowAgainChecked) {
                            onDontShowAgain()
                        }
                        onProceed()
                    },
                    text = "Proceed",
                    type = ZenithButtonType.Filled,
                    isFirst = false,
                    size = ZenithButtonSize.ExtraLarge,
                    enabled = understandChecked,
                    onDisabledClick = { showError = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
