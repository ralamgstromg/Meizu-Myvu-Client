package com.myvu.client.routines

import com.myvu.client.database.TodoRepository
import com.myvu.client.skills.handlers.ScheduleRoutineHandler
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutinesTest {

    private val tz = TimeZone.getTimeZone("America/Bogota")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Calendar =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, h, min) }

    private fun routine(hour: Int, minute: Int, days: Set<Int>) = ScheduledRoutine(
        "r1", "Test", hour, minute, days, setOf(RoutineAction.TASKS), setOf(RoutineChannel.NOTIFICATION)
    )

    @Test
    fun nextTriggerIsLaterTodayOrNextSelectedDay() {
        // Friday 2026-10-02 06:00 Bogotá
        val now = at(2026, 10, 2, 6, 0)
        val r = routine(7, 0, ScheduledRoutine.WEEKDAYS)
        assertEquals(at(2026, 10, 2, 7, 0).timeInMillis, r.nextTrigger(now.timeInMillis, now))

        // Friday 08:00: next weekday run is Monday 2026-10-05 07:00
        val later = at(2026, 10, 2, 8, 0)
        assertEquals(at(2026, 10, 5, 7, 0).timeInMillis, r.nextTrigger(later.timeInMillis, later))

        assertNull(routine(7, 0, emptySet()).nextTrigger(now.timeInMillis, now))
    }

    @Test
    fun storeRoundTripsRoutines() {
        val ctx = RuntimeEnvironment.getApplication()
        val r = ScheduledRoutine.templates().first()
        RoutineStore.save(ctx, r)
        assertEquals(r, RoutineStore.get(ctx, r.id))
        RoutineStore.delete(ctx, r.id)
        assertNull(RoutineStore.get(ctx, r.id))
    }

    @Test
    fun composeListsPendingTasks() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        TodoRepository(ctx).createTodo("Pagar la luz")
        val text = RoutineRunner.compose(ctx, setOf(RoutineAction.TASKS))
        assertTrue(text, text.contains("Pagar la luz"))
    }

    @Test
    fun scheduleRoutineSkillParsesSpanishRequest() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val result = ScheduleRoutineHandler().execute(
            ctx,
            JSONObject().put("name", "Buenos días").put("time", "07:30").put("days", "lunes a viernes")
                .put("actions", "agenda, TRM y clima").put("channels", "voz y gafas")
        )
        assertTrue(result.message, result.success)
        val saved = RoutineStore.all(ctx).single { it.name == "Buenos días" }
        assertEquals(7, saved.hour)
        assertEquals(30, saved.minute)
        assertEquals(ScheduledRoutine.WEEKDAYS, saved.days)
        assertEquals(setOf(RoutineAction.AGENDA, RoutineAction.TRM, RoutineAction.WEATHER), saved.actions)
        assertEquals(setOf(RoutineChannel.VOICE, RoutineChannel.HUD), saved.channels)
    }

    @Test
    fun birthdaysParseAndMatchUpcomingDays() {
        assertEquals(Triple(1990, 10, 3), BirthdayService.parseDate("1990-10-03"))
        assertEquals(Triple(null, 10, 2), BirthdayService.parseDate("--10-02"))
        assertNull(BirthdayService.parseDate("ayer"))

        val all = listOf(
            BirthdayService.Birthday("Ana", 10, 2, 1996),
            BirthdayService.Birthday("Luis", 10, 3, null),
            BirthdayService.Birthday("Eva", 12, 24, null)
        )
        val upcoming = BirthdayService.upcoming(all, at(2026, 10, 2, 9, 0), 1)
        assertEquals(listOf("Ana" to 0, "Luis" to 1), upcoming.map { it.first.name to it.second })
    }

    @Test
    fun gmailSenderNameIsReadable() {
        assertEquals("Ana Pérez", GmailService.senderName("\"Ana Pérez\" <ana@x.com>"))
        assertEquals("facturas", GmailService.senderName("<facturas@banco.com>"))
    }
}
