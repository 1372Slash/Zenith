/*
 * Ported from Curbox (https://github.com/iecal/curbox-android-kt-rewrite).
 * Original work: Copyright (c) Curbox contributors.
 * Adapted for Zenith: package renamed, feature-specific integration.
 */
package com.etrisad.zenith.service.reels

import android.view.accessibility.AccessibilityNodeInfo
import com.etrisad.zenith.service.reels.script.ScriptError
import com.etrisad.zenith.service.reels.script.Values

/**
 * Selector based search over a live accessibility tree, using the ported script selector
 * vocabulary: `id`, `text`, `desc`, `class`, `textContains`, `descContains`, `clickable`,
 * `scrollable`, `selected`, `checked`, and `path`.
 *
 * Whenever a selector can drive the platform's batched finders
 * (findAccessibilityNodeInfosByViewId / ByText) it is used as an anchor and the remaining
 * selectors are verified on that small candidate set; otherwise the tree is walked depth first.
 * `path` resolves a class index hierarchy walk from the start node.
 *
 * Every node returned by [findFirst] / [findAll] is owned by the caller and should be recycled.
 */
object ReelNodeFinder {

    const val MAX_FIND_RESULTS = 500

    fun interface Matcher { fun test(node: AccessibilityNodeInfo): Boolean }

    data class PathSegment(val className: String, val index: Int, val isWildcard: Boolean)

    fun compileMatchers(named: Map<String, Any?>): List<Matcher> = named.mapNotNull { (key, value) ->
        when (key) {
            "id" -> Values.stringify(value).let { s -> Matcher { it.viewIdResourceName == s } }
            "text" -> Values.stringify(value).let { s -> Matcher { it.text?.toString() == s } }
            "desc" -> Values.stringify(value).let { s -> Matcher { it.contentDescription?.toString() == s } }
            "class" -> Values.stringify(value).let { s -> Matcher { it.className?.toString() == s } }
            "textContains" -> Values.stringify(value).let { s -> Matcher { it.text?.toString()?.contains(s, true) == true } }
            "descContains" -> Values.stringify(value).let { s -> Matcher { it.contentDescription?.toString()?.contains(s, true) == true } }
            "clickable" -> Values.isTruthy(value).let { b -> Matcher { it.isClickable == b } }
            "scrollable" -> Values.isTruthy(value).let { b -> Matcher { it.isScrollable == b } }
            "selected" -> Values.isTruthy(value).let { b -> Matcher { it.isSelected == b } }
            "checked" -> Values.isTruthy(value).let { b -> Matcher { it.isChecked == b } }
            "path" -> null
            else -> throw ScriptError("unknown selector '$key'")
        }
    }

    fun matchesAll(node: AccessibilityNodeInfo, matchers: List<Matcher>): Boolean {
        for (m in matchers) if (!m.test(node)) return false
        return true
    }

    fun findFirst(
        start: AccessibilityNodeInfo,
        named: Map<String, Any?>,
        onVisit: () -> Unit = {}
    ): AccessibilityNodeInfo? {
        val matchers = compileMatchers(named)
        named["path"]?.let { spec ->
            val candidates = walkPath(start, parsePath(Values.stringify(spec)))
            return firstMatch(candidates, matchers, onVisit)
        }
        val candidates = anchorCandidates(start, named) ?: return dfsFirst(start, matchers, onVisit)
        return firstMatch(candidates, matchers, onVisit)
    }

    fun findAll(
        start: AccessibilityNodeInfo,
        named: Map<String, Any?>,
        onVisit: () -> Unit = {}
    ): List<AccessibilityNodeInfo> {
        val matchers = compileMatchers(named)
        val out = ArrayList<AccessibilityNodeInfo>()
        val candidates = named["path"]?.let { spec -> walkPath(start, parsePath(Values.stringify(spec))) }
            ?: anchorCandidates(start, named)
        if (candidates != null) {
            for (c in candidates) {
                onVisit()
                if (out.size < MAX_FIND_RESULTS && matchesAll(c, matchers)) out.add(c) else recycle(c)
            }
        } else {
            dfsCollect(start, matchers, out, onVisit)
        }
        return out
    }

    private fun firstMatch(
        candidates: List<AccessibilityNodeInfo>,
        matchers: List<Matcher>,
        onVisit: () -> Unit
    ): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        for (c in candidates) {
            onVisit()
            if (result == null && matchesAll(c, matchers)) result = c else recycle(c)
        }
        return result
    }

    private fun anchorCandidates(
        start: AccessibilityNodeInfo,
        named: Map<String, Any?>
    ): List<AccessibilityNodeInfo>? {
        val list = when {
            named.containsKey("id") -> start.findAccessibilityNodeInfosByViewId(Values.stringify(named["id"]))
            named.containsKey("text") -> start.findAccessibilityNodeInfosByText(Values.stringify(named["text"]))
            named.containsKey("textContains") -> start.findAccessibilityNodeInfosByText(Values.stringify(named["textContains"]))
            named.containsKey("desc") -> start.findAccessibilityNodeInfosByText(Values.stringify(named["desc"]))
            named.containsKey("descContains") -> start.findAccessibilityNodeInfosByText(Values.stringify(named["descContains"]))
            else -> return null
        }
        return list ?: emptyList()
    }

    private fun dfsFirst(
        start: AccessibilityNodeInfo,
        matchers: List<Matcher>,
        onVisit: () -> Unit
    ): AccessibilityNodeInfo? {
        onVisit()
        if (matchesAll(start, matchers)) return obtain(start)
        for (i in 0 until start.childCount) {
            val child = start.getChild(i) ?: continue
            val found = dfsFirst(child, matchers, onVisit)
            recycle(child)
            if (found != null) return found
        }
        return null
    }

    private fun dfsCollect(
        node: AccessibilityNodeInfo,
        matchers: List<Matcher>,
        out: MutableList<AccessibilityNodeInfo>,
        onVisit: () -> Unit
    ) {
        if (out.size >= MAX_FIND_RESULTS) return
        onVisit()
        if (matchesAll(node, matchers)) out.add(obtain(node))
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dfsCollect(child, matchers, out, onVisit)
            recycle(child)
        }
    }

    fun parseSelector(spec: String): Map<String, Any?> {
        val named = LinkedHashMap<String, Any?>()
        for (part in spec.split(";")) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            val sep = trimmed.indexOf(':')
            if (sep < 0) {
                named["id"] = trimmed
                continue
            }
            val key = canonicalKey(trimmed.substring(0, sep).trim())
            val value = trimmed.substring(sep + 1).trim()
            when (key) {
                null -> named["id"] = trimmed
                "clickable", "scrollable", "selected", "checked" -> named[key] = value.toBoolean()
                else -> named[key] = value
            }
        }
        return named
    }

    private fun canonicalKey(raw: String): String? = when (raw.lowercase()) {
        "id" -> "id"
        "text" -> "text"
        "desc" -> "desc"
        "class" -> "class"
        "textcontains" -> "textContains"
        "desccontains" -> "descContains"
        "clickable" -> "clickable"
        "scrollable" -> "scrollable"
        "selected" -> "selected"
        "checked" -> "checked"
        "path" -> "path"
        else -> null
    }

    fun parsePath(path: String): List<PathSegment> {
        if (path.isEmpty()) return emptyList()
        return path.split(">").map { segment ->
            val bracketStart = segment.indexOf('[')
            if (bracketStart >= 0) {
                val className = segment.substring(0, bracketStart)
                val indexStr = segment.substring(bracketStart + 1, segment.indexOf(']'))
                if (indexStr == "*") {
                    PathSegment(className, -1, isWildcard = true)
                } else {
                    PathSegment(className, indexStr.toIntOrNull() ?: 0, isWildcard = false)
                }
            } else {
                PathSegment(segment, 0, isWildcard = false)
            }
        }
    }

    /** Descend [root] through [segments], returning the matched leaf nodes (owned by the caller). */
    fun walkPath(root: AccessibilityNodeInfo, segments: List<PathSegment>): List<AccessibilityNodeInfo> {
        if (segments.isEmpty()) return emptyList()

        var currentNodes = mutableListOf(root)

        for (seg in segments) {
            val nextNodes = mutableListOf<AccessibilityNodeInfo>()

            for (current in currentNodes) {
                if (seg.isWildcard) {
                    for (i in 0 until current.childCount) {
                        val child = current.getChild(i) ?: continue
                        if (child.className?.toString() == seg.className) nextNodes.add(child) else recycle(child)
                    }
                } else {
                    var match: AccessibilityNodeInfo? = null
                    var matchCount = 0
                    for (i in 0 until current.childCount) {
                        val child = current.getChild(i) ?: continue
                        if (child.className?.toString() == seg.className) {
                            if (matchCount == seg.index) {
                                match = child
                                break
                            }
                            matchCount++
                            recycle(child)
                        } else {
                            recycle(child)
                        }
                    }
                    if (match != null) nextNodes.add(match)
                }

                if (current != root) recycle(current)
            }

            currentNodes = nextNodes
            if (currentNodes.isEmpty()) return emptyList()
        }

        return currentNodes
    }

    private fun obtain(node: AccessibilityNodeInfo): AccessibilityNodeInfo =
        @Suppress("DEPRECATION") AccessibilityNodeInfo.obtain(node)

    fun recycle(node: AccessibilityNodeInfo) {
        try {
            @Suppress("DEPRECATION") node.recycle()
        } catch (_: Exception) {}
    }
}
