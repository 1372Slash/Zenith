package com.etrisad.zenith.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.etrisad.zenith.data.website.NEW_TAB_BUTTON_ID_LIST
import com.etrisad.zenith.data.website.WebsiteRepository

object BrowserNewTabAction {

    private const val TAG = "BrowserNewTabAction"

    fun performNewTabClick(service: AccessibilityService, browserPackage: String): Boolean {
        if (!WebsiteRepository.isKnownBrowser(browserPackage)) {
            return false
        }

        val info = NEW_TAB_BUTTON_ID_LIST[browserPackage]
        if (info == null) {
            return false
        }

        try {
            val root = service.rootInActiveWindow ?: run {
                return false
            }

            if (root.packageName != browserPackage) {
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
                        return true
                    }
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i) ?: continue
                        if (child.isClickable) {
                            child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            child.recycle()
                            root.recycle()
                            return true
                        }
                        child.recycle()
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle()
                    root.recycle()
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
                                return true
                            }
                            for (i in 0 until node.childCount) {
                                val child = node.getChild(i) ?: continue
                                if (child.isClickable) {
                                    child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    child.recycle()
                                    nodes.forEach { it.recycle() }
                                    root.recycle()
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
            return false
        } catch (e: Exception) {
            return false
        }
    }
}
