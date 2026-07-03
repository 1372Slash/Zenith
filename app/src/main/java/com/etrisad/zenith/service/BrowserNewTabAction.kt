package com.etrisad.zenith.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.etrisad.zenith.data.website.NEW_TAB_BUTTON_ID_LIST
import com.etrisad.zenith.data.website.WebsiteRepository

object BrowserNewTabAction {

    private const val TAG = "BrowserNewTabAction"

    fun performNewTabClick(service: AccessibilityService, browserPackage: String): Boolean {
        if (!WebsiteRepository.isKnownBrowser(browserPackage)) {
            Log.d(TAG, "Unknown browser: $browserPackage")
            return false
        }

        val info = NEW_TAB_BUTTON_ID_LIST[browserPackage]
        if (info == null) {
            Log.d(TAG, "No new tab button info for: $browserPackage")
            return false
        }

        try {
            val root = service.rootInActiveWindow ?: run {
                Log.d(TAG, "No root node in active window")
                return false
            }

            if (root.packageName != browserPackage) {
                Log.d(TAG, "Active window is not the browser: ${root.packageName}")
                root.recycle()
                return false
            }
            if (info.tabSwitcherId != null) {
                val tabSwitcherNodes = try {
                    root.findAccessibilityNodeInfosByViewId(info.tabSwitcherId)
                } catch (_: Exception) {
                    null
                }
                if (!tabSwitcherNodes.isNullOrEmpty()) {
                    val node = tabSwitcherNodes[0]
                    if (node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        node.recycle()
                        root.recycle()
                        Log.d(TAG, "Clicked tab switcher button for $browserPackage")
                        return true
                    }
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i) ?: continue
                        if (child.isClickable) {
                            child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            child.recycle()
                            root.recycle()
                            Log.d(TAG, "Clicked child of tab switcher for $browserPackage")
                            return true
                        }
                        child.recycle()
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle()
                    root.recycle()
                    Log.d(TAG, "Clicked tab switcher (non-clickable fallback) for $browserPackage")
                    return true
                }
                tabSwitcherNodes?.forEach { it.recycle() }
            }
            if (info.newTabButtonId != null) {
                val newTabNodes = try {
                    root.findAccessibilityNodeInfosByViewId(info.newTabButtonId)
                } catch (_: Exception) {
                    null
                }
                if (!newTabNodes.isNullOrEmpty()) {
                    newTabNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    newTabNodes.forEach { it.recycle() }
                    root.recycle()
                    Log.d(TAG, "Clicked new tab button for $browserPackage")
                    return true
                }
                newTabNodes?.forEach { it.recycle() }
            }
            for (desc in info.fallbackContentDescriptions) {
                val nodes = try {
                    root.findAccessibilityNodeInfosByText(desc)
                } catch (_: Exception) {
                    null
                }
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        val contentDesc = node.contentDescription?.toString() ?: ""
                        val nodeText = node.text?.toString() ?: ""
                        if (contentDesc.contains(desc, ignoreCase = true) || nodeText.contains(desc, ignoreCase = true)) {
                            if (node.isClickable) {
                                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                nodes.forEach { it.recycle() }
                                root.recycle()
                                Log.d(TAG, "Clicked by content description '$desc' for $browserPackage")
                                return true
                            }
                            for (i in 0 until node.childCount) {
                                val child = node.getChild(i) ?: continue
                                if (child.isClickable) {
                                    child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    child.recycle()
                                    nodes.forEach { it.recycle() }
                                    root.recycle()
                                    Log.d(TAG, "Clicked child by text '$desc' for $browserPackage")
                                    return true
                                }
                                child.recycle()
                            }
                        }
                    }
                }
                nodes?.forEach { it.recycle() }
            }

            root.recycle()
            Log.d(TAG, "Could not find any new tab button for $browserPackage")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error performing new tab click for $browserPackage: ${e.message}")
            return false
        }
    }
}
