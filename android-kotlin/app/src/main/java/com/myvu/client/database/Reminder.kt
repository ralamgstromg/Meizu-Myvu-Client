package com.myvu.client.database

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Reminder(
    var id: Long = 0,
    var title: String = "",
    var body: String = "",
    var tags: String = "",
    var summary: String = "",
    var actionItems: String = "",
    var mindmapData: String = "",
    var attachmentsJson: String = "[]",
    var triggerAt: Long = 0,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var state: String = "PENDING", // PENDING, COMPLETED, SNOOZED, CANCELLED
    var alarmRequestCode: Int = 0
) {
    val tagsList: List<String>
        get() = if (tags.isBlank()) emptyList() else tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun getAttachments(): List<Attachment> = Attachment.listFromJson(attachmentsJson)

    fun formattedTriggerDate(): String {
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        return sdf.format(Date(triggerAt))
    }

    fun formattedDate(): String {
        val time = if (createdAt > 0) createdAt else System.currentTimeMillis()
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        return sdf.format(Date(time))
    }
}
