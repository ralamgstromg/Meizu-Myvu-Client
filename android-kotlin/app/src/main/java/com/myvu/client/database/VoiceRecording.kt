package com.myvu.client.database

data class VoiceRecording(
    var id: Long = 0L,
    var title: String = "",
    var audioPath: String = "",
    var durationMs: Long = 0L,
    var fileSizeBytes: Long = 0L,
    var rawTranscript: String = "",
    var diarizedTranscript: String = "",
    var summary: String = "",
    var actionItems: String = "",
    var mindmapData: String = "",
    var tags: String = "",
    var category: String = CATEGORY_MEETING,
    var status: String = STATUS_READY,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val CATEGORY_MEETING = "MEETING"
        const val CATEGORY_IDEA = "IDEA"
        const val CATEGORY_CONVERSATION = "CONVERSATION"
        const val CATEGORY_LECTURE = "LECTURE"
        const val CATEGORY_GENERAL = "GENERAL"

        const val STATUS_RECORDING = "RECORDING"
        const val STATUS_TRANSCRIBING = "TRANSCRIBING"
        const val STATUS_ANALYZING = "ANALYZING"
        const val STATUS_READY = "READY"
        const val STATUS_ERROR = "ERROR"
    }

    val tagsList: List<String>
        get() = if (tags.isBlank()) emptyList() else tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun formattedDuration(): String {
        val totalSecs = (durationMs / 1000).coerceAtLeast(0)
        val hours = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }
}
