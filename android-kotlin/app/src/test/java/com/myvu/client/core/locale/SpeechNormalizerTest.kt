package com.myvu.client.core.locale

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechNormalizerTest {

    private fun n(s: String) = SpeechNormalizer.normalize(s)

    @Test
    fun usGroupedNumbersBecomeColombian() {
        assertEquals("Hay 1.250 personas", n("Hay 1,250 personas"))
        assertEquals("Mide 3,5 metros", n("Mide 3.5 metros"))
    }

    @Test
    fun alreadyColombianNumbersAreKept() {
        assertEquals("Hay 1.250 personas y 2,75 litros", n("Hay 1.250 personas y 2,75 litros"))
    }

    @Test
    fun currencyIsSpokenWithCents() {
        assertEquals("La TRM es 4.150 pesos con 32 centavos", n("La TRM es 4,150.32 COP"))
        assertEquals("Cuesta 20 dólares", n("Cuesta USD 20"))
        assertEquals("Cuesta 1 dólar con 5 centavos", n("Cuesta US$ 1.05"))
        assertEquals("Pagué 35.000 pesos", n("Pagué $35.000"))
        assertEquals("Son 10 euros con 50 céntimos", n("Son 10,50 EUR"))
    }

    @Test
    fun timesAreSpokenWithPeriodOfDay() {
        assertEquals("Reunión a las 3:30 de la tarde", n("Reunión a las 15:30"))
        assertEquals("Alarma a las 7 de la mañana", n("Alarma a las 7:00 a. m."))
        assertEquals("Cena a las 8:15 de la noche", n("Cena a las 8:15 pm"))
        assertEquals("Medianoche: 12 de la mañana", n("Medianoche: 00:00"))
    }

    @Test
    fun unitsPercentAndUrls() {
        assertEquals("Lluvia 80 por ciento, 18 grados", n("Lluvia 80%, 18 °C"))
        assertEquals("Viento de 20 kilómetros por hora a 5 kilómetros", n("Viento de 20 km/h a 5 km"))
        assertEquals("Más en enlace", n("Más en https://example.com/a?b=1"))
    }
}
