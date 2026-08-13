package com.myvu.client.database

data class Note(
    var id: Long = 0,
    var type: String = "TEXT", // "TEXT" or "VOICE"
    var title: String = "",
    var body: String = "",
    var audioPath: String? = null,
    var durationSec: Int = 0,
    var createdAt: Long = 0,
    var updatedAt: Long = 0
)
