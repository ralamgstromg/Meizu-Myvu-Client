package com.myvu.client.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownUtilsTest {

    @Test
    fun testStripsMarkdownHeadersAndFormatting() {
        val input = "### 📌 Titulo Principal\n**Texto en negrita** y *texto en cursiva* con `codigo inline`."
        val result = MarkdownUtils.formatCleanPlainTextForGlasses(input)
        assertFalse("Should not contain markdown headers", result.contains("#"))
        assertFalse("Should not contain markdown bold/italic asterisks", result.contains("*"))
        assertFalse("Should not contain code backticks", result.contains("`"))
        assertTrue("Should preserve inner text", result.contains("Titulo Principal"))
        assertTrue("Should preserve bold text", result.contains("Texto en negrita"))
        assertTrue("Should preserve italic text", result.contains("texto en cursiva"))
        assertTrue("Should preserve code text", result.contains("codigo inline"))
    }

    @Test
    fun testStripsBulletsAndEmojis() {
        val input = "• 👟 Pasos: 8500\n• 🧘 Estrés: 28/100\n• ❤️ Ritmo: 72 bpm"
        val result = MarkdownUtils.formatCleanPlainTextForGlasses(input)
        assertFalse("Should not contain bullet points", result.contains("•"))
        assertFalse("Should not contain shoe emoji", result.contains("👟"))
        assertFalse("Should not contain meditation emoji", result.contains("🧘"))
        assertFalse("Should not contain heart emoji", result.contains("❤️"))
        assertTrue(result.contains("Pasos: 8500"))
        assertTrue(result.contains("Estrés: 28/100"))
        assertTrue(result.contains("Ritmo: 72 bpm"))
    }

    @Test
    fun testSanitizesUSFormatNumbersAndThousands() {
        val input = "El total es 1,250.50 y la meta es 10,000 pasos en 1,000,000 habitantes."
        val result = MarkdownUtils.formatCleanPlainTextForGlasses(input)
        assertEquals("El total es 1250.50 y la meta es 10000 pasos en 1000000 habitantes.", result)
    }

    @Test
    fun testSanitizesEuropeanFormatNumbersAndThousands() {
        val input = "El premio es de 1.000.000 COP y el costo es 1.250,50 COP."
        val result = MarkdownUtils.formatCleanPlainTextForGlasses(input)
        assertEquals("El premio es de 1000000 COP y el costo es 1250.50 COP.", result)
    }

    @Test
    fun testSanitizesCurrencySymbols() {
        val input = "El precio es $ 4,500.50 COP o € 50 o £ 30."
        val result = MarkdownUtils.formatCleanPlainTextForGlasses(input)
        assertFalse("Should not contain dollar sign", result.contains("$"))
        assertFalse("Should not contain euro sign", result.contains("€"))
        assertFalse("Should not contain pound sign", result.contains("£"))
        assertTrue("Contains cleaned COP number", result.contains("4500.50 COP"))
        assertTrue("Converted EUR symbol", result.contains("50 EUR") || result.contains("EUR 50"))
        assertTrue("Converted GBP symbol", result.contains("30 GBP") || result.contains("GBP 30"))
    }

    @Test
    fun testStripsSystemContextAndSkillTags() {
        val input = "[Contexto del Sistema: Viernes] [SKILL: currency-convert] El dólar está en 4150 COP."
        val result = MarkdownUtils.formatCleanPlainTextForGlasses(input)
        assertFalse("Should not contain system context", result.contains("Contexto del Sistema"))
        assertFalse("Should not contain skill tag", result.contains("SKILL:"))
        assertEquals("El dólar está en 4150 COP.", result)
    }

    @Test
    fun testUnwrapsJsonObject() {
        val input = "{\"answer\": \"Hoy va a llover en Medellín.\", \"status\": 1}"
        val result = MarkdownUtils.formatCleanPlainTextForGlasses(input)
        assertEquals("Hoy va a llover en Medellín.", result)
    }
}
