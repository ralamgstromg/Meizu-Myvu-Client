package com.myvu.client.plugin.tasker

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskerBundleManagerTest {

    @Test
    fun bundleSerializationForHudMessage() {
        val bundle = TaskerBundleManager.buildHudBundle(
            title = "Aviso Tasker",
            content = "Batería móvil al 100%"
        )
        val action = TaskerBundleManager.parseAction(bundle)
        assertEquals(TaskerConstants.TYPE_SHOW_HUD, action.type)
        assertEquals("Aviso Tasker", action.title)
        assertEquals("Batería móvil al 100%", action.content)

        val blurb = TaskerBundleManager.generateBlurb(action)
        assertEquals("HUD: Aviso Tasker - Batería móvil al 100%", blurb)
        assertEquals(blurb, TaskerBundleManager.generateBlurb(bundle))
    }

    @Test
    fun bundleSerializationForHudWithoutTitle() {
        val bundle = TaskerBundleManager.buildHudBundle(
            title = null,
            content = "Mensaje sin título"
        )
        val action = TaskerBundleManager.parseAction(bundle)
        assertEquals(TaskerConstants.TYPE_SHOW_HUD, action.type)
        assertNull(action.title)
        assertEquals("Mensaje sin título", action.content)

        val blurb = TaskerBundleManager.generateBlurb(action)
        assertEquals("HUD: Mensaje sin título", blurb)
    }

    @Test
    fun bundleSerializationForTeleprompter() {
        val bundle = TaskerBundleManager.buildTeleprompterBundle(
            text = "Texto largo del discurso",
            title = "Discurso 1"
        )
        val action = TaskerBundleManager.parseAction(bundle)
        assertEquals(TaskerConstants.TYPE_SHOW_TELEPROMPTER, action.type)
        assertEquals("Discurso 1", action.title)
        assertEquals("Texto largo del discurso", action.content)

        val blurb = TaskerBundleManager.generateBlurb(action)
        assertEquals("Teleprompter: Discurso 1 - Texto largo del discurso", blurb)
    }

    @Test
    fun bundleSerializationForBrightnessAndVolume() {
        val brightnessBundle = TaskerBundleManager.buildBrightnessBundle(7)
        val brightnessAction = TaskerBundleManager.parseAction(brightnessBundle)
        assertEquals(TaskerConstants.TYPE_SET_BRIGHTNESS, brightnessAction.type)
        assertEquals(7, brightnessAction.valueInt)
        assertEquals("Brillo: 7", TaskerBundleManager.generateBlurb(brightnessAction))

        val volumeBundle = TaskerBundleManager.buildVolumeBundle(12)
        val volumeAction = TaskerBundleManager.parseAction(volumeBundle)
        assertEquals(TaskerConstants.TYPE_SET_VOLUME, volumeAction.type)
        assertEquals(12, volumeAction.valueInt)
        assertEquals("Volumen: 12", TaskerBundleManager.generateBlurb(volumeAction))
    }

    @Test
    fun bundleSerializationForToggles() {
        val wifiBundle = TaskerBundleManager.buildWifiBundle(true)
        val wifiAction = TaskerBundleManager.parseAction(wifiBundle)
        assertEquals(TaskerConstants.TYPE_TOGGLE_WIFI, wifiAction.type)
        assertEquals(true, wifiAction.valueBoolean)
        assertEquals("WiFi: Activado", TaskerBundleManager.generateBlurb(wifiAction))

        val zenBundle = TaskerBundleManager.buildZenModeBundle(false)
        val zenAction = TaskerBundleManager.parseAction(zenBundle)
        assertEquals(TaskerConstants.TYPE_SET_ZEN_MODE, zenAction.type)
        assertEquals(false, zenAction.valueBoolean)
        assertEquals("Modo Zen: Desactivado", TaskerBundleManager.generateBlurb(zenAction))

        val airBundle = TaskerBundleManager.buildAirModeBundle(true)
        val airAction = TaskerBundleManager.parseAction(airBundle)
        assertEquals(TaskerConstants.TYPE_SET_AIR_MODE, airAction.type)
        assertEquals(true, airAction.valueBoolean)
        assertEquals("Modo Air: Activado", TaskerBundleManager.generateBlurb(airAction))
    }

    @Test
    fun bundleSerializationForStandbyPosAndRawJson() {
        val posBundle = TaskerBundleManager.buildStandbyPosBundle(2)
        val posAction = TaskerBundleManager.parseAction(posBundle)
        assertEquals(TaskerConstants.TYPE_SET_STANDBY_POS, posAction.type)
        assertEquals(2, posAction.valueInt)
        assertEquals("Posición Standby: 2", TaskerBundleManager.generateBlurb(posAction))

        val rawBundle = TaskerBundleManager.buildRawJsonBundle("{\"action\":\"test\"}")
        val rawAction = TaskerBundleManager.parseAction(rawBundle)
        assertEquals(TaskerConstants.TYPE_SEND_RAW, rawAction.type)
        assertEquals("{\"action\":\"test\"}", rawAction.rawJson)
        assertEquals("Raw JSON: {\"action\":\"test\"}", TaskerBundleManager.generateBlurb(rawAction))
    }

    @Test
    fun extractActionHandlesNullOrEmptyBundle() {
        assertNull(TaskerBundleManager.extractAction(null))
        assertNull(TaskerBundleManager.extractAction(Bundle()))

        val emptyParsed = TaskerBundleManager.parseAction(null)
        assertEquals("", emptyParsed.type)
    }

    @Test
    fun eventSerializationAndBlurb() {
        val gestureBundle = TaskerBundleManager.buildGestureEventBundle(3, "Pulsación Larga")
        val gestureEvent = TaskerBundleManager.parseEvent(gestureBundle)
        assertEquals(TaskerConstants.EVENT_TOUCH_GESTURE, gestureEvent.eventType)
        assertEquals(3, gestureEvent.gestureCode)
        assertEquals("Pulsación Larga", gestureEvent.gestureName)
        assertEquals("Gesto Táctil: Pulsación Larga", TaskerBundleManager.generateEventBlurb(gestureEvent))
        assertEquals("Gesto Táctil: Pulsación Larga", TaskerBundleManager.generateEventBlurb(gestureBundle))

        val aiBundle = TaskerBundleManager.buildAiButtonEventBundle(3)
        val aiEvent = TaskerBundleManager.parseEvent(aiBundle)
        assertEquals(TaskerConstants.EVENT_AI_BUTTON, aiEvent.eventType)
        assertEquals(3, aiEvent.buttonCode)
        assertEquals("Botón AI (Código: 3)", TaskerBundleManager.generateEventBlurb(aiEvent))

        val batteryBundle = TaskerBundleManager.buildBatteryEventBundle(85, true)
        val batteryEvent = TaskerBundleManager.parseEvent(batteryBundle)
        assertEquals(TaskerConstants.EVENT_BATTERY_CHANGED, batteryEvent.eventType)
        assertEquals(85, batteryEvent.batteryLevel)
        assertEquals(true, batteryEvent.isCharging)
        assertEquals("Batería: 85% (Cargando)", TaskerBundleManager.generateEventBlurb(batteryEvent))

        val connBundle = TaskerBundleManager.buildConnectionEventBundle(true)
        val connEvent = TaskerBundleManager.parseEvent(connBundle)
        assertEquals(TaskerConstants.EVENT_CONNECTED, connEvent.eventType)
        assertEquals("CONNECTED", connEvent.connectionState)
        assertEquals("Gafas Conectadas", TaskerBundleManager.generateEventBlurb(connEvent))

        val disconnBundle = TaskerBundleManager.buildConnectionEventBundle(false)
        val disconnEvent = TaskerBundleManager.parseEvent(disconnBundle)
        assertEquals(TaskerConstants.EVENT_DISCONNECTED, disconnEvent.eventType)
        assertEquals("DISCONNECTED", disconnEvent.connectionState)
        assertEquals("Gafas Desconectadas", TaskerBundleManager.generateEventBlurb(disconnEvent))
    }

    @Test
    fun extractEventHandlesNullOrEmptyBundle() {
        assertNull(TaskerBundleManager.extractEvent(null))
        assertNull(TaskerBundleManager.extractEvent(Bundle()))

        val emptyEvent = TaskerBundleManager.parseEvent(null)
        assertEquals("", emptyEvent.eventType)
    }

    @Test
    fun taskerVariableExtractionAndResolution() {
        val text = "Alerta de %sender: mensaje %message_text recibido con código %code_1"
        assertTrue(TaskerBundleManager.containsVariables(text))
        assertFalse(TaskerBundleManager.containsVariables("Texto sin variables"))

        val vars = TaskerBundleManager.extractVariables(text)
        assertEquals(listOf("%sender", "%message_text", "%code_1"), vars)

        val varsMap = mapOf(
            "%sender" to "Alice",
            "%message_text" to "Hola Mundo",
            "%code_1" to "200"
        )
        val resolved = TaskerBundleManager.applyVariables(text, varsMap)
        assertEquals("Alerta de Alice: mensaje Hola Mundo recibido con código 200", resolved)

        val action = TaskerAction(
            type = TaskerConstants.TYPE_SHOW_HUD,
            title = "De: %sender",
            content = "Mensaje: %message_text",
            rawJson = "{\"sender\":\"%sender\"}"
        )
        val resolvedAction = TaskerBundleManager.resolveActionVariables(action, varsMap)
        assertEquals("De: Alice", resolvedAction.title)
        assertEquals("Mensaje: Hola Mundo", resolvedAction.content)
        assertEquals("{\"sender\":\"Alice\"}", resolvedAction.rawJson)
    }

    @Test
    fun variableReplaceKeysConstant() {
        val keys = TaskerBundleManager.getVariableReplaceKeys()
        assertTrue(keys.contains(TaskerConstants.KEY_TITLE))
        assertTrue(keys.contains(TaskerConstants.KEY_CONTENT))
        assertTrue(keys.contains(TaskerConstants.KEY_RAW_JSON))
    }
}
