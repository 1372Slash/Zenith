package com.etrisad.zenith.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.etrisad.zenith.data.website.WebsiteRepository
import com.etrisad.zenith.data.website.URL_BAR_ID_LIST
import java.util.ArrayDeque

class WebsiteUrlTracker(
    private val service: AccessibilityService,
) {
    fun getUrlBarViewIds(packageName: String): List<String> {
        val known = URL_BAR_ID_LIST[packageName]
        if (known != null) return listOf(known.displayUrlBarId)

        val fallbackIds = listOf(
            "$packageName:id/url_bar",
            "$packageName:id/url_field",
            "$packageName:id/url",
            "$packageName:id/address_bar",
            "$packageName:id/location_bar",
            "$packageName:id/search_box",
            "$packageName:id/search_box_text",
            "$packageName:id/omnibox"
        )
        val specificIds = when (packageName) {
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary", "org.chromium.chrome",
            "com.android.chrome.beta" -> listOf(
                "$packageName:id/url_bar",
                "$packageName:id/search_box_text"
            )
            "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta" -> listOf(
                "$packageName:id/url_bar",
                "$packageName:id/location_bar_edit_text"
            )
            "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus",
            "org.mozilla.fennec_fdroid", "org.ironfoxoss.ironfox", "io.github.forkmaintainers.iceraven" -> listOf(
                "$packageName:id/url_bar_title",
                "$packageName:id/mozac_browser_toolbar_url_view",
                "$packageName:id/toolbar_edit_text",
                "ADDRESSBAR_URL_BOX"
            )
            "com.microsoft.emmx", "com.microsoft.emmx.beta", "com.microsoft.edge.canary", "com.microsoft.edge.beta" -> listOf(
                "$packageName:id/url_bar",
                "$packageName:id/search_box_text",
                "$packageName:id/url_text",
                "$packageName:id/address_bar",
                "$packageName:id/omnibox_text",
                "$packageName:id/search_bar_text"
            )
            "com.brave.browser" -> listOf(
                "$packageName:id/url_bar",
                "$packageName:id/search_box_text"
            )
            "com.opera.browser", "com.opera.mini.native", "com.opera.browser.beta" -> listOf(
                "$packageName:id/url_field"
            )
            "com.duckduckgo.mobile.android" -> listOf(
                "$packageName:id/omniboxTextInput",
                "$packageName:id/search_edit_text",
                "$packageName:id/omnibox_text"
            )
            "com.vivaldi.browser" -> listOf(
                "$packageName:id/url_bar"
            )
            "com.kiwibrowser.browser" -> listOf(
                "$packageName:id/url_bar"
            )
            "mark.via.gp" -> listOf(
                "$packageName:id/url_bar",
                "$packageName:id/location_bar"
            )
            "org.cromite.cromite" -> listOf(
                "$packageName:id/url_bar"
            )
            "app.vanadium.browser" -> listOf(
                "$packageName:id/url_bar"
            )
            else -> fallbackIds
        }
        return specificIds
    }

    fun findUrlByViewIds(rootNode: AccessibilityNodeInfo, packageName: String): String? {
        val viewIds = getUrlBarViewIds(packageName)
        for (id in viewIds) {
            val nodes = try {
                rootNode.findAccessibilityNodeInfosByViewId(id)
            } catch (_: Exception) {
                null
            } ?: continue

            var foundDomain: String? = null
            for (node in nodes) {
                if (foundDomain == null) {
                    val text = node.text?.toString()
                    if (!text.isNullOrBlank()) {
                        foundDomain = extractUrlDomain(text)
                    }
                    if (foundDomain == null) {
                        val desc = node.contentDescription?.toString()
                        if (!desc.isNullOrBlank()) {
                            foundDomain = extractUrlDomain(desc)
                        }
                    }
                }
                node.recycle()
            }
            if (foundDomain != null) return foundDomain
        }
        return null
    }

    fun extractUrlFromAccessibilityNode(browserPackage: String? = null): String? {
        try {
            val allWindows = try { service.windows } catch (_: Exception) { null }
            if (allWindows != null) {
                for (win in allWindows) {
                    val winRoot = win.root ?: continue
                    val pkg = winRoot.packageName?.toString() ?: "?"
                    if (browserPackage != null && pkg != browserPackage) {
                        winRoot.recycle()
                        continue
                    }
                    var found = findUrlByViewIds(winRoot, pkg)
                    if (found == null) {
                        found = findUrlNode(winRoot)
                    }
                    winRoot.recycle()
                    if (found != null) return found
                }
            }

            val root = service.rootInActiveWindow
            if (root != null) {
                val pkg = root.packageName?.toString() ?: "?"
                if (browserPackage != null && pkg != browserPackage) {
                    root.recycle()
                    return null
                }

                var result = findUrlByViewIds(root, pkg)
                if (result == null) {
                    val rText = root.text?.toString()
                    if (rText != null) {
                        val d = extractUrlDomain(rText)
                        if (d != null) { root.recycle(); return d }
                    }
                    val rDesc = root.contentDescription?.toString()
                    if (rDesc != null) {
                        val d = extractUrlDomain(rDesc)
                        if (d != null) { root.recycle(); return d }
                    }

                    val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (focused != null) {
                        val fText = focused.text?.toString()
                        if (fText != null) {
                            val d = extractUrlDomain(fText)
                            if (d != null) { focused.recycle(); root.recycle(); return d }
                        }
                        val fDesc = focused.contentDescription?.toString()
                        if (fDesc != null) {
                            val d = extractUrlDomain(fDesc)
                            if (d != null) { focused.recycle(); root.recycle(); return d }
                        }
                        if (android.os.Build.VERSION.SDK_INT >= 26) {
                            val fHint = focused.hintText?.toString()
                            if (fHint != null) {
                                val d = extractUrlDomain(fHint)
                                if (d != null) { focused.recycle(); root.recycle(); return d }
                            }
                        }
                        focused.recycle()
                    }
                    result = findUrlNode(root)
                }
                root.recycle()
                return result
            }
            return null
        } catch (e: Exception) {
            Log.e("Zenith_URL", "Error extracting URL: ${e.message}")
            return null
        }
    }

    fun findUrlNodeQuick(node: AccessibilityNodeInfo?): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        node?.let { queue.add(it) }
        var depth = 0
        while (queue.isNotEmpty() && depth < 60) {
            val current = queue.poll() ?: continue
            depth++
            val cls = current.className?.toString() ?: ""
            val text = current.text?.toString()
            val desc = current.contentDescription?.toString()
            if (cls.contains("EditText") || cls.contains("UrlBar") || cls.contains("Omnibox") || cls.contains("LocationBar")) {
                if (text != null && text.isNotBlank()) {
                    val domain = extractUrlDomain(text)
                    if (domain != null) {
                        current.recycle()
                        while (queue.isNotEmpty()) queue.poll()?.recycle()
                        return domain
                    }
                }
                if (desc != null && desc.isNotBlank()) {
                    val domain = extractUrlDomain(desc)
                    if (domain != null) {
                        current.recycle()
                        while (queue.isNotEmpty()) queue.poll()?.recycle()
                        return domain
                    }
                }
            }
            for (i in 0 until current.childCount) {
                val child = current.getChild(i) ?: continue
                queue.add(child)
            }
            if (current !== node) current.recycle()
        }
        return null
    }

    private fun findUrlNode(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null

        val className = node.className?.toString() ?: ""
        val viewId = node.viewIdResourceName?.toString() ?: ""
        val text = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()

        val isUrlInput = className.contains("EditText") || className.contains("TextView") ||
                className.contains("UrlBar") || className.contains("Omnibox") ||
                className.contains("Url") || className.contains("SearchView") ||
                className.contains("LocationBar") || className.contains("Location") ||
                className.contains("AutoComplete") || className.contains("MultiAutoComplete")

        val isUrlId = viewId.contains("url_bar") || viewId.contains("omnibox") ||
                viewId.contains("location_bar") || viewId.contains("search_box") ||
                viewId.contains("url") || viewId.contains("address_bar")

        val hasText = text != null && !text.isBlank() && text.length < 2048
        val hasDesc = contentDesc != null && !contentDesc.isBlank() && contentDesc.length < 2048
        val isPotentialUrl = (hasText && (text!!.contains(".") || text.startsWith("http") || text.startsWith("www"))) ||
                (hasDesc && (contentDesc!!.contains(".") || contentDesc.startsWith("http") || contentDesc.startsWith("www")))

        if (isUrlInput || isUrlId || isPotentialUrl) {
            if (text != null && !text.isBlank()) {
                val domain = extractUrlDomain(text)
                if (domain != null) return domain
            }
            if (contentDesc != null && !contentDesc.isBlank()) {
                val domain = extractUrlDomain(contentDesc)
                if (domain != null) return domain
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findUrlNode(child)
            if (result != null) {
                child?.recycle()
                return result
            }
            child?.recycle()
        }
        return null
    }

    fun extractUrlDomain(text: String): String? {
        if (text.isBlank() || text.length > 2048 || text.contains("\n")) return null

        val filtered = WebsiteRepository.filterUrlFromText(text)
        val urlText = if (filtered != null) {
            if (!filtered.startsWith("http://") && !filtered.startsWith("https://")) "https://$filtered" else filtered
        } else {
            val trimmed = text.trim()
            when {
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("www.", ignoreCase = true) -> trimmed
                trimmed.contains(".") && !trimmed.contains(" ")
                        && trimmed.length > trimmed.lastIndexOf('.') + 2 -> "https://$trimmed"
                else -> return null
            }
        }
        val domain = WebsiteRepository.extractDomain(urlText)
        if (domain == null || !domain.contains(".") || domain.endsWith(".")) return null

        val parts = domain.split(".")
        val tld = parts.lastOrNull() ?: ""
        if (tld.isEmpty() || !tld.any { it.isLetter() }) return null

        return domain
    }

    fun checkAccessibilityEvent(event: AccessibilityEvent): String? {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val pkg = event.packageName?.toString() ?: return null
            if (WebsiteRepository.isKnownBrowser(pkg)) {
                val source = event.source
                if (source != null) {
                    val srcText = source.text?.toString()?.trim()
                    if (!srcText.isNullOrBlank()) {
                        val domain = extractUrlDomain(srcText)
                        source.recycle()
                        return domain
                    }
                    source.recycle()
                }
            }
        }
        return null
    }

    fun checkViewTextChanged(packageName: String, event: AccessibilityEvent): String? {
        if (!WebsiteRepository.isKnownBrowser(packageName)) return null
        val className = event.className?.toString() ?: ""
        val isUrlInput = className.contains("EditText") || className.contains("UrlBar") ||
                className.contains("Omnibox") || className.contains("Url") ||
                className.contains("LocationBar")
        if (!isUrlInput) return null

        val rawText = event.text?.joinToString("") ?: return null
        if (rawText.isBlank()) return null
        return extractUrlDomain(rawText)
    }

    fun extractFromWindowStateChange(packageName: String, event: AccessibilityEvent): String? {
        if (!WebsiteRepository.isKnownBrowser(packageName)) return null
        try {
            val source = event.source
            if (source != null) {
                val srcText = source.text?.toString()?.trim()
                if (!srcText.isNullOrBlank()) {
                    val srcDomain = extractUrlDomain(srcText)
                    source.recycle()
                    return srcDomain
                }
                source.recycle()
            }
        } catch (_: Exception) {}

        val eventText = event.text?.joinToString("")?.trim()
        if (eventText != null && eventText.isNotBlank()) {
            return extractUrlDomain(eventText)
        }

        try {
            val root = service.rootInActiveWindow
            if (root != null && root.packageName == packageName) {
                val result = findUrlNodeQuick(root)
                root.recycle()
                if (result != null) return result
            }
        } catch (_: Exception) {}

        return null
    }

    fun extractFromActiveWindow(packageName: String): String? {
        try {
            val root = service.rootInActiveWindow
            if (root != null && root.packageName == packageName) {
                var result = findUrlByViewIds(root, packageName)
                if (result == null) {
                    result = findUrlNodeQuick(root)
                }
                if (result == null) {
                    val allWindows = try { service.windows } catch (_: Exception) { null }
                    if (allWindows != null) {
                        for (win in allWindows) {
                            val winRoot = win.root ?: continue
                            if (winRoot.packageName == packageName) {
                                result = findUrlByViewIds(winRoot, packageName)
                                if (result == null) result = findUrlNodeQuick(winRoot)
                            }
                            winRoot.recycle()
                            if (result != null) break
                        }
                    }
                }

                root.recycle()
                return result
            }
        } catch (_: Exception) {}
        return null
    }
}
