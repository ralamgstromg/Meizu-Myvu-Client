package com.myvu.client.service

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDigestTest {

    @Test
    fun limitsWordsAndAddsEllipsis() {
        assertEquals("uno dos tres…", NotificationDigest.limitWords("uno dos tres, cuatro cinco", 3))
        assertEquals("uno dos", NotificationDigest.limitWords("  uno   dos ", 3))
    }

    @Test
    fun spokenNamesSenderAndAppAndStripsEmoji() {
        assertEquals(
            "Mensaje de Ana en WhatsApp: llego tarde a la…",
            NotificationDigest.spoken("WhatsApp", "Ana 😀", "llego tarde a la reunión de hoy", 4)
        )
        assertEquals("Notificación de Gmail: Factura lista", NotificationDigest.spoken("Gmail", null, "Factura lista", 20))
        assertEquals("Nueva notificación de Banco", NotificationDigest.spoken("Banco", "", "", 20))
    }

    @Test
    fun hudFallsBackToAppNameAsTitle() {
        assertEquals("Gmail" to "Factura lista", NotificationDigest.hud("Gmail", null, "Factura lista", 20))
    }

    @Test
    fun groupTitlesNameSenderAndGroup() {
        assertEquals(
            "Mensaje de Softia en el grupo MovilPlata soft de WhatsApp: Todo bn",
            NotificationDigest.spoken("WhatsApp", "MovilPlata soft: ~ Softia", "Todo bn", 20)
        )
        assertEquals("Mensaje de Ana en WhatsApp: hola", NotificationDigest.spoken("WhatsApp", "~ Ana", "hola", 20))
        // Non-messaging apps keep "X: Y" titles as they are.
        assertEquals("Mensaje de Banco: Alerta en Bancolombia: Compra aprobada", NotificationDigest.spoken("Bancolombia", "Banco: Alerta", "Compra aprobada", 20))
    }
}
