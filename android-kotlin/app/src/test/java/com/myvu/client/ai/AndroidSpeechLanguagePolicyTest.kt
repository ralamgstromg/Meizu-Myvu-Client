package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSpeechLanguagePolicyTest {
    @Test
    fun candidatesPreferRequestedRegionThenBaseThenSpanishFallbacks() {
        assertEquals(
            listOf("es-CO", "es"),
            AndroidSpeechLanguagePolicy.candidates("es-CO")
        )
    }

    @Test
    fun candidatesNormalizeAndRemoveDuplicates() {
        assertEquals(
            listOf("es-CO", "es"),
            AndroidSpeechLanguagePolicy.candidates(" ES_co ")
        )
    }

    @Test
    fun languageErrorsAreRetryableButPermissionIsNot() {
        assertTrue(AndroidSpeechErrorPolicy.isLanguageFallbackError(12))
        assertTrue(AndroidSpeechErrorPolicy.isLanguageFallbackError(11))
        assertTrue(!AndroidSpeechErrorPolicy.isLanguageFallbackError(9))
    }
}
