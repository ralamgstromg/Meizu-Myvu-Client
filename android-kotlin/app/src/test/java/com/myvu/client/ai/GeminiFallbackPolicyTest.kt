package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiFallbackPolicyTest {
    @Test
    fun defaultPolicyUsesNanoThenApi() {
        assertEquals(GeminiFallbackPolicy.NANO_THEN_API, GeminiFallbackPolicy.fromId(null))
    }

    @Test
    fun unknownPolicyFallsBackToSafeDefault() {
        assertEquals(GeminiFallbackPolicy.NANO_THEN_API, GeminiFallbackPolicy.fromId("bad-value"))
    }

    @Test
    fun localOnlyPolicyDisallowsCloudFallback() {
        assertFalse(GeminiFallbackPolicy.NANO_ONLY.allowsApiFallback)
        assertTrue(GeminiFallbackPolicy.NANO_THEN_API.allowsApiFallback)
    }
}
