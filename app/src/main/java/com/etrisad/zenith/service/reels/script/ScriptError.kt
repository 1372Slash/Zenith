package com.etrisad.zenith.service.reels.script

class ScriptError(
    message: String,
    val line: Int = 0
) : Exception(if (line > 0) "Line $line: $message" else message)
