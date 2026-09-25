package com.etrisad.zenith.ui.components.nfc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun normalizeNfcTagId(raw: String): String =
    raw.trim().uppercase().replace(":", "").replace(" ", "").replace("-", "")

fun nfcTagIdToString(tag: Tag): String {
    val id = tag.id ?: return ""
    return buildString {
        for (b in id) {
            append(String.format("%02X", b))
        }
    }
}

fun isNfcSupported(context: Context): Boolean =
    try {
        NfcAdapter.getDefaultAdapter(context) != null
    } catch (_: Exception) {
        false
    }

fun isNfcEnabled(context: Context): Boolean =
    try {
        NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
    } catch (_: Exception) {
        false
    }

fun openNfcSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}

data class NfcScanEvent(
    val tagId: String,
    val timestamp: Long = System.currentTimeMillis()
)

object NfcScanResultBus {
    private val _lastScan = MutableStateFlow<NfcScanEvent?>(null)
    val lastScan: StateFlow<NfcScanEvent?> = _lastScan.asStateFlow()

    fun emit(tagId: String) {
        val normalized = normalizeNfcTagId(tagId)
        if (normalized.isNotEmpty()) {
            _lastScan.value = NfcScanEvent(tagId = normalized)
        }
    }

    fun clear() {
        _lastScan.value = null
    }
}

@Composable
fun EnableNfcReaderMode(
    enabled: Boolean = true,
    onTagDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val currentOnTag by rememberUpdatedState(onTagDetected)
    DisposableEffect(context, enabled) {
        if (!enabled) {
            onDispose { }
            return@DisposableEffect onDispose { }
        }
        val activity = context as? Activity
        val adapter = try {
            NfcAdapter.getDefaultAdapter(context)
        } catch (_: Exception) {
            null
        }
        if (activity == null || adapter == null || !adapter.isEnabled) {
            onDispose { }
            return@DisposableEffect onDispose { }
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }
        try {
            adapter.enableReaderMode(
                activity,
                { tag ->
                    tag?.let {
                        val id = nfcTagIdToString(it)
                        if (id.isNotEmpty()) currentOnTag(id)
                    }
                },
                flags,
                options
            )
        } catch (_: Exception) {}
        onDispose {
            try {
                adapter.disableReaderMode(activity)
            } catch (_: Exception) {}
        }
    }
}
