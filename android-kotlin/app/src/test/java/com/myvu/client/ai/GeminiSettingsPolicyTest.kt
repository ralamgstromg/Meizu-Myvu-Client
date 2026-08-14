package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class GeminiSettingsPolicyTest {

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
    }

    @Test
    fun defaultGeminiFallbackPolicyIsNanoThenApi() {
        assertEquals(
            GeminiFallbackPolicy.NANO_THEN_API,
            GeminiFallbackPolicy.fromId(null)
        )
    }

    @Test
    fun unknownGeminiFallbackPolicyIdDefaultsToNanoThenApi() {
        assertEquals(
            GeminiFallbackPolicy.NANO_THEN_API,
            GeminiFallbackPolicy.fromId("invalid_policy_id")
        )
    }

    @Test
    fun validPolicyIdsRoundTripCorrectly() {
        assertEquals(
            GeminiFallbackPolicy.NANO_THEN_API,
            GeminiFallbackPolicy.fromId("nano_then_api")
        )
        assertEquals(
            GeminiFallbackPolicy.NANO_ONLY,
            GeminiFallbackPolicy.fromId("nano_only")
        )
        assertEquals(
            GeminiFallbackPolicy.API_ONLY,
            GeminiFallbackPolicy.fromId("api_only")
        )
    }

    @Test
    fun policyAllowsApiFallbackPropertyCorrect() {
        assertTrue(GeminiFallbackPolicy.NANO_THEN_API.allowsApiFallback)
        assertFalse(GeminiFallbackPolicy.NANO_ONLY.allowsApiFallback)
        assertFalse(GeminiFallbackPolicy.API_ONLY.allowsApiFallback)
    }
}
