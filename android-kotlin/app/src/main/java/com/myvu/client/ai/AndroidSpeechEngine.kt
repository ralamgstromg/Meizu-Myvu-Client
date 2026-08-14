package com.myvu.client.ai

interface AndroidSpeechEngine {
    fun start(
        languageTag: String?,
        onPartial: ((String) -> Unit)? = null,
        onResult: (String) -> Unit,
        onError: (Int, String) -> Unit
    ): Boolean

    fun stop()
    fun cancel()
    fun destroy()
}
