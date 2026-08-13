package com.myvu.client.ai

import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

object HttpEndpoint {
    @JvmStatic
    @Throws(IOException::class)
    fun parse(rawUrl: String?, label: String): URL {
        if (rawUrl.isNullOrBlank()) {
            throw IOException("$label is missing (endpoint is empty)")
        }
        val trimmed = rawUrl.trim()
        val formatted = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
        return try {
            URL(formatted)
        } catch (e: MalformedURLException) {
            throw IOException("invalid $label: $rawUrl (${e.message})", e)
        }
    }
}
