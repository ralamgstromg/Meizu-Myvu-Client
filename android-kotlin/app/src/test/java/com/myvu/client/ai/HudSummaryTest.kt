package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HudSummaryTest {

    @Test
    fun shortTextIsUnchanged() {
        assertEquals("Son las 5.", HudSummary.condense("  Son las 5.  "))
    }

    @Test
    fun keepsWholeLeadingSentencesWithinLimit() {
        val text = "Primera frase corta. Segunda frase corta. " + "x".repeat(200)

        assertEquals("Primera frase corta. Segunda frase corta.", HudSummary.condense(text, 60))
    }

    @Test
    fun longFirstSentenceIsCutAtWordBoundaryWithEllipsis() {
        val text = "palabra ".repeat(50).trim()

        val result = HudSummary.condense(text, 40)

        assertTrue(result.length <= 40)
        assertTrue(result.endsWith("…"))
        assertTrue(!result.contains("palabr…"))
    }
}
