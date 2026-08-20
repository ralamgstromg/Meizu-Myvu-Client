package com.myvu.client.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandaloneFeaturesTest {

    @Test
    fun testExternalInfoServiceWeatherQueryClassification() {
        assertTrue(ExternalInfoService.isWeatherQuery("Clima en Bogotá"))
        assertTrue(ExternalInfoService.isWeatherQuery("¿Cómo está el tiempo hoy?"))
        assertFalse(ExternalInfoService.isWeatherQuery("Llamar a Juan Perez"))
    }

    @Test
    fun testExternalInfoServiceCurrencyQueryClassification() {
        assertTrue(ExternalInfoService.isCurrencyQuery("Convertir 100 dólares a euros"))
        assertTrue(ExternalInfoService.isCurrencyQuery("Precio del dólar hoy"))
        assertFalse(ExternalInfoService.isCurrencyQuery("Abre Spotify"))
    }
}
