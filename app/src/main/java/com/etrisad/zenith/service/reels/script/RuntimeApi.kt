/*
 * Ported from Curbox (https://github.com/iecal/curbox-android-kt-rewrite).
 * Original work: Copyright (c) Curbox contributors.
 * Adapted for Zenith: package renamed, feature-specific integration.
 */
package com.etrisad.zenith.service.reels.script

/**
 * Host bridge between the interpreter and the Android world (accessibility nodes).
 * Implemented fresh per script run by the reel runtime.
 */
interface RuntimeApi {
    /** Predefined read-only globals available to the script, e.g. `app`, `screen`, `event`. */
    fun provideGlobals(): Map<String, Any?>

    /** Invoke a global function by [name]. Throws [ScriptError] if the name is unknown. */
    fun callFunction(name: String, args: List<Any?>, named: Map<String, Any?>): Any?
}

/**
 * A script-visible handle to a UI node. The host wraps an AccessibilityNodeInfo behind this
 * interface so the interpreter stays free of Android types.
 */
interface ScriptNode {
    /** Read a property such as `x`, `text`, `clickable`, `childCount`. */
    fun prop(name: String): Any?

    /** Call a method such as `find`, `findAll`, `child`, `parent`, `subtreeText`. */
    fun call(name: String, args: List<Any?>, named: Map<String, Any?>): Any?
}
