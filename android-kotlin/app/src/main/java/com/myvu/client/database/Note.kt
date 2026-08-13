package com.myvu.client.database

data class Note(
    var id: Long = 0,
    var body: String = "",
    var createdAt: Long = 0,
    var updatedAt: Long = 0
)
