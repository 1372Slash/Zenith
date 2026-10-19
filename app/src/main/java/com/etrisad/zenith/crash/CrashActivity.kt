package com.etrisad.zenith.crash

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.etrisad.zenith.ZenithApplication
import com.etrisad.zenith.data.local.database.DbLogBuffer
import com.etrisad.zenith.util.DiagnosticReportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.cancelBackupLaunch(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val record = CrashStore.latest(this)
        CrashStore.markSeen(this)
        DbLogBuffer.d(
            "ZenithCrash",
            "CRASH_SCREEN_OPENED: ${record?.exceptionClass ?: "no record"}"
        )

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var exporting by remember { mutableStateOf(false) }

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip")
            ) { uri ->
                if (uri != null) {
                    exporting = true
                    scope.launch {
                        val message = exportDiagnostics(context, uri)
                        exporting = false
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }

            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                CrashScreen(
                    record = record,
                    exporting = exporting,
                    onRetry = { SafeMode.restartApp(context) },
                    onSafeMode = {
                        SafeMode.setEnabled(context, true)
                        SafeMode.restartApp(context)
                    },
                    onExit = {
                        (context as? Activity)?.finishAndRemoveTask()
                        SafeMode.killProcess()
                    },
                    onExportZip = { exportLauncher.launch(DiagnosticReportUtils.defaultFileName()) }
                )
            }
        }
    }

    private suspend fun exportDiagnostics(context: android.content.Context, uri: Uri): String {
        return try {
            val preferences = withContext(Dispatchers.IO) {
                (context.applicationContext as? ZenithApplication)
                    ?.userPreferencesRepository
                    ?.userPreferencesFlow
                    ?.first()
            } ?: return "Could not load preferences; report not exported"
            DiagnosticReportUtils.export(
                context = context,
                targetUri = uri,
                preferences = preferences,
                userConfirmed = true
            ).fold(
                onSuccess = { "Diagnostic report saved" },
                onFailure = { "Export failed: ${it.message}" }
            )
        } catch (e: Exception) {
            "Export failed: ${e.message}"
        }
    }
}
