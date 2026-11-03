/*
 * Ported from Curbox (https://github.com/iecal/curbox-android-kt-rewrite).
 * Original work: Copyright (c) Curbox contributors.
 * Adapted for Zenith: package renamed, feature-specific integration.
 */
package com.etrisad.zenith.service.reels.script

/**
 * Thrown for any failure while lexing, parsing, or running a reel detector script.
 * [line] is the 1-based source line where the failure was detected (0 if unknown).
 */
class ScriptError(
    message: String,
    val line: Int = 0
) : Exception(if (line > 0) "Line $line: $message" else message)
