package com.myvu.client.database

data class Reminder(
    var id: Long = 0,
    var title: String = "",
    var body: String = "",
    var triggerAt: Long = 0,
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
    var state: String = "PENDING", // PENDING, COMPLETED, SNOOZED, CANCELLED
    var alarmRequestCode: Int = 0
)
