package com.myvu.client.reminder

import java.util.Calendar
import java.util.regex.Pattern

object ReminderTimeParser {

    private val RELATIVE_PATTERN = Pattern.compile(
        "(?i)^(?:en|in)?\\s*(\\d+)\\s*(m(?:in(?:uto)?s?)?|h(?:our|ora)?s?|d(?:ía|ia|ay)?s?)$"
    )

    private val TIME_PATTERN = Pattern.compile(
        "^(\\d{1,2})[:\\.](\\d{2})$"
    )

    @JvmStatic
    fun parseTimeToMillis(rawInput: String?): Long {
        if (rawInput.isNullOrBlank()) {
            return -1
        }

        val cleaned = rawInput.trim().lowercase()

        try {
            val millis = cleaned.toLong()
            if (millis > System.currentTimeMillis()) {
                return millis
            }
        } catch (ignored: NumberFormatException) {
        }

        val relMatcher = RELATIVE_PATTERN.matcher(cleaned)
        if (relMatcher.find()) {
            try {
                val amount = relMatcher.group(1)!!.toInt()
                val unit = relMatcher.group(2)!!.lowercase()

                val cal = Calendar.getInstance()
                if (unit.startsWith("m")) {
                    cal.add(Calendar.MINUTE, amount)
                } else if (unit.startsWith("h")) {
                    cal.add(Calendar.HOUR_OF_DAY, amount)
                } else if (unit.startsWith("d")) {
                    cal.add(Calendar.DAY_OF_YEAR, amount)
                }
                return cal.timeInMillis
            } catch (ignored: Exception) {
            }
        }

        val timeMatcher = TIME_PATTERN.matcher(cleaned)
        if (timeMatcher.find()) {
            try {
                val hour = timeMatcher.group(1)!!.toInt()
                val minute = timeMatcher.group(2)!!.toInt()

                if (hour in 0..23 && minute in 0..59) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    if (cal.timeInMillis <= System.currentTimeMillis()) {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    return cal.timeInMillis
                }
            } catch (ignored: Exception) {
            }
        }

        val fallback = Calendar.getInstance()
        fallback.add(Calendar.MINUTE, 15)
        return fallback.timeInMillis
    }
}
