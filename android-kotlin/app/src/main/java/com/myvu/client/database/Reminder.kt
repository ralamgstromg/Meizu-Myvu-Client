package com.myvu.client.database

data class Reminder(
    var id: Long = 0,
    var body: String = "",
    var triggerAt: Long = 0,
    var createdAt: Long = 0,
    var state: String = "PENDING",
    var alarmRequestCode: Int = 0
)
