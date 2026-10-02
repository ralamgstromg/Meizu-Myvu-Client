package com.myvu.client.routines

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myvu.client.core.ExactAlarms
import com.myvu.client.core.LogBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Keeps one alarm per enabled routine, re-armed after each run and on boot. */
object RoutineScheduler {

    const val EXTRA_ROUTINE_ID = "routine_id"

    fun scheduleAll(context: Context) {
        RoutineStore.all(context).forEach { schedule(context, it) }
    }

    fun schedule(context: Context, routine: ScheduledRoutine) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pendingIntent(context, routine)
        val next = routine.nextTrigger(System.currentTimeMillis())
        if (!routine.enabled || next == null) {
            am.cancel(pi)
            return
        }
        try {
            ExactAlarms.set(am, next, pi)
            LogBus.log("RoutineScheduler -> '${routine.name}' next run at $next")
        } catch (e: Exception) {
            LogBus.error("RoutineScheduler -> could not schedule '${routine.name}'", e)
        }
    }

    fun cancel(context: Context, routine: ScheduledRoutine) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pendingIntent(context, routine))
    }

    private fun pendingIntent(context: Context, routine: ScheduledRoutine): PendingIntent {
        val intent = Intent(context, RoutineReceiver::class.java).putExtra(EXTRA_ROUTINE_ID, routine.id)
        return PendingIntent.getBroadcast(
            context, routine.requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** Runs a routine when its alarm fires, then arms the next occurrence. */
class RoutineReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val routine = intent.getStringExtra(RoutineScheduler.EXTRA_ROUTINE_ID)?.let { RoutineStore.get(app, it) } ?: return
        val pending = goAsync()
        scope.launch {
            try {
                if (routine.enabled) RoutineRunner.run(app, routine)
            } catch (e: Exception) {
                LogBus.error("RoutineReceiver -> '${routine.name}' failed", e)
            } finally {
                RoutineScheduler.schedule(app, routine)
                pending.finish()
            }
        }
    }
}
