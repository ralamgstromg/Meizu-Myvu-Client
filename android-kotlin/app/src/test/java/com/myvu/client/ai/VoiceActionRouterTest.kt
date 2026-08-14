package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActionRouterTest {

    @Test
    fun testFastPathPatternParsing() {
        val raw = "Llamar a Matías Castro"
        val nfd = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
        val normalized = nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase()
        val callMatch = Regex("^(llamar?|marcar?|marca|llama|call)\\s+(a|al)?\\s*(.+)$", RegexOption.IGNORE_CASE).find(normalized)

        assertTrue(callMatch != null)
        val contactTarget = raw.substring(callMatch!!.groups[3]!!.range.first).trim()
        assertEquals("Matías Castro", contactTarget)
    }

    @Test
    fun testWhatsAppPatternParsing() {
        val query = "enviar whatsapp a carlos hola llego en 5"
        val nfd = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD)
        val normalized = nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase()
        val waMatch = Regex("^(enviar?|manda|mandar?|escribir?)\\s+(un\\s+)?(whatsapp|mensaje)\\s+(a|al)?\\s*(.+)$", RegexOption.IGNORE_CASE).find(normalized)

        assertTrue(waMatch != null)
        val payload = query.substring(waMatch!!.groups[5]!!.range.first).trim()
        assertEquals("carlos hola llego en 5", payload)
    }

    @Test
    fun testPhoneticCallPatternParsing() {
        val raw = "Jamar a Matías Castro"
        val nfd = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
        val normalized = nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase()
        val callMatch = Regex("^(llamar?|marcar?|marca|llama|call|jamar?|yamar?|llamas|llamame)\\s+(a|al|a\\s+mi)?\\s*(.+)$", RegexOption.IGNORE_CASE).find(normalized)

        assertTrue(callMatch != null)
        val rawTarget = raw.substring(callMatch!!.groups[3]!!.range.first).trim()
        val clean = rawTarget.replace(Regex("(?i)^(a|al|a\\s+mi|el|la|las|los)\\s+"), "").trim()
        assertEquals("Matías Castro", clean)
    }

    @Test
    fun testParaWhatsAppParsing() {
        val query = "para Matías Castro: hola cómo estás"
        val nfd = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD)
        val normalized = nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase()
        assertTrue(normalized.startsWith("para ") && !normalized.matches(Regex("^para\\s+(las?\\s+)?[0-9].*")))
        val targetAndMsg = query.replace(Regex("(?i)^para\\s+"), "").trim()
        assertEquals("Matías Castro: hola cómo estás", targetAndMsg)
    }
}
