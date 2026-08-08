package com.myvu.client.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.myvu.client.core.LogBus;
import com.myvu.client.database.Reminder;
import com.myvu.client.database.ReminderRepository;

import java.util.List;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_TIME_CHANGED.equals(action) ||
            Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {

            LogBus.log("BootReceiver -> Rescheduling pending reminders after " + action);

            ReminderRepository repo = new ReminderRepository(context);
            List<Reminder> pending = repo.getPendingReminders();

            long now = System.currentTimeMillis();
            for (Reminder r : pending) {
                if (r.getTriggerAt() > now) {
                    ReminderScheduler.scheduleReminder(context, r.getId(), r.getTriggerAt(), r.getAlarmRequestCode());
                } else {
                    // Mark past un-triggered reminders as FAILED or trigger immediately
                    repo.updateReminderState(r.getId(), "FAILED");
                }
            }
        }
    }
}
