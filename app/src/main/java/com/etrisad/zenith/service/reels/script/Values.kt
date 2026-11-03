/*
 * Ported from Curbox (https://github.com/iecal/curbox-android-kt-rewrite).
 * Original work: Copyright (c) Curbox contributors.
 * Adapted for Zenith: package renamed, feature-specific integration.
 */
package com.etrisad.zenith.service.reels.script

/**
 * Helpers for the dynamically typed value model. Script values are plain Kotlin objects:
 * [Double], [String], [Boolean], `null`, [List] (`List<Any?>`), and [ScriptNode].
 */
object Values {

    /** Only `false` and `null` are falsy; everything else (including 0) is truthy. */
    fun isTruthy(v: Any?): Boolean = when (v) {
        null -> false
        is Boolean -> v
        else -> true
    }

    fun equal(a: Any?, b: Any?): Boolean = when {
        a == null && b == null -> true
        a is Double && b is Double -> a == b
        else -> a == b
    }

    fun asNumber(v: Any?, line: Int): Double = when (v) {
        is Double -> v
        is Boolean -> if (v) 1.0 else 0.0
        else -> throw ScriptError("expected a number but got ${typeName(v)}", line)
    }

    fun asInt(v: Any?, line: Int): Int = asNumber(v, line).toInt()

    fun typeName(v: Any?): String = when (v) {
        null -> "null"
        is Double -> "number"
        is String -> "string"
        is Boolean -> "boolean"
        is List<*> -> "list"
        is ScriptNode -> "node"
        else -> v::class.simpleName ?: "value"
    }

    /** Display form used by `str()`, `log()`, and string concatenation. */
    fun stringify(v: Any?): String = when (v) {
        null -> "null"
        is Double -> if (v.isFinite() && v == Math.floor(v)) v.toLong().toString() else v.toString()
        is Boolean -> v.toString()
        is List<*> -> v.joinToString(prefix = "[", postfix = "]") { stringify(it) }
        is ScriptNode -> "node(${v.prop("id") ?: v.prop("class") ?: "?"})"
        else -> v.toString()
    }
}
