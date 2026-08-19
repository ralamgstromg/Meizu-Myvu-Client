package com.myvu.client.ai

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class VoiceActionRouterTest {

    private lateinit var context: Context
    private lateinit var router: VoiceActionRouter

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val executor = PhoneActionExecutor(context)
        router = VoiceActionRouter(context, executor)
    }

    @Test
    fun testFastPathWeatherWithCity() {
        val res1 = router.tryRoute("¿Qué temperatura era mañana en Barranquilla?")
        assertTrue(res1.handled)
        assertTrue(res1.isAsyncExternalSearch)
        assertFalse(res1.isAsyncWeather)

        val res2 = router.tryRoute("Clima en Bogotá")
        assertTrue(res2.handled)
        assertTrue(res2.isAsyncExternalSearch)

        val res3 = router.tryRoute("cómo está el tiempo en Medellín")
        assertTrue(res3.handled)
        assertTrue(res3.isAsyncExternalSearch)
    }

    @Test
    fun testFastPathWeatherGeneralWithoutCity() {
        val res1 = router.tryRoute("cómo está el clima")
        assertTrue(res1.handled)
        assertTrue(res1.isAsyncWeather)
        assertFalse(res1.isAsyncExternalSearch)

        val res2 = router.tryRoute("temperatura hoy")
        assertTrue(res2.handled)
        assertTrue(res2.isAsyncWeather)
        assertFalse(res2.isAsyncExternalSearch)
    }

    @Test
    fun testFastPathCurrencyQueries() {
        val res1 = router.tryRoute("¿A cómo está el dólar hoy?")
        assertTrue(res1.handled)
        assertTrue(res1.isAsyncExternalSearch)

        val res2 = router.tryRoute("Precio del euro a cop")
        assertTrue(res2.handled)
        assertTrue(res2.isAsyncExternalSearch)

        val res3 = router.tryRoute("Convertir 100 dólares a euros")
        assertTrue(res3.handled)
        assertTrue(res3.isAsyncExternalSearch)
    }

    @Test
    fun testFastPathGeneralSearchQueries() {
        val res1 = router.tryRoute("Busca en google las últimas noticias de tecnología")
        assertTrue(res1.handled)
        assertTrue(res1.isAsyncExternalSearch)

        val res2 = router.tryRoute("Buscar la capital de Australia")
        assertTrue(res2.handled)
        assertTrue(res2.isAsyncExternalSearch)

        val res3 = router.tryRoute("Quién es Marie Curie")
        assertTrue(res3.handled)
        assertTrue(res3.isAsyncExternalSearch)

        val res4 = router.tryRoute("Qué es la fotosíntesis")
        assertTrue(res4.handled)
        assertTrue(res4.isAsyncExternalSearch)
    }

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
    fun testWhatsAppNaturalCommaParsing() {
        val query = "Enviar mensaje de whatsapp a Matías Castro, hola cómo vas."
        val nfd = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD)
        val normalized = nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase()
        val waMatch = Regex("^(enviar?|manda|mandar?|escribir?|mensaje\\s+para|para)\\s+(un\\s+)?(mensaje\\s+de\\s+whatsapp|whatsapp|mensaje)?\\s*(a|al|a\\s+mi|para)?\\s*(.+)$", RegexOption.IGNORE_CASE).find(normalized)

        assertTrue(waMatch != null)
        val payload = query.substring(waMatch!!.groups[5]!!.range.first).trim()
        assertEquals("Matías Castro, hola cómo vas.", payload)

        val parts = payload.split(Regex(","), 2)
        assertEquals("Matías Castro", parts[0].trim())
        assertEquals("hola cómo vas.", parts[1].trim())
    }
}
