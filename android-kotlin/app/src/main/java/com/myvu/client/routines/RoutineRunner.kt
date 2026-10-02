package com.myvu.client.routines

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.myvu.client.ai.CalendarService
import com.myvu.client.ai.ExternalInfoService
import com.myvu.client.ai.HudAnswerPager
import com.myvu.client.ai.HudSummary
import com.myvu.client.app.feature.Notifications
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.core.TextToSpeechHelper
import com.myvu.client.core.errors.attempt
import com.myvu.client.database.ReminderRepository
import com.myvu.client.database.TodoRepository
import com.myvu.client.service.MyvuService
import com.myvu.client.weather.WeatherSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Composes a routine's briefing from its actions and delivers it on its channels. */
object RoutineRunner {

    private const val CHANNEL_ID = "myvu_routines"
    private val ES_CO = Locale("es", "CO")

    suspend fun run(context: Context, routine: ScheduledRoutine) {
        val text = compose(context, routine.actions)
        LogBus.log("RoutineRunner -> '${routine.name}' composed (${text.length} chars)")
        deliver(context, routine, text)
    }

    /**
     * One sentence block per action, in [RoutineAction] order. Failing sections are
     * skipped and logged. Runs on [Dispatchers.IO]: sections do DB and network I/O.
     */
    suspend fun compose(context: Context, actions: Set<RoutineAction>): String = withContext(Dispatchers.IO) {
        val parts = mutableListOf<String>()
        for (action in RoutineAction.values().filter { it in actions }) {
            val part = when (action) {
                RoutineAction.AGENDA -> attempt("Routine agenda") { agenda(context) }
                RoutineAction.REMINDERS -> attempt("Routine reminders") { reminders(context) }
                RoutineAction.TASKS -> attempt("Routine tasks") { tasks(context) }
                RoutineAction.BIRTHDAYS -> attempt("Routine birthdays") { BirthdayService.summary(context) }
                RoutineAction.EMAILS -> try {
                    GmailService.unreadSummary(context)
                } catch (e: Exception) {
                    LogBus.warn("Routine emails failed: ${e.message}"); null
                }
                RoutineAction.TRM -> attempt("Routine TRM") { trm() }
                RoutineAction.WEATHER -> attempt("Routine weather") { weather(context) }
            }
            if (!part.isNullOrBlank()) parts.add(part.trim().let { if (it.endsWith(".")) it else "$it." })
        }
        parts.joinToString(" ").ifBlank { "No hay novedades para esta rutina." }
    }

    private fun agenda(context: Context): String {
        val events = CalendarService.getEvents(context, "hoy")
        return if (events.isBlank() || events.contains("No tienes eventos")) "No tienes eventos en la agenda de hoy."
        else "Agenda de hoy: $events"
    }

    private fun reminders(context: Context): String {
        val (start, end) = todayBounds()
        val today = ReminderRepository(context).getPendingReminders()
            .filter { it.triggerAt in start until end }
            .sortedBy { it.triggerAt }
        if (today.isEmpty()) return "No tienes recordatorios para hoy."
        val fmt = SimpleDateFormat("h:mm a", ES_CO)
        return "Recordatorios de hoy: " + today.joinToString("; ") { r ->
            "${fmt.format(Date(r.triggerAt))}, ${r.title.ifBlank { r.body }.take(80)}"
        }
    }

    private fun tasks(context: Context): String {
        val pending = TodoRepository(context).getPendingTodos()
        if (pending.isEmpty()) return "No tienes tareas pendientes."
        val label = if (pending.size == 1) "tarea pendiente" else "tareas pendientes"
        return "Tienes ${pending.size} $label: " + pending.take(5).joinToString(", ") { it.title }
    }

    private fun trm(): String {
        val rate = ExternalInfoService.fetchCurrencyRate("USD", "COP") ?: return "No pude consultar la TRM."
        val fmt = NumberFormat.getNumberInstance(ES_CO).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "El dólar está a ${fmt.format(rate)} COP"
    }

    private fun weather(context: Context): String {
        WeatherSync.lastSummary?.takeIf { it.isNotBlank() }?.let { return "Clima: $it" }
        val place = Prefs.weatherPlace(context).ifBlank { "Bogotá" }
        val result = ExternalInfoService.executeSearch("clima en $place hoy")
        return if (result.isBlank() || result.startsWith("No se encontraron")) "No pude consultar el clima." else "Clima: $result"
    }

    private fun todayBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return start to cal.timeInMillis
    }

    fun deliver(context: Context, routine: ScheduledRoutine, text: String) {
        if (RoutineChannel.NOTIFICATION in routine.channels) attempt("Routine notification") { notify(context, routine, text) }
        if (RoutineChannel.VOICE in routine.channels) attempt("Routine voice") {
            TextToSpeechHelper.init(context)
            TextToSpeechHelper.speak("${routine.name}. $text", context = context)
        }
        if (RoutineChannel.HUD in routine.channels) attempt("Routine HUD") {
            val connection = MyvuService.activeConnection()
            if (connection == null) {
                LogBus.log("RoutineRunner -> glasses not connected, HUD delivery skipped")
            } else {
                HudAnswerPager.open(text)
                connection.sendAction(Notifications.buildShow(routine.name, HudSummary.condense(text)))
            }
        }
    }

    private fun notify(context: Context, routine: ScheduledRoutine, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Rutinas del asistente", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(routine.name)
            .setContentText(HudSummary.condense(text, 80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            @Suppress("MissingPermission")
            manager.notify(routine.requestCode, notification)
        } else {
            LogBus.warn("RoutineRunner -> notifications disabled, '${routine.name}' not posted")
        }
    }
}
