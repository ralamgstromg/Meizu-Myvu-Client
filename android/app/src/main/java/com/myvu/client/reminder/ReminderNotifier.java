package com.myvu.client.reminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.myvu.client.app.feature.Notifications;
import com.myvu.client.core.LogBus;
import com.myvu.client.database.Reminder;
import com.myvu.client.service.MyvuService;

public class ReminderNotifier {

    public static final String CHANNEL_ID = "myvu_reminders";

    public static void notifyReminder(Context context, Reminder reminder) {
        if (reminder == null) return;

        createNotificationChannel(context);

        // 1. Android Phone Notification with Actions
        int notificationId = (int) reminder.getId();

        // Complete Action
        Intent completeIntent = new Intent(context, ReminderActionReceiver.class);
        completeIntent.setAction(ReminderActionReceiver.ACTION_COMPLETE);
        completeIntent.putExtra("reminder_id", reminder.getId());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pComplete = PendingIntent.getBroadcast(context, notificationId * 10 + 1, completeIntent, flags);

        // Snooze Action (10 min)
        Intent snoozeIntent = new Intent(context, ReminderActionReceiver.class);
        snoozeIntent.setAction(ReminderActionReceiver.ACTION_SNOOZE);
        snoozeIntent.putExtra("reminder_id", reminder.getId());
        PendingIntent pSnooze = PendingIntent.getBroadcast(context, notificationId * 10 + 2, snoozeIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Recordatorio MYVU")
                .setContentText(reminder.getBody())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Completar", pComplete)
                .addAction(android.R.drawable.ic_menu_recent_history, "Posponer 10m", pSnooze);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(notificationId, builder.build());
        }

        // 2. MYVU Glasses HUD Notification
        sendToGlasses(context, reminder);
    }

    public static void dismissGlassesNotification(Context context, long reminderId) {
        try {
            com.myvu.client.service.ConnectionManager conn = MyvuService.activeConnection();
            if (conn != null) {
                String hudId = Notifications.notificationId("com.myvu.client", (int) reminderId);
                conn.sendAction(Notifications.buildDismiss(hudId));
            }
        } catch (Exception e) {
            LogBus.warn("ReminderNotifier -> Could not dismiss HUD notification: " + e.getMessage());
        }
    }

    private static void sendToGlasses(Context context, Reminder reminder) {
        try {
            com.myvu.client.service.ConnectionManager conn = MyvuService.activeConnection();
            if (conn != null) {
                String title = "Recordatorio";
                String body = reminder.getBody();
                long now = System.currentTimeMillis();

                org.json.JSONObject entry = Notifications.entry(
                        "com.myvu.client",
                        (int) reminder.getId(),
                        title,
                        body,
                        "Recordatorio",
                        now,
                        false
                );
                String actionJson = Notifications.buildShow(entry);
                conn.sendAction(actionJson);
                LogBus.log("ReminderNotifier -> Pushed reminder #" + reminder.getId() + " to HUD glasses");
            }
        } catch (Exception e) {
            LogBus.error("ReminderNotifier -> Could not send reminder to glasses", e);
        }
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Recordatorios MYVU",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Canal de notificaciones de recordatorios locales");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }
}
