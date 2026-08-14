package com.myvu.client.database

data class TodoItem(
    var id: Long = 0,
    var listName: String = "General",
    var title: String = "",
    var completed: Boolean = false,
    var tags: String = "",
    var createdAt: Long = 0,
    var updatedAt: Long = 0
)
