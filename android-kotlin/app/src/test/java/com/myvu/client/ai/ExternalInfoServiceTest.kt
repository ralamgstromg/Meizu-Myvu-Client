package com.myvu.client.ai

import com.myvu.client.app.feature.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalInfoServiceTest {

    @Test
    fun testDetectExternalQueryTypes() {
        assertTrue(ExternalInfoService.isWeatherQuery("qué temperatura hay en Barranquilla"))
        assertTrue(ExternalInfoService.isWeatherQuery("clima en Bogotá mañana"))
        assertTrue(ExternalInfoService.isWeatherQuery("cómo está el tiempo en Medellín"))
        assertTrue(ExternalInfoService.isWeatherQuery("pronóstico para Madrid"))
        assertTrue(ExternalInfoService.isWeatherQuery("va a llover hoy"))

        assertTrue(ExternalInfoService.isCurrencyQuery("cuánto está el dólar en pesos colombianos"))
        assertTrue(ExternalInfoService.isCurrencyQuery("precio del euro a cop"))
        assertTrue(ExternalInfoService.isCurrencyQuery("a cómo está el dólar"))
        assertTrue(ExternalInfoService.isCurrencyQuery("convertir 100 dólares a euros"))
        assertTrue(ExternalInfoService.isCurrencyQuery("tasa de cambio usd a cop"))
        assertTrue(ExternalInfoService.isCurrencyQuery("a cómo está la TRM hoy"))
        assertTrue(ExternalInfoService.isCurrencyQuery("precio del dólar hoy en colombia"))

        assertTrue(ExternalInfoService.isNewsQuery("noticias el día de hoy en colombia"))
        assertTrue(ExternalInfoService.isNewsQuery("solicite informacion de noticias el dia de hoy en colombia"))
        assertTrue(ExternalInfoService.isNewsQuery("dame las últimas noticias"))
        assertTrue(ExternalInfoService.isNewsQuery("qué pasa en Medellín hoy"))
        assertTrue(ExternalInfoService.isNewsQuery("titulares de hoy"))

        assertTrue(ExternalInfoService.isStockOrMarketQuery("cómo están las acciones de Apple"))
        assertTrue(ExternalInfoService.isStockOrMarketQuery("precio de las acciones de Tesla"))
        assertTrue(ExternalInfoService.isStockOrMarketQuery("precio de Bitcoin hoy"))
        assertTrue(ExternalInfoService.isStockOrMarketQuery("a cómo están las acciones de Ecopetrol"))
        assertTrue(ExternalInfoService.isStockOrMarketQuery("cómo va la bolsa hoy"))

        assertTrue(ExternalInfoService.isGeneralSearchQuery("busca en google quién descubrió América"))
        assertTrue(ExternalInfoService.isGeneralSearchQuery("buscar la capital de Australia"))
        assertTrue(ExternalInfoService.isGeneralSearchQuery("google quién es Elon Musk"))
        assertTrue(ExternalInfoService.isGeneralSearchQuery("quién fue Albert Einstein"))
        assertTrue(ExternalInfoService.isGeneralSearchQuery("qué es la computación cuántica"))
        assertTrue(ExternalInfoService.isGeneralSearchQuery("En google cuál es la manera más rápida de buscar un ítem en una array de python."))
        assertTrue(ExternalInfoService.isGeneralSearchQuery("en google cómo se busca un valor en una lista"))
        assertTrue(ExternalInfoService.isGeneralSearchQuery("cuál es la manera más rápida de ordenar una lista"))
        assertTrue(ExternalInfoService.isGeneralSearchQuery("cómo buscar un archivo en linux"))
        assertTrue(ExternalInfoService.isGeneralSearchQuery("cómo programar una api en kotlin"))
    }

    @Test
    fun testFormatCurrencyAnswer() {
        val answer = ExternalInfoService.formatCurrencyResult(1.0, "USD", 4150.0, "COP")
        assertTrue(answer.contains("1 USD equivale a 4150 COP") || answer.contains("4150"))
        assertFalse(answer.contains("*"))
        assertFalse(answer.contains("#"))

        val answer2 = ExternalInfoService.formatCurrencyResult(50.0, "EUR", 54.5, "USD")
        assertTrue(answer2.contains("50 EUR equivale a 54.5 USD") || answer2.contains("54.5"))
    }

    @Test
    fun testExtractCityFromWeatherQuery() {
        assertEquals("Barranquilla", ExternalInfoService.extractCityFromWeatherQuery("qué temperatura hay en Barranquilla"))
        assertEquals("Bogotá", ExternalInfoService.extractCityFromWeatherQuery("clima en Bogotá mañana"))
        assertEquals("Medellín", ExternalInfoService.extractCityFromWeatherQuery("cómo está el tiempo en Medellín"))
        assertEquals("Madrid", ExternalInfoService.extractCityFromWeatherQuery("pronóstico para Madrid"))
        assertEquals("Buenos Aires", ExternalInfoService.extractCityFromWeatherQuery("temperatura en Buenos Aires hoy"))
        assertNull(ExternalInfoService.extractCityFromWeatherQuery("cómo está el clima hoy"))
    }

    @Test
    fun testExtractCurrencyRequest() {
        val req1 = ExternalInfoService.extractCurrencyRequest("cuánto está el dólar en pesos colombianos")
        assertNotNull(req1)
        assertEquals("USD", req1!!.from)
        assertEquals("COP", req1.to)
        assertEquals(1.0, req1.amount, 0.001)

        val req2 = ExternalInfoService.extractCurrencyRequest("precio del euro a cop")
        assertNotNull(req2)
        assertEquals("EUR", req2!!.from)
        assertEquals("COP", req2.to)

        val req3 = ExternalInfoService.extractCurrencyRequest("convertir 100 dólares a euros")
        assertNotNull(req3)
        assertEquals(100.0, req3!!.amount, 0.001)
        assertEquals("USD", req3.from)
        assertEquals("EUR", req3.to)

        val reqTrm = ExternalInfoService.extractCurrencyRequest("a cómo está la TRM hoy")
        assertNotNull(reqTrm)
        assertEquals("USD", reqTrm!!.from)
        assertEquals("COP", reqTrm.to)
    }

    @Test
    fun testCleanForGlasses() {
        val rawHtml = "<b>Barranquilla</b> tiene una temperatura actual de <b>32°C</b>. <a href='...'>Ver más</a>"
        val clean = ExternalInfoService.cleanForGlasses(rawHtml)
        assertEquals("Barranquilla tiene una temperatura actual de 32°C. Ver más", clean)

        val markdownAndEmojis = "☀️ El clima está **despejado** y soleado! #Clima"
        val cleanMd = ExternalInfoService.cleanForGlasses(markdownAndEmojis)
        assertFalse(cleanMd.contains("☀️"))
        assertFalse(cleanMd.contains("**"))
        assertFalse(cleanMd.contains("#"))
    }

    @Test
    fun testFormatWeatherResult() {
        val reading = Weather.Reading().apply {
            areaName = "Barranquilla"
            temp = 31
            condition = "Cielo despejado"
            dayTempMax = 33
            dayTempMin = 26
        }
        val text = ExternalInfoService.formatWeatherResult(reading)
        assertTrue(text.contains("31°C"))
        assertTrue(text.contains("Barranquilla"))
        assertTrue(text.contains("despejado") || text.contains("Cielo despejado"))
    }

    @Test
    fun testGeocodeCache() {
        com.myvu.client.weather.OpenMeteo.clearGeocodeCache()
        // Test that blank or invalid query returns null safely
        val blankResult = com.myvu.client.weather.OpenMeteo.geocode("   ")
        org.junit.Assert.assertNull(blankResult)
    }

    @Test
    fun testNewsSearchExtraction() {
        val result = ExternalInfoService.fetchNewsSearch("Noticias relevantes hay en Barranquilla el día de hoy.")
        if (result != null) {
            assertTrue("Debería incluir Barranquilla en la etiqueta de noticias", result.contains("Barranquilla"))
            assertFalse("No debería incluir palabras de relleno en la etiqueta", result.contains("relevantes hay"))
        }

        val resultWithAccent = ExternalInfoService.fetchNewsSearch("¿Qué noticias relevantes hay hoy en Barranquilla?")
        if (resultWithAccent != null) {
            assertTrue("Debería incluir Barranquilla en la etiqueta", resultWithAccent.contains("Barranquilla"))
            assertFalse("No debería incluir el prefijo interrogativo", resultWithAccent.contains("¿Qué noticias"))
            assertFalse("No debería incluir palabras de relleno", resultWithAccent.contains("relevantes hay"))
        }
    }

    @Test
    fun testCurrencyQueryExcludesDefinitionsAndSubstrings() {
        // Must NOT match "eur" inside "neuronales"
        assertFalse(
            ExternalInfoService.isCurrencyQuery("¿Cuál es el significado de la palabra retropropagación en redes neuronales?")
        )
        assertFalse(ExternalInfoService.isCurrencyQuery("estudios de neurociencia en Europa"))
        assertFalse(ExternalInfoService.isCurrencyQuery("campo de girasoles"))
        assertFalse(ExternalInfoService.isCurrencyQuery("microprocesador acoplado"))

        // Must be recognized as general/definition search instead
        assertTrue(
            ExternalInfoService.isGeneralSearchQuery("¿Cuál es el significado de la palabra retropropagación en redes neuronales?")
        )

        // Real currency queries must still match
        assertTrue(ExternalInfoService.isCurrencyQuery("a cómo está el euro"))
        assertTrue(ExternalInfoService.isCurrencyQuery("¿Cuál es el TRM de hoy?"))
        assertTrue(ExternalInfoService.isCurrencyQuery("precio del dólar hoy"))
    }
}
