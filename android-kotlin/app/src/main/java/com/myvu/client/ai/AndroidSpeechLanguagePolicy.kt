package com.myvu.client.ai

import java.util.Locale

object AndroidSpeechLanguagePolicy {
    private val fallbackLanguages = listOf("es-CO", "es", "en-US", "en")

    fun candidates(requested: String?): List<String> {
        val normalized = requested.orEmpty().trim().replace('_', '-')
        val values = ArrayList<String>()
        if (normalized.isNotEmpty()) {
            values += normalized
            val base = normalized.substringBefore('-')
            if (base != normalized) values += base
        }
        values += fallbackLanguages
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
    fun isLanguageFallbackError(code: Int): Boolean = code == 11 || code == 12
}
