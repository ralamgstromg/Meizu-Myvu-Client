package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.Prefs
import com.myvu.client.core.TotalDisconnectHelper
import com.myvu.client.database.Note
import com.myvu.client.database.NoteRepository
import com.myvu.client.database.Reminder
import com.myvu.client.database.ReminderRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StandaloneAiAndServiceToggleTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun testServiceMasterSwitchStateToggling() {
        // Initial state: enable service reconnect
        Prefs.setAutoReconnectEnabled(context, true)
        assertTrue(Prefs.autoReconnectEnabled(context))

        // Total disconnect (switch turned OFF)
        TotalDisconnectHelper.performTotalDisconnect(context, showToast = false)
        assertFalse(Prefs.autoReconnectEnabled(context))

        // User turns switch back ON
        Prefs.setAutoReconnectEnabled(context, true)
        assertTrue(Prefs.autoReconnectEnabled(context))
    }

    @Test
    fun testDailyBriefingServiceWorksStandaloneWithoutConnectedDevices() {
        val briefing = DailyBriefingService.generateBriefingText(context)
        assertNotNull(briefing)
        assertTrue("Briefing should not be empty", briefing.isNotBlank())
        assertTrue("Briefing should contain a greeting", briefing.contains("Buenos") || briefing.contains("Buenas"))
    }

    @Test
    fun testNotesAndRemindersWorkStandalone() {
        val noteRepo = NoteRepository(context)
        val testNote = Note(
            title = "Prueba Standalone AI",
            body = "Nota creada sin dispositivos conectados",
            tags = "Ideas"
        )
        val noteId = noteRepo.insert(testNote)
        assertTrue(noteId > 0)
        val loadedNotes = noteRepo.getAll()
        assertTrue(loadedNotes.any { it.title == "Prueba Standalone AI" })

        val reminderRepo = ReminderRepository(context)
        val testReminder = Reminder(
            title = "Recordatorio Standalone",
            triggerAt = System.currentTimeMillis() + 3600000L
        )
        val remId = reminderRepo.insert(testReminder)
        assertTrue(remId > 0)
        val loadedReminders = reminderRepo.getAll()
        assertTrue(loadedReminders.any { it.title == "Recordatorio Standalone" })
    }
}
