package com.etrisad.zenith.service.reels.script

object Values {

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

    fun stringify(v: Any?): String = when (v) {
        null -> "null"
        is Double -> if (v.isFinite() && v == Math.floor(v)) v.toLong().toString() else v.toString()
        is Boolean -> v.toString()
        is List<*> -> v.joinToString(prefix = "[", postfix = "]") { stringify(it) }
        is ScriptNode -> "node(${v.prop("id") ?: v.prop("class") ?: "?"})"
        else -> v.toString()
    }
}
