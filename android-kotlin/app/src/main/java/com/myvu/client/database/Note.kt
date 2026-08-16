package com.myvu.client.database

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Note(
    var id: Long = 0,
    var type: String = "TEXT", // "TEXT" or "VOICE"
    var title: String = "",
    var body: String = "",
    var audioPath: String? = null,
    var durationSec: Int = 0,
    var tags: String = "",
    var summary: String = "",
    var actionItems: String = "",
    var mindmapData: String = "",
    var attachmentsJson: String = "[]",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    val tagsList: List<String>
        get() = if (tags.isBlank()) emptyList() else tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun getAttachments(): List<Attachment> = Attachment.listFromJson(attachmentsJson)

    fun formattedDate(): String {
        val time = if (updatedAt > 0) updatedAt else (if (createdAt > 0) createdAt else System.currentTimeMillis())
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        return sdf.format(Date(time))
    }
}
