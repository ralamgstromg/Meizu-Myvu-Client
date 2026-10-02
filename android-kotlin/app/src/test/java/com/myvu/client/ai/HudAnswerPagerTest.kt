package com.myvu.client.ai

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HudAnswerPagerTest {

    private var now = 0L
    private val text = (1..12).joinToString(" ") { "Oración número $it del pronóstico." }

    @Before
    fun setUp() {
        HudAnswerPager.timeProvider = { now }
    }

    @After
    fun tearDown() {
        HudAnswerPager.close()
        HudAnswerPager.timeProvider = { System.currentTimeMillis() }
    }

    @Test
    fun paginateKeepsSentencesWithinLimitAndFirstPageMatchesSummary() {
        val pages = HudSummary.paginate(text)

        assertTrue(pages.size > 1)
        assertTrue(pages.all { it.length <= HudSummary.DEFAULT_MAX_CHARS })
        assertEquals(HudSummary.condense(text), pages.first())
        assertEquals(text, pages.joinToString(" "))
    }

    @Test
    fun pagesForwardAndBackAndClosesAtTheEnd() {
        HudAnswerPager.open(text)
        val total = HudSummary.paginate(text).size

        assertNull(HudAnswerPager.previous())
        assertTrue(HudAnswerPager.next()!!.startsWith("(2/$total)"))
        assertTrue(HudAnswerPager.previous()!!.startsWith("(1/$total)"))
        repeat(total - 1) { HudAnswerPager.next() }
        assertNull(HudAnswerPager.next())
        assertFalse(HudAnswerPager.isOpen())
    }

    @Test
    fun expiresAfterTtl() {
        HudAnswerPager.open(text)
        now += HudAnswerPager.TTL_MS + 1

        assertFalse(HudAnswerPager.isOpen())
        assertNull(HudAnswerPager.next())
    }

    @Test
    fun singlePageAnswerDoesNotOpen() {
        HudAnswerPager.open("Respuesta corta.")

        assertFalse(HudAnswerPager.isOpen())
    }
}
