/*
 * Ported from Curbox (https://github.com/iecal/curbox-android-kt-rewrite).
 * Original work: Copyright (c) Curbox contributors.
 * Adapted for Zenith: reduced to a read-only runtime used by the reel detectors
 * (no overlay drawing, global actions, or persistence).
 */
package com.etrisad.zenith.service.reels

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.etrisad.zenith.service.reels.script.Budget
import com.etrisad.zenith.service.reels.script.Builtins
import com.etrisad.zenith.service.reels.script.RuntimeApi
import com.etrisad.zenith.service.reels.script.ScriptError
import com.etrisad.zenith.service.reels.script.ScriptNode
import com.etrisad.zenith.service.reels.script.Values
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-run [RuntimeApi] implementation bridging the ported script language to the live
 * accessibility tree. Created fresh for every reel detection.
 *
 * Accessibility node handles obtained during a run are tracked in [ownedNodes] and recycled in
 * [finish]; the supplied [root] is owned by the caller.
 */
class ReelScriptRuntime(
    private val context: Context,
    private val root: AccessibilityNodeInfo,
    private val budget: Budget,
    private val globals: Map<String, Any?>,
    private val scriptId: String
) : RuntimeApi {

    val output = StringBuilder()

    private val ownedNodes = ArrayList<AccessibilityNodeInfo>()

    companion object {
        // Resolved foreign-app string resources, cached across runs (key = "package:resName").
        private val appStringCache = ConcurrentHashMap<String, String>()
        // Compiled patterns are immutable and safe to reuse across short-lived runtime instances.
        private val regexCache = ConcurrentHashMap<String, Regex>()

        private const val MAX_SUBTREE_DEPTH = 64
        private const val MAX_SUBTREE_CHARS = 100_000
    }

    override fun provideGlobals(): Map<String, Any?> = globals

    override fun callFunction(name: String, args: List<Any?>, named: Map<String, Any?>): Any? {
        return when (name) {
            "root" -> NodeHandle(root)
            "find" -> ReelNodeFinder.findFirst(root, named) { budget.countNode() }?.let { wrap(it) }
            "findAll" -> ReelNodeFinder.findAll(root, named) { budget.countNode() }.map { wrap(it) }
            "log" -> {
                output.append(args.joinToString(" ") { Values.stringify(it) }).append('\n')
                null
            }
            "appString" -> resolveAppString(args.getOrNull(0))
            "subtreeText" -> {
                val target = args.getOrNull(0)
                if (target !is NodeHandle) {
                    throw ScriptError("subtreeText() expects a node, got ${Values.typeName(target)}")
                }
                target.collectSubtreeText(args.drop(1))
            }
            else -> {
                val result = Builtins.tryCall(name, args, budget, regexCache)
                if (result === Builtins.UNKNOWN) throw ScriptError("unknown function '$name'")
                result
            }
        }
    }

    /** Recycle every node handle this run created. Call exactly once when the run ends. */
    fun finish() {
        for (node in ownedNodes) {
            try {
                @Suppress("DEPRECATION") node.recycle()
            } catch (_: Exception) {}
        }
        ownedNodes.clear()
    }

    private fun wrap(node: AccessibilityNodeInfo): NodeHandle {
        ownedNodes.add(node)
        return NodeHandle(node)
    }

    private fun resolveAppString(arg: Any?): Any? {
        val resName = arg as? String
            ?: throw ScriptError("appString() expects a resource name string")
        val pkg = globals["app"] as? String ?: return null
        val cacheKey = "$pkg:$resName"
        appStringCache[cacheKey]?.let { return it }
        return try {
            val res = context.packageManager.getResourcesForApplication(pkg)
            val id = res.getIdentifier(resName, "string", pkg)
            if (id == 0) null else res.getString(id).also { appStringCache[cacheKey] = it }
        } catch (_: Exception) {
            null
        }
    }

    private fun recycle(node: AccessibilityNodeInfo) {
        try {
            @Suppress("DEPRECATION") node.recycle()
        } catch (_: Exception) {}
    }

    /** Script-visible wrapper over an [AccessibilityNodeInfo]. Bounds are read once, lazily. */
    inner class NodeHandle(private val node: AccessibilityNodeInfo) : ScriptNode {

        val bounds: Rect by lazy { Rect().also { node.getBoundsInScreen(it) } }

        override fun prop(name: String): Any? = when (name) {
            "id" -> node.viewIdResourceName
            "text" -> node.text?.toString()
            "desc" -> node.contentDescription?.toString()
            "class" -> node.className?.toString()
            "x", "left" -> bounds.left.toDouble()
            "y", "top" -> bounds.top.toDouble()
            "right" -> bounds.right.toDouble()
            "bottom" -> bounds.bottom.toDouble()
            "w", "width" -> bounds.width().toDouble()
            "h", "height" -> bounds.height().toDouble()
            "cx" -> bounds.centerX().toDouble()
            "cy" -> bounds.centerY().toDouble()
            "childCount" -> node.childCount.toDouble()
            "clickable" -> node.isClickable
            "scrollable" -> node.isScrollable
            "checked" -> node.isChecked
            "selected" -> node.isSelected
            "focused" -> node.isFocused
            "enabled" -> node.isEnabled
            "visible" -> node.isVisibleToUser
            "path" -> computePath(node)
            else -> throw ScriptError("unknown node property '$name'")
        }

        override fun call(name: String, args: List<Any?>, named: Map<String, Any?>): Any? = when (name) {
            "find" -> ReelNodeFinder.findFirst(node, named) { budget.countNode() }?.let { wrap(it) }
            "findAll" -> ReelNodeFinder.findAll(node, named) { budget.countNode() }.map { wrap(it) }
            "child" -> {
                val i = Values.asInt(args.getOrNull(0) ?: throw ScriptError("child() needs an index"), 0)
                if (i < 0 || i >= node.childCount) null else node.getChild(i)?.let { wrap(it) }
            }
            "children" -> (0 until node.childCount).mapNotNull { i -> node.getChild(i)?.let { wrap(it) } }
            "parent" -> node.parent?.let { wrap(it) }
            "subtreeText" -> collectSubtreeText(args)
            else -> throw ScriptError("unknown node method '$name'")
        }

        fun collectSubtreeText(args: List<Any?>): String {
            val maxDepth = boundedSubtreeArg(args, 0, "maxDepth", MAX_SUBTREE_DEPTH)
            val maxChars = boundedSubtreeArg(args, 1, "maxChars", MAX_SUBTREE_CHARS)
            if (maxChars == 0) return ""

            val out = StringBuilder(minOf(maxChars, 1024))
            budget.countNode()
            appendNodeText(node, out, maxChars)
            if (out.length >= maxChars || maxDepth == 0) return out.toString()

            fun visit(parent: AccessibilityNodeInfo, depth: Int) {
                if (depth > maxDepth || out.length >= maxChars) return
                for (i in 0 until parent.childCount) {
                    if (out.length >= maxChars) break
                    budget.countNode()
                    val child = parent.getChild(i) ?: continue
                    try {
                        appendNodeText(child, out, maxChars)
                        if (depth < maxDepth && out.length < maxChars) visit(child, depth + 1)
                    } finally {
                        recycle(child)
                    }
                }
            }

            visit(node, 1)
            return out.toString()
        }

        private fun boundedSubtreeArg(
            args: List<Any?>,
            index: Int,
            label: String,
            hardMax: Int
        ): Int {
            val raw = args.getOrNull(index)
                ?: throw ScriptError("subtreeText() requires $label")
            val value = Values.asInt(raw, 0)
            if (value < 0) throw ScriptError("subtreeText() $label must be non-negative")
            if (value > hardMax) {
                throw ScriptError("subtreeText() $label exceeds maximum $hardMax")
            }
            return value
        }

        private fun appendNodeText(target: AccessibilityNodeInfo, out: StringBuilder, maxChars: Int) {
            appendTextPart(target.text?.toString(), out, maxChars)
            val desc = target.contentDescription?.toString()
            if (desc != target.text?.toString()) appendTextPart(desc, out, maxChars)
        }

        private fun appendTextPart(value: String?, out: StringBuilder, maxChars: Int) {
            val text = value?.trim().orEmpty()
            if (text.isEmpty() || out.length >= maxChars) return
            if (out.isNotEmpty()) {
                if (out.length >= maxChars) return
                out.append('\n')
            }
            val remaining = maxChars - out.length
            if (remaining > 0) out.append(text, 0, minOf(text.length, remaining))
        }
    }

    /** Best-effort class-index path from the root, e.g. `FrameLayout[0]/RecyclerView[1]`. */
    private fun computePath(node: AccessibilityNodeInfo): String {
        val segments = ArrayList<String>()
        var current: AccessibilityNodeInfo? = run {
            @Suppress("DEPRECATION")
            AccessibilityNodeInfo.obtain(node)
        }
        var guard = 0
        while (current != null && guard++ < 100) {
            val cur = current
            val parent = cur.parent
            if (parent == null) { recycle(cur); break }
            val cls = cur.className?.toString()?.substringAfterLast('.') ?: "?"
            var index = 0
            for (i in 0 until parent.childCount) {
                val sib = parent.getChild(i) ?: continue
                val isSame = sib == cur
                val sibCls = sib.className?.toString()?.substringAfterLast('.')
                recycle(sib)
                if (isSame) break
                if (sibCls == cls) index++
            }
            segments.add("$cls[$index]")
            recycle(cur)
            current = parent
        }
        return segments.asReversed().joinToString("/")
    }
}
