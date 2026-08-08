package com.myvu.client.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.myvu.client.core.LogBus;
import com.myvu.client.database.Reminder;
import com.myvu.client.database.ReminderRepository;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        long reminderId = intent.getLongExtra("reminder_id", -1);
        if (reminderId == -1) return;

        LogBus.log("ReminderReceiver -> Alarm triggered for reminder #" + reminderId);

        ReminderRepository repo = new ReminderRepository(context);
        Reminder reminder = repo.getReminder(reminderId);

        if (reminder != null && "PENDING".equals(reminder.getState())) {
            repo.updateReminderState(reminderId, "FIRED");
            ReminderNotifier.notifyReminder(context, reminder);
        }
    }
}
