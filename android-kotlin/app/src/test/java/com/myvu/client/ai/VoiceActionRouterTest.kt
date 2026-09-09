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

    @Test
    fun testCalendarFastPathVariations() {
        val q1 = router.tryRoute("¿Tengo alguna reunión hoy por la tarde?")
        assertTrue("Debería capturar reunión en singular", q1.handled)

        val q2 = router.tryRoute("Tengo alguna reunión el día de hoy.")
        assertTrue("Debería capturar reunión sin signo de interrogación", q2.handled)

        val q3 = router.tryRoute("¿Qué citas tengo hoy?")
        assertTrue("Debería capturar citas", q3.handled)

        val q4 = router.tryRoute("¿Tengo compromisos pendientes?")
        assertTrue("Debería capturar compromisos", q4.handled)

        val q5 = router.tryRoute("¿Qué tengo para hoy?")
        assertTrue("Debería capturar qué tengo para hoy", q5.handled)
    }

    @Test
    fun testFastPathSmsRouting() {
        val res1 = router.tryRoute("Enviar un SMS a Matías que ya voy llegando")
        assertTrue(res1.handled)
        assertTrue(res1.responseText.contains("mensaje de texto"))

        val res2 = router.tryRoute("Manda mensaje de texto a Carlos: llego tarde")
        assertTrue(res2.handled)
        assertTrue(res2.responseText.contains("mensaje de texto"))

        val res3 = router.tryRoute("Escribe un texto a Matias Castro")
        assertTrue(res3.handled)
        assertTrue(res3.responseText.contains("mensaje de texto"))
    }

    @Test
    fun testFastPathNotificationRouting() {
        val res1 = router.tryRoute("Notificaciones tengo pendientes por leer.")
        assertTrue(res1.handled)

        val res2 = router.tryRoute("¿Qué notificaciones tengo?")
        assertTrue(res2.handled)

        val res3 = router.tryRoute("Tengo notificaciones pendientes")
        assertTrue(res3.handled)

        val res4 = router.tryRoute("Revisa mis notificaciones de whatsapp")
        assertTrue(res4.handled)
    }

    @Test
    fun testFastPathMediaControls() {
        val pause = router.tryRoute("pausa la música")
        assertTrue(pause.handled)
        assertEquals("Música pausada.", pause.responseText)

        val play = router.tryRoute("reproduce música")
        assertTrue(play.handled)
        assertEquals("Reproduciendo música.", play.responseText)

        val next = router.tryRoute("siguiente canción")
        assertTrue(next.handled)
        assertEquals("Siguiente canción.", next.responseText)

        val prev = router.tryRoute("canción anterior")
        assertTrue(prev.handled)
        assertEquals("Canción anterior.", prev.responseText)
    }

    @Test
    fun testFastPathFlashlightAndVolumeControls() {
        val torchOn = router.tryRoute("enciende la linterna")
        assertTrue(torchOn.handled)

        val torchOff = router.tryRoute("apaga la linterna")
        assertTrue(torchOff.handled)

        val volUp = router.tryRoute("sube el volumen")
        assertTrue(volUp.handled)

        val volDown = router.tryRoute("baja el volumen")
        assertTrue(volDown.handled)

        val silent = router.tryRoute("silencia el teléfono")
        assertTrue(silent.handled)

        val vibrate = router.tryRoute("pon en vibración")
        assertTrue(vibrate.handled)

        val normal = router.tryRoute("activa el sonido")
        assertTrue(normal.handled)
    }

    @Test
    fun testFastPathOpenApp() {
        val openSpotify = router.tryRoute("abre Spotify")
        assertTrue(openSpotify.handled)
        assertTrue(openSpotify.responseText.contains("Spotify", ignoreCase = true))

        val openMaps = router.tryRoute("abre Maps")
        assertTrue(openMaps.handled)
        assertTrue(openMaps.responseText.contains("Maps", ignoreCase = true))
    }

    @Test
    fun testFastPathVoipCalls() {
        val waCall = router.tryRoute("Llama a Matías Castro por WhatsApp")
        assertTrue(waCall.handled)
        assertTrue(waCall.responseText.contains("WhatsApp", ignoreCase = true))
        assertTrue(waCall.responseText.contains("Matías Castro", ignoreCase = true))

        // Con punto final de Whisper STT
        val waCallWithDot = router.tryRoute("Llama por WhatsApp a Matías Castro.")
        assertTrue(waCallWithDot.handled)
        assertTrue(waCallWithDot.responseText.contains("WhatsApp", ignoreCase = true))
        // El punto no debe quedar en el nombre del destinatario
        assertEquals("Llamando a Matías Castro por WhatsApp...", waCallWithDot.responseText)

        // Variación fonética de Whisper STT ("Anomar")
        val waCallAnomar = router.tryRoute("Anomar por WhatsApp a Matías Castro.")
        assertTrue(waCallAnomar.handled)
        assertEquals("Llamando a Matías Castro por WhatsApp...", waCallAnomar.responseText)

        val teamsCall = router.tryRoute("Inicia llamada de Teams con Carlos Gómez")
        assertTrue(teamsCall.handled)
        assertTrue(teamsCall.responseText.contains("Teams", ignoreCase = true))
        assertTrue(teamsCall.responseText.contains("Carlos Gómez", ignoreCase = true))

        val gchatCall = router.tryRoute("Llama a Pedro por Google Chat")
        assertTrue(gchatCall.handled)
        assertTrue(gchatCall.responseText.contains("Google Chat", ignoreCase = true))
        assertTrue(gchatCall.responseText.contains("Pedro", ignoreCase = true))
    }

    @Test
    fun testFastPathHealthQueries() {
        val steps = router.tryRoute("¿Cuántos pasos llevo hoy?")
        assertTrue(steps.handled)
        assertTrue(steps.responseText.contains("pasos", ignoreCase = true))

        val stress = router.tryRoute("¿Cuál es mi nivel de estrés?")
        assertTrue(stress.handled)
        assertTrue(stress.responseText.contains("estrés", ignoreCase = true))

        val hr = router.tryRoute("ritmo cardíaco")
        assertTrue(hr.handled)
        assertTrue(hr.responseText.contains("cardíaca", ignoreCase = true) || hr.responseText.contains("bpm", ignoreCase = true))

        val full = router.tryRoute("resumen de salud")
        assertTrue(full.handled)
        assertTrue(full.responseText.contains("Salud", ignoreCase = true))
    }
}
