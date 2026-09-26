package com.etrisad.zenith.ui.components.nfc

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.lifecycleScope
import com.etrisad.zenith.ZenithApplication
import com.etrisad.zenith.data.preferences.ThemeConfig
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.theme.ZenithTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NfcScanActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private var expectedIds: List<String> = emptyList()
    private var acceptAny: Boolean = false
    private var reportToBus: Boolean = true

    private var lastScannedId = mutableStateOf<String?>(null)
    private var lastError = mutableStateOf<String?>(null)
    private var successId = mutableStateOf<String?>(null)
    private var kickProgress = mutableStateOf(0f)
    private var kickJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishAndRemoveTask()
                }
            }
        )

        expectedIds = intent.getStringArrayListExtra(EXTRA_EXPECTED_IDS)
            ?.map { normalizeNfcTagId(it) }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        acceptAny = intent.getBooleanExtra(EXTRA_ACCEPT_ANY, false)
        reportToBus = intent.getBooleanExtra(EXTRA_REPORT_TO_BUS, true)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        try {
            nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        } catch (_: Exception) {
            nfcAdapter = null
        }

        setContent {
            val userPreferencesRepository = (application as ZenithApplication).userPreferencesRepository
            val userPreferences by userPreferencesRepository.userPreferencesFlow.collectAsState(
                initial = UserPreferences()
            )
            val darkTheme = when (userPreferences.themeConfig) {
                ThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                ThemeConfig.LIGHT -> false
                ThemeConfig.DARK -> true
            }
            ZenithTheme(
                darkTheme = darkTheme,
                dynamicColor = userPreferences.dynamicColor,
                fontOption = userPreferences.fontOption,
                expressiveColors = userPreferences.expressiveColors,
                gsFlexSettings = userPreferences.gsFlexSettings
            ) {
                val scanned = lastScannedId.value
                val error = lastError.value
                val success = successId.value
                val kickP = kickProgress.value
                val supported = remember { isNfcSupported(this@NfcScanActivity) }
                val enabled = remember(scanned, error, success) { isNfcEnabled(this@NfcScanActivity) }

                LaunchedEffect(success) {
                    if (success != null) {
                        delay(700)
                        finishAndRemoveTask()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        success != null -> MaterialTheme.colorScheme.primaryContainer
                                        error != null -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    success != null -> Icons.Outlined.CheckCircle
                                    error != null -> Icons.Outlined.ErrorOutline
                                    else -> Icons.Outlined.Nfc
                                },
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = when {
                                    success != null -> MaterialTheme.colorScheme.onPrimaryContainer
                                    error != null -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = when {
                                success != null -> "NFC tag accepted!"
                                !supported -> "NFC not supported"
                                !enabled -> "NFC is turned off"
                                else -> "Hold NFC tag near your phone"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                success != null -> "Tag ID: $success"
                                !supported -> "This device has no NFC hardware."
                                !enabled -> "Turn on NFC in system settings, then come back here."
                                error != null -> error ?: ""
                                else -> if (acceptAny || expectedIds.isEmpty()) {
                                    "Any NFC tag will do."
                                } else {
                                    "Tap one of your ${expectedIds.size} registered tag(s)."
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (error != null && success == null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center
                        )
                        if (scanned != null && success == null && error == null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Last read: $scanned",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        if (!supported || !enabled) {
                            ZenithButton(
                                onClick = { openNfcSettings(this@NfcScanActivity) },
                                text = "Open NFC Settings",
                                icon = Icons.Outlined.Nfc,
                                type = ZenithButtonType.Filled,
                                size = ZenithButtonSize.Large,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        ZenithButton(
                            onClick = { finishAndRemoveTask() },
                            text = "Cancel",
                            type = ZenithButtonType.Tonal,
                            size = ZenithButtonSize.Large,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        ZenithButton(
                            onClick = { kickToLauncher() },
                            text = "Close App",
                            type = ZenithButtonType.Text,
                            contentColor = MaterialTheme.colorScheme.error,
                            backgroundProgressProvider = { kickP },
                            fillMaxWidth = true,
                            size = ZenithButtonSize.ExtraLarge
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
            val options = Bundle().apply {
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
            }
            nfcAdapter?.enableReaderMode(this, this, flags, options)
        } catch (_: Exception) {}
        restartKickTimer()
    }

    override fun onPause() {
        cancelKickTimer()
        try {
            nfcAdapter?.disableReaderMode(this)
        } catch (_: Exception) {}
        super.onPause()
    }
    private fun restartKickTimer() {
        kickJob?.cancel()
        kickProgress.value = 0f
        kickJob = lifecycleScope.launch {
            delay(KICK_GRACE_MS)
            val startTime = System.currentTimeMillis()
            while (true) {
                val p = ((System.currentTimeMillis() - startTime) / (KICK_COUNTDOWN_S * 1000f)).coerceIn(0f, 1f)
                kickProgress.value = p
                if (p >= 1f) break
                delay(50)
            }
            kickToLauncher()
        }
    }

    private fun cancelKickTimer() {
        kickJob?.cancel()
        kickJob = null
        kickProgress.value = 0f
    }

    private fun kickToLauncher() {
        try {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(home)
        } catch (_: Exception) {}
        finishAndRemoveTask()
    }

    override fun onTagDiscovered(tag: Tag?) {
        val id = tag?.let { nfcTagIdToString(it) } ?: return
        val normalized = normalizeNfcTagId(id)
        if (normalized.isEmpty()) return
        runOnUiThread {
            // Any tag tap counts as activity, restart the kick timer.
            restartKickTimer()
            lastScannedId.value = normalized
            val matches = acceptAny || expectedIds.isEmpty() || normalized in expectedIds
            if (matches) {
                lastError.value = null
                successId.value = normalized
                if (reportToBus) {
                    NfcScanResultBus.emit(normalized)
                } else {
                    val data = Intent().putExtra(RESULT_TAG_ID, normalized)
                    setResult(RESULT_OK, data)
                }
            } else {
                lastError.value = "Wrong tag ($normalized), use one of your registered tags"
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val EXTRA_EXPECTED_IDS = "extra_expected_ids"
        const val EXTRA_ACCEPT_ANY = "extra_accept_any"
        const val EXTRA_REPORT_TO_BUS = "extra_report_to_bus"
        const val RESULT_TAG_ID = "result_tag_id"
        private const val KICK_GRACE_MS = 5000L
        private const val KICK_COUNTDOWN_S = 5

        fun start(
            context: Context,
            expectedTagIds: List<String> = emptyList(),
            acceptAny: Boolean = false,
            reportToBus: Boolean = true
        ) {
            val intent = Intent(context, NfcScanActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_EXPECTED_IDS, ArrayList(expectedTagIds))
                putExtra(EXTRA_ACCEPT_ANY, acceptAny)
                putExtra(EXTRA_REPORT_TO_BUS, reportToBus)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }
}
