package com.myvu.client.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.myvu.client.core.LogBus

class TodoRepository(context: Context) {

    private val dbHelper: LocalDatabase = LocalDatabase.getInstance(context)

    fun createTodo(title: String, listName: String = "General", tags: String = ""): Long {
        val now = System.currentTimeMillis()
        val item = TodoItem(
            listName = listName.ifBlank { "General" },
            title = title.trim(),
            completed = false,
            tags = tags.trim(),
            createdAt = now,
            updatedAt = now
        )
        return insert(item)
    }

    fun insert(item: TodoItem): Long {
        val now = if (item.createdAt == 0L) System.currentTimeMillis() else item.createdAt
        val updated = if (item.updatedAt == 0L) now else item.updatedAt
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("list_name", item.listName)
            put("title", item.title)
            put("completed", if (item.completed) 1 else 0)
            put("tags", item.tags)
            put("created_at", now)
            put("updated_at", updated)
        }
        val id = db.insert("todos", null, values)
        if (id != -1L) {
            item.id = id
            LogBus.log("TodoRepository -> Inserted todo #$id in [${item.listName}]: ${item.title}")
        } else {
            LogBus.error("TodoRepository -> Failed to insert todo", null)
        }
        return id
    }

    fun markCompleted(id: Long, completed: Boolean): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("completed", if (completed) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        val rows = db.update("todos", values, "id = ?", arrayOf(id.toString()))
        LogBus.log("TodoRepository -> markCompleted #$id = $completed (rows: $rows)")
        return rows > 0
    }

    fun markCompletedByTitle(titlePattern: String, completed: Boolean = true): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("completed", if (completed) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        val rows = db.update("todos", values, "title LIKE ?", arrayOf("%$titlePattern%"))
        LogBus.log("TodoRepository -> markCompletedByTitle '$titlePattern' = $completed (rows: $rows)")
        return rows > 0
    }

    fun deleteTodo(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val rows = db.delete("todos", "id = ?", arrayOf(id.toString()))
        LogBus.log("TodoRepository -> Deleted todo #$id (rows: $rows)")
        return rows > 0
    }

    fun deleteByTitle(titlePattern: String): Boolean {
        val db = dbHelper.writableDatabase
        val rows = db.delete("todos", "title LIKE ?", arrayOf("%$titlePattern%"))
        LogBus.log("TodoRepository -> deleteByTitle '$titlePattern' (rows: $rows)")
        return rows > 0
    }

    fun getPendingTodos(listName: String? = null): List<TodoItem> {
        val db = dbHelper.readableDatabase
        val selection = if (listName.isNullOrBlank() || listName.equals("all", ignoreCase = true)) {
            "completed = 0"
        } else {
            "completed = 0 AND list_name LIKE ?"
        }
        val args = if (listName.isNullOrBlank() || listName.equals("all", ignoreCase = true)) null else arrayOf("%$listName%")

        val list = mutableListOf<TodoItem>()
        db.query("todos", null, selection, args, null, null, "created_at ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(fromCursor(cursor))
            }
        }
        return list
    }

    fun getAllTodos(listName: String? = null): List<TodoItem> {
        val db = dbHelper.readableDatabase
        val selection = if (listName.isNullOrBlank() || listName.equals("all", ignoreCase = true)) null else "list_name LIKE ?"
        val args = if (listName.isNullOrBlank() || listName.equals("all", ignoreCase = true)) null else arrayOf("%$listName%")

        val list = mutableListOf<TodoItem>()
        db.query("todos", null, selection, args, null, null, "completed ASC, created_at DESC")?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(fromCursor(cursor))
            }
        }
        return list
    }

    fun getDistinctLists(): List<String> {
        val db = dbHelper.readableDatabase
        val lists = mutableListOf<String>()
        db.rawQuery("SELECT DISTINCT list_name FROM todos ORDER BY list_name ASC", null)?.use { cursor ->
            while (cursor.moveToNext()) {
                lists.add(cursor.getString(0))
            }
        }
        return lists
    }

    private fun fromCursor(cursor: Cursor): TodoItem {
        return TodoItem(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            listName = cursor.getString(cursor.getColumnIndexOrThrow("list_name")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            completed = cursor.getInt(cursor.getColumnIndexOrThrow("completed")) == 1,
            tags = cursor.getString(cursor.getColumnIndexOrThrow("tags")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        )
    }
}
