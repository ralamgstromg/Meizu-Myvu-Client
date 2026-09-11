package com.myvu.client.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactHelperTest {

    @Test
    fun testCleanTextStripsAccentsPunctuationAndEmojis() {
        val raw = "¡Matías Castro (Hijo) - Celular 📱!"
        val cleaned = ContactHelper.cleanText(raw)
        assertEquals("matias castro hijo celular", cleaned)
    }

    @Test
    fun testMatiasCastroHijoMatchesMatiasQuery() {
        val query = "matias"
        val targetContact = "Matias Castro hijo"
        val unrelatedContact = "Carlos Gomez"

        val targetScore = ContactHelper.calculateScore(query, targetContact)
        val unrelatedScore = ContactHelper.calculateScore(query, unrelatedContact)

        assertTrue("Target score ($targetScore) should be > 500 for prefix & containment match", targetScore >= 500)
        assertTrue("Target score ($targetScore) should vastly beat unrelated ($unrelatedScore)", targetScore > unrelatedScore)
    }

    @Test
    fun testMatiasCastroHijoMatchesMatiasCastroQueryBest() {
        val query = "matias castro"
        val targetContact = "Matias Castro hijo"
        val partialContact = "Matias Ortiz"
        val otherPartial = "Carlos Castro"

        val targetScore = ContactHelper.calculateScore(query, targetContact)
        val partialScore1 = ContactHelper.calculateScore(query, partialContact)
        val partialScore2 = ContactHelper.calculateScore(query, otherPartial)

        // Target contact has 100% query token containment (both "matias" and "castro") + prefix match
        assertTrue("Target score ($targetScore) should exceed 800", targetScore >= 800)
        assertTrue("Target ($targetScore) should beat partial1 ($partialScore1)", targetScore > partialScore1)
        assertTrue("Target ($targetScore) should beat partial2 ($partialScore2)", targetScore > partialScore2)
    }

    @Test
    fun testSubsetContainmentWorksIndependentOfOrder() {
        val query = "castro matias"
        val targetContact = "Matias Castro hijo"

        val score = ContactHelper.calculateScore(query, targetContact)
        // Both tokens present (100% containment) gives high score
        assertTrue("Score ($score) should be at least 400 for 100% subset containment", score >= 400)
    }

    @Test
    fun testConciseContactBreaksTieWhenQueryIsExact() {
        val query = "matias castro"
        val exactContact = "Matias Castro"
        val compoundContact = "Matias Castro hijo"

        val exactScore = ContactHelper.calculateScore(query, exactContact)
        val compoundScore = ContactHelper.calculateScore(query, compoundContact)

        assertEquals(3000, exactScore)
        assertTrue("Exact match (3000) should be higher than compound match ($compoundScore)", exactScore > compoundScore)
        assertTrue("Compound match should still be very strong (> 800)", compoundScore > 800)
    }

    @Test
    fun testMatiasCastroQueryStrictlyRejectsDenisCastro() {
        val query = "matias castro"
        val wrongContact = "Denis Castro"
        val exactContact = "Matias Castro"
        val compoundContact = "Matias Castro hijo"

        val wrongScore = ContactHelper.calculateScore(query, wrongContact)
        val exactScore = ContactHelper.calculateScore(query, exactContact)
        val compoundScore = ContactHelper.calculateScore(query, compoundContact)

        assertEquals("Denis Castro MUST have 0 score because given name 'matias' completely mismatches", 0, wrongScore)
        assertEquals(3000, exactScore)
        assertTrue("Compound match must be >= 2000", compoundScore >= 2000)
    }

    @Test
    fun testSequentialOrderPriorityBeatsUnordered() {
        val query = "matias castro"
        val orderedContact = "Matias David Castro"
        val unorderedContact = "Castro Matias"

        val orderedScore = ContactHelper.calculateScore(query, orderedContact)
        val unorderedScore = ContactHelper.calculateScore(query, unorderedContact)

        assertTrue("Ordered score ($orderedScore) must exceed 1200", orderedScore >= 1200)
        assertTrue("Ordered score ($orderedScore) must beat unordered ($unorderedScore)", orderedScore > unorderedScore)
    }

    @Test
    fun testSingleTokenSurnameQueryMatchesContactWithSurname() {
        val query = "Castro"
        val surnameContact = "Denis Castro"
        val prefixContact = "Castro Hermanos"
        val unrelatedContact = "Carlos Gomez"

        val surnameScore = ContactHelper.calculateScore(query, surnameContact)
        val prefixScore = ContactHelper.calculateScore(query, prefixContact)
        val unrelatedScore = ContactHelper.calculateScore(query, unrelatedContact)

        assertTrue("Surname match should have valid score (>= 400)", surnameScore >= 400)
        assertTrue("Prefix match should have high score (>= 2000)", prefixScore >= 2000)
        assertEquals("Unrelated contact must have 0 score", 0, unrelatedScore)
    }

    @Test
    fun testSpanishNicknameAliasesResolveSemantically() {
        val query = "mati castro"
        val targetContact = "Matias Castro"
        val wrongContact = "Denis Castro"

        val targetScore = ContactHelper.calculateScore(query, targetContact)
        val wrongScore = ContactHelper.calculateScore(query, wrongContact)

        assertTrue("Semantic nickname 'mati' -> 'matias' should yield score >= 1200", targetScore >= 1200)
        assertEquals("Denis Castro must be rejected with 0 score", 0, wrongScore)
    }

    @Test
    fun testSpanishKinshipAliasesResolveSemantically() {
        val query = "papa"
        val targetContact = "Padre"
        val unrelatedContact = "Carlos Gomez"

        val targetScore = ContactHelper.calculateScore(query, targetContact)
        val unrelatedScore = ContactHelper.calculateScore(query, unrelatedContact)

        assertTrue("Kinship 'papa' -> 'padre' should yield valid score (>= 400)", targetScore >= 400)
        assertEquals("Unrelated contact must have 0 score", 0, unrelatedScore)
    }

    @Test
    fun testFormatColombianPhone() {
        assertEquals("573011161686", ContactHelper.formatColombianPhone("301 116 1686"))
        assertEquals("573011161686", ContactHelper.formatColombianPhone("+57 301 116 1686"))
        assertEquals("576012345678", ContactHelper.formatColombianPhone("6012345678"))
    }

    @Test
    fun testCleanPunctuation() {
        assertEquals("Raúl Castro", ContactHelper.cleanPunctuation("Raúl Castro."))
        assertEquals("Matías Castro", ContactHelper.cleanPunctuation("¿Matías Castro?!"))
        assertEquals("Carlos Gomez", ContactHelper.cleanPunctuation("  Carlos Gomez,  "))
    }

    @Test
    fun testSentenceDelimiterSeparatesContactFromMessageWithComma() {
        // Simulating the user query: "Matías Castro. Hola hijo, ¿cómo vas? ¿Cómo te fue?"
        val text = "Matías Castro. Hola hijo, ¿cómo vas? ¿Cómo te fue?"
        val periodParts = text.split(Regex("(?iu)(?<=[a-z0-9áéíóúñüÁÉÍÓÚÑÜ])\\.\\s+"), 2)
        assertEquals(2, periodParts.size)
        assertEquals("Matías Castro", ContactHelper.cleanPunctuation(periodParts[0]))
        assertEquals("Hola hijo, ¿cómo vas? ¿Cómo te fue?", periodParts[1].trim())
    }
}
