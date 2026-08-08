package com.myvu.client.reminder;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.myvu.client.core.LogBus;
import com.myvu.client.database.Reminder;
import com.myvu.client.database.ReminderRepository;

public class ReminderActionReceiver extends BroadcastReceiver {

    public static final String ACTION_COMPLETE = "com.myvu.client.reminder.ACTION_COMPLETE";
    public static final String ACTION_SNOOZE = "com.myvu.client.reminder.ACTION_SNOOZE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        long reminderId = intent.getLongExtra("reminder_id", -1);
        if (reminderId == -1) return;

        String action = intent.getAction();
        ReminderRepository repo = new ReminderRepository(context);

        // Cancel Phone Notification
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel((int) reminderId);
        }

        // Dismiss HUD notification
        ReminderNotifier.dismissGlassesNotification(context, reminderId);

        if (ACTION_COMPLETE.equals(action)) {
            LogBus.log("ReminderActionReceiver -> Marking reminder #" + reminderId + " as COMPLETED");
            repo.updateReminderState(reminderId, "COMPLETED");
        } else if (ACTION_SNOOZE.equals(action)) {
            long newTriggerAt = System.currentTimeMillis() + (10 * 60 * 1000); // +10 minutes
            LogBus.log("ReminderActionReceiver -> Snoozing reminder #" + reminderId + " +10m");

            Reminder r = repo.getReminder(reminderId);
            if (r != null && repo.snoozeReminder(reminderId, newTriggerAt)) {
                ReminderScheduler.scheduleReminder(context, reminderId, newTriggerAt, r.getAlarmRequestCode());
            }
        }
    }
}
