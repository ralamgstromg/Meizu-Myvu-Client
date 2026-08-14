package com.myvu.client.ai

import java.util.Locale

object AndroidSpeechLanguagePolicy {
    fun candidates(requested: String?): List<String> {
        val raw = requested.orEmpty().trim().replace('_', '-')
        val sanitized = if (raw.contains("-u-")) raw.substringBefore("-u-") else raw
        val clean = sanitized.substringBefore("-x-").trim()

        val values = ArrayList<String>()
        if (clean.isNotEmpty()) {
            values += clean
            val base = clean.substringBefore('-')
            if (base != clean) values += base
        }
        values += listOf("es-CO", "es")
        return values.map { it.lowercase(Locale.US) }
            .distinct()
            .map { tag ->
                tag.split('-').mapIndexed { index, part ->
                    if (index > 0 && part.length == 2) part.uppercase(Locale.US) else part
                }.joinToString("-")
            }
    }
}

object AndroidSpeechErrorPolicy {
    fun isLanguageFallbackError(code: Int): Boolean = code == 11 || code == 12 || code == 7
}
