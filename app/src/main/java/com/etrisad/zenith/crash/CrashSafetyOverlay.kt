package com.etrisad.zenith.crash

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * App-wide crash affordances drawn on top of the main UI:
 *  - a small pill at the bottom while a crash report has not been looked at,
 *    tapping it reopens [CrashActivity];
 *  - a banner while Safe Mode is active, offering one tap back to normal mode.
 */
@Composable
fun CrashSafetyOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasUnseenCrash by remember { mutableStateOf(CrashStore.hasUnseen(context)) }
    var safeModeActive by remember { mutableStateOf(SafeMode.isEnabled(context)) }
    var safeBannerDismissed by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUnseenCrash = CrashStore.hasUnseen(context)
                safeModeActive = SafeMode.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (safeModeActive && !safeBannerDismissed) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SAFE MODE",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Your saved data is not loaded",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(
                        onClick = {
                            SafeMode.setEnabled(context, false)
                            SafeMode.restartApp(context)
                        }
                    ) {
                        Text("Restart", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    IconButton(onClick = { safeBannerDismissed = true }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = hasUnseenCrash,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 128.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Surface(
                shape = RoundedCornerShape(100),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 6.dp,
                modifier = Modifier.clickable {
                    hasUnseenCrash = false
                    context.startActivity(
                        Intent(context, CrashActivity::class.java).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                    )
                }
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "App crashed - view report",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            CrashStore.markSeen(context)
                            hasUnseenCrash = false
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
