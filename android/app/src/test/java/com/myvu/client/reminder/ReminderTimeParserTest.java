package com.myvu.client.reminder;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ReminderTimeParserTest {

    @Test
    public void testRelativeMinutes() {
        long now = System.currentTimeMillis();
        long parsed = ReminderTimeParser.parseTimeToMillis("en 10 minutos");
        assertTrue(parsed >= now + (9 * 60 * 1000));
        assertTrue(parsed <= now + (11 * 60 * 1000));
    }

    @Test
    public void testRelativeHours() {
        long now = System.currentTimeMillis();
        long parsed = ReminderTimeParser.parseTimeToMillis("in 2 hours");
        assertTrue(parsed >= now + (119 * 60 * 1000));
        assertTrue(parsed <= now + (121 * 60 * 1000));
    }

    @Test
    public void testTimeStringFormat() {
        long parsed = ReminderTimeParser.parseTimeToMillis("23:59");
        assertTrue(parsed > System.currentTimeMillis());
    }

    @Test
    public void testFallback() {
        long now = System.currentTimeMillis();
        long parsed = ReminderTimeParser.parseTimeToMillis("invalid time text");
        // Fallback is 15 minutes
        assertTrue(parsed >= now + (14 * 60 * 1000));
    }
}
