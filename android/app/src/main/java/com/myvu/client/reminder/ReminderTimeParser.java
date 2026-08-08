package com.myvu.client.reminder;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReminderTimeParser {

    private static final Pattern RELATIVE_PATTERN = Pattern.compile(
            "(?i)^(?:en|in)?\\s*(\\d+)\\s*(m(?:in(?:uto)?s?)?|h(?:our|ora)?s?|d(?:ía|ia|ay)?s?)$"
    );

    private static final Pattern TIME_PATTERN = Pattern.compile(
            "^(\\d{1,2})[:\\.](\\d{2})$"
    );

    public static long parseTimeToMillis(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return -1;
        }

        String cleaned = rawInput.trim().toLowerCase();

        // 1. Try numeric timestamp directly
        try {
            long millis = Long.parseLong(cleaned);
            if (millis > System.currentTimeMillis()) {
                return millis;
            }
        } catch (NumberFormatException ignored) {}

        // 2. Relative time: e.g. "en 10 minutos", "in 2 hours", "15m", "1h"
        Matcher relMatcher = RELATIVE_PATTERN.matcher(cleaned);
        if (relMatcher.find()) {
            try {
                int amount = Integer.parseInt(relMatcher.group(1));
                String unit = relMatcher.group(2).toLowerCase();

                Calendar cal = Calendar.getInstance();
                if (unit.startsWith("m")) {
                    cal.add(Calendar.MINUTE, amount);
                } else if (unit.startsWith("h")) {
                    cal.add(Calendar.HOUR_OF_DAY, amount);
                } else if (unit.startsWith("d")) {
                    cal.add(Calendar.DAY_OF_YEAR, amount);
                }
                return cal.getTimeInMillis();
            } catch (Exception ignored) {}
        }

        // 3. Time pattern: e.g. "18:30" or "08.45"
        Matcher timeMatcher = TIME_PATTERN.matcher(cleaned);
        if (timeMatcher.find()) {
            try {
                int hour = Integer.parseInt(timeMatcher.group(1));
                int minute = Integer.parseInt(timeMatcher.group(2));

                if (hour >= 0 && hour < 24 && minute >= 0 && minute < 60) {
                    Calendar cal = Calendar.getInstance();
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE, minute);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);

                    // If time has passed today, schedule for tomorrow
                    if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                        cal.add(Calendar.DAY_OF_YEAR, 1);
                    }
                    return cal.getTimeInMillis();
                }
            } catch (Exception ignored) {}
        }

        // Default fallback: 15 minutes from now if unparseable but present
        Calendar fallback = Calendar.getInstance();
        fallback.add(Calendar.MINUTE, 15);
        return fallback.getTimeInMillis();
    }
}
