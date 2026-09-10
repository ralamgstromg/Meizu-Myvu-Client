package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.Prefs
import com.myvu.client.database.TodoRepository
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
class DailyAssistantAutomationTest {

    private lateinit var context: Context
    private lateinit var router: VoiceActionRouter
    private lateinit var executor: PhoneActionExecutor

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        executor = PhoneActionExecutor(context)
        router = VoiceActionRouter(context, executor)
    }

    @Test
    fun testDailyBriefingGeneration() {
        val briefing = DailyBriefingService.generateBriefingText(context)
        assertNotNull(briefing)
        assertTrue(briefing.isNotBlank())
        // Should contain greeting (buenos días / buenas tardes / buenas noches)
        assertTrue(
            briefing.contains("Buenos días") ||
            briefing.contains("Buenas tardes") ||
            briefing.contains("Buenas noches")
        )

        val hud = DailyBriefingService.generateHudBriefing(context)
        assertNotNull(hud)
        assertTrue(hud.isNotBlank())
    }

    @Test
    fun testDailyBriefingRouting() {
        val res1 = router.tryRoute("Buenos días")
        assertTrue(res1.handled)
        assertTrue(res1.responseText.contains("Son las") || res1.responseText.contains("Buenos") || res1.responseText.contains("Buenas"))

        val res2 = router.tryRoute("Mi día")
        assertTrue(res2.handled)

        val res3 = router.tryRoute("Resumen del día")
        assertTrue(res3.handled)

        val res4 = router.tryRoute("Inicia mi día")
        assertTrue(res4.handled)
    }

    @Test
    fun testRoutineManagerModes() {
        // Meeting mode
        val resMeet = RoutineManager.setMeetingMode(context, true)
        assertTrue(resMeet.contains("Modo reunión activado"))
        assertEquals("meeting", Prefs.activeRoutineMode(context))

        val resMeetOff = RoutineManager.setMeetingMode(context, false)
        assertTrue(resMeetOff.contains("Modo reunión finalizado") || resMeetOff.contains("desactivado"))
        assertEquals("none", Prefs.activeRoutineMode(context))

        // Drive mode
        val resDrive = RoutineManager.setDriveMode(context, true)
        assertTrue(resDrive.contains("conducción") || resDrive.contains("activado"))
        assertEquals("drive", Prefs.activeRoutineMode(context))

        RoutineManager.setDriveMode(context, false)
        assertEquals("none", Prefs.activeRoutineMode(context))

        // Gym mode
        val resGym = RoutineManager.setGymMode(context, true)
        assertTrue(resGym.contains("Modo entrenamiento activado") || resGym.contains("activado"))
        assertEquals("gym", Prefs.activeRoutineMode(context))

        RoutineManager.setGymMode(context, false)
        assertEquals("none", Prefs.activeRoutineMode(context))

        // Night mode
        val resNight = RoutineManager.setNightMode(context, true)
        assertTrue(resNight.contains("Modo descanso activado") || resNight.contains("activado"))
        assertEquals("night", Prefs.activeRoutineMode(context))

        RoutineManager.setNightMode(context, false)
        assertEquals("none", Prefs.activeRoutineMode(context))
    }

    @Test
    fun testRoutineRouting() {
        val r1 = router.tryRoute("activa modo reunión")
        assertTrue(r1.handled)
        assertTrue(r1.responseText.contains("Modo reunión activado"))

        val r2 = router.tryRoute("desactiva modo reunión")
        assertTrue(r2.handled)

        val r3 = router.tryRoute("modo auto")
        assertTrue(r3.handled)
        assertTrue(r3.responseText.contains("conducción") || r3.responseText.contains("activado"))

        val r4 = router.tryRoute("modo gym")
        assertTrue(r4.handled)
        assertTrue(r4.responseText.contains("Modo entrenamiento activado") || r4.responseText.contains("activado"))

        val r5 = router.tryRoute("modo noche")
        assertTrue(r5.handled)
        assertTrue(r5.responseText.contains("Modo descanso activado") || r5.responseText.contains("activado"))
    }

    @Test
    fun testSpatialMemory() {
        // Save parking
        SpatialMemoryManager.saveCoordinates(context, 4.6097, -74.0817, "Piso 2 sótano")
        assertEquals(4.6097, Prefs.parkingLatitude(context), 0.0001)
        assertEquals(-74.0817, Prefs.parkingLongitude(context), 0.0001)
        assertEquals("Piso 2 sótano", Prefs.parkingNote(context))

        // Query parking
        val locQuery = SpatialMemoryManager.getParkingLocation(context)
        assertTrue(locQuery.contains("Piso 2 sótano") || locQuery.contains("4.6097"))

        // Cardinal direction
        val (dist, card) = SpatialMemoryManager.calculateDirection(4.6000, -74.0817, 4.6097, -74.0817)
        assertTrue(dist > 0)
        assertTrue(card.contains("Norte"))

        // Clear parking
        SpatialMemoryManager.clearParkingLocation(context)
        val cleared = SpatialMemoryManager.getParkingLocation(context)
        assertTrue(cleared.contains("No tienes ningún estacionamiento"))
    }

    @Test
    fun testSpatialMemoryRouting() {
        val rSave = router.tryRoute("estacioné aquí")
        assertTrue(rSave.handled)

        val rWhere = router.tryRoute("¿Dónde estacioné?")
        assertTrue(rWhere.handled)

        val rClear = router.tryRoute("borra mi estacionamiento")
        assertTrue(rClear.handled)
    }

    @Test
    fun testShoppingListFastPaths() {
        val repo = TodoRepository(context)

        val rAdd = router.tryRoute("agrega leche a las compras")
        assertTrue(rAdd.handled)
        assertTrue(rAdd.responseText.contains("Agregado 'leche' a tu lista de compras"))

        val pending = repo.getPendingTodos("Compras")
        assertTrue(pending.any { it.title.equals("leche", ignoreCase = true) })

        val rQuery = router.tryRoute("lista de compras")
        assertTrue(rQuery.handled)
        assertTrue(rQuery.responseText.contains("leche"))

        val rDone = router.tryRoute("compré leche")
        assertTrue(rDone.handled)
        assertTrue(rDone.responseText.contains("Marcado 'leche' como comprado"))

        val pendingAfter = repo.getPendingTodos("Compras")
        assertFalse(pendingAfter.any { it.title.equals("leche", ignoreCase = true) })
    }

    @Test
    fun testSendLocationRouting() {
        val r = router.tryRoute("mándale mi ubicación a Carlos")
        assertTrue(r.handled)
        assertTrue(r.responseText.contains("Carlos", ignoreCase = true))
    }
}
