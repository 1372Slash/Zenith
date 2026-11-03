package com.etrisad.zenith.service.reels.script

interface RuntimeApi {
    fun provideGlobals(): Map<String, Any?>

    fun callFunction(name: String, args: List<Any?>, named: Map<String, Any?>): Any?
}

interface ScriptNode {
    fun prop(name: String): Any?

    fun call(name: String, args: List<Any?>, named: Map<String, Any?>): Any?
}
