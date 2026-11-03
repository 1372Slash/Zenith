package com.etrisad.zenith.service.reels

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.etrisad.zenith.data.local.database.OverlayLogBuffer
import com.etrisad.zenith.service.reels.script.Budget
import com.etrisad.zenith.service.reels.script.Interpreter
import com.etrisad.zenith.service.reels.script.Parser
import com.etrisad.zenith.service.reels.script.ScriptError
import com.etrisad.zenith.service.reels.script.Stmt

data class ReelDetection(
    val comparator: String,
    val isInstagramReelOpenedFromDmInbox: Boolean = false
)

internal fun reelDetectionFromScriptResult(
    result: String,
    comparisonResultCleanser: (String) -> String
): ReelDetection {
    val openedFromDmInbox = result.startsWith(ReelAppConfig.INSTAGRAM_DM_REEL_MARKER)
    val comparator = if (openedFromDmInbox) {
        result.removePrefix(ReelAppConfig.INSTAGRAM_DM_REEL_MARKER)
    } else {
        result
    }
    return ReelDetection(
        comparator = comparisonResultCleanser(comparator),
        isInstagramReelOpenedFromDmInbox = openedFromDmInbox
    )
}

class ReelScriptRunner(
    private val service: AccessibilityService
) {

    private var programs: Map<String, List<Stmt>> = emptyMap()
    private var screen: Map<String, Any?> = emptyMap()

    fun setup() {
        val metrics = service.resources.displayMetrics
        screen = mapOf(
            "width" to metrics.widthPixels.toDouble(),
            "height" to metrics.heightPixels.toDouble()
        )
        programs = buildMap {
            for ((packageName, data) in ReelAppConfig.reelData) {
                try {
                    put(packageName, Parser.parse(data.scriptSource))
                } catch (error: ScriptError) {
                    OverlayLogBuffer.w(
                        TAG,
                        "Compile error in reel detector for $packageName: ${error.message}"
                    )
                }
            }
            ReelAppConfig.reelData[INSTAGRAM_LITE_KEY]?.let { data ->
                try {
                    put(
                        ReelAppConfig.INSTAGRAM_LITE_PACKAGE,
                        Parser.parse(data.scriptSource)
                    )
                } catch (error: ScriptError) {
                    OverlayLogBuffer.w(
                        TAG,
                        "Compile error in reel detector for ${ReelAppConfig.INSTAGRAM_LITE_PACKAGE}: ${error.message}"
                    )
                }
            }
        }
    }

    fun detect(event: AccessibilityEvent): ReelDetection? {
        val packageName = event.packageName?.toString() ?: return null
        val data = ReelAppConfig.dataFor(packageName) ?: return null
        val program = programs[packageName] ?: return null
        val root = try {
            service.rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return null
        if (root.packageName?.toString() != packageName) {
            try {
                @Suppress("DEPRECATION")
                root.recycle()
            } catch (_: Exception) {
            }
            return null
        }
        val budget = Budget()
        val runtime = ReelScriptRuntime(
            context = service,
            root = root,
            budget = budget,
            globals = mapOf(
                "app" to packageName,
                "screen" to screen,
                "event" to mapOf(
                    "type" to eventTypeName(event.eventType),
                    "package" to packageName,
                    "text" to event.text.joinToString(" ").takeIf { it.isNotEmpty() },
                    "class" to event.className?.toString(),
                    "fromIndex" to event.fromIndex.toDouble(),
                    "toIndex" to event.toIndex.toDouble(),
                    "itemCount" to event.itemCount.toDouble()
                )
            ),
            scriptId = "reel::$packageName"
        )

        return try {
            val result = Interpreter(runtime, budget).run(program) as? String ?: return null
            reelDetectionFromScriptResult(result, data.comparisonResultCleanser)
        } catch (error: ScriptError) {
            OverlayLogBuffer.w(TAG, "Runtime error in reel detector for $packageName: ${error.message}")
            null
        } catch (_: Exception) {
            null
        } finally {
            runtime.finish()
            try {
                @Suppress("DEPRECATION")
                root.recycle()
            } catch (_: Exception) {
            }
        }
    }

    fun eventTypesFor(packageName: String): Int? =
        ReelAppConfig.dataFor(packageName)?.eventType

    fun pagerClassNameFor(packageName: String): String? =
        ReelAppConfig.dataFor(packageName)?.pagerClassName

    private fun eventTypeName(type: Int): String = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "window_state"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "content"
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> "scrolled"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "clicked"
        AccessibilityEvent.TYPE_VIEW_SELECTED -> "selected"
        else -> "other"
    }

    private companion object {
        const val TAG = "ReelsShield"
        const val INSTAGRAM_LITE_KEY = "com.instagram.lite.positional"
    }
}
