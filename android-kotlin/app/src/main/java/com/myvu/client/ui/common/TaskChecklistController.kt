package com.myvu.client.ui.common

import android.content.Context
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.myvu.client.R
import com.myvu.client.core.LogBus
import com.myvu.client.database.TodoRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reusable, decoupled controller for managing Action Items / Task lists across screens:
 * - JSON task parsing & serialization
 * - Dynamic checkbox rendering with strike-through & priority tags
 * - Manual task addition dialog
 * - 1-tap export to central TodoRepository
 */
class TaskChecklistController(
    private val activity: AppCompatActivity,
    private val layTasksContainer: LinearLayout,
    private val btnAddManualTask: MaterialButton?,
    private val btnExportToTodo: MaterialButton?,
    private val defaultCategory: String = "General",
    private val onTasksChanged: (String) -> Unit
) {

    private val todoRepo = TodoRepository(activity)
    private var currentTasksJson: String = "[]"

    init {
        setupButtons()
    }

    private fun setupButtons() {
        btnAddManualTask?.setOnClickListener {
            showAddManualTaskDialog()
        }

        btnExportToTodo?.setOnClickListener {
            exportTasksToTodoRepo()
        }
    }

    fun populateTasks(tasksJson: String) {
        currentTasksJson = tasksJson
        layTasksContainer.removeAllViews()

        if (tasksJson.isBlank() || tasksJson == "[]") {
            val emptyTv = TextView(activity).apply {
                text = "No se han detectado tareas de acción. Añade una manualmente o analiza con IA."
                setTextColor(ContextCompat.getColor(context, R.color.outline_obsidian))
                textSize = 14f
                setPadding(0, 16, 0, 16)
            }
            layTasksContainer.addView(emptyTv)
            return
        }

        try {
            val array = JSONArray(tasksJson)

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val text = item.optString("text", "").ifBlank { item.optString("task", "") }
                if (text.isBlank()) continue

                val isDone = item.optBoolean("done", false)
                val priority = item.optString("priority", "MEDIA").uppercase()

                val rowLayout = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(8, 8, 8, 8)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 6
                    }
                    layoutParams = params
                }

                val cbTask = CheckBox(activity).apply {
                    this.text = text
                    isChecked = isDone
                    setTextColor(ContextCompat.getColor(context, R.color.on_surface_obsidian))
                    textSize = 14f
                    if (isDone) {
                        paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        alpha = 0.6f
                    } else {
                        paintFlags = paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        alpha = 1.0f
                    }
                    val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    layoutParams = params
                }

                val tvPriority = TextView(activity).apply {
                    this.text = " $priority "
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(context, R.color.cyber_teal))
                    setPadding(12, 4, 12, 4)
                    when (priority) {
                        "ALTA", "HIGH" -> setTextColor(ContextCompat.getColor(context, R.color.cyber_neon_red))
                        "BAJA", "LOW" -> setTextColor(ContextCompat.getColor(context, R.color.outline_obsidian))
                        else -> setTextColor(ContextCompat.getColor(context, R.color.cyber_teal))
                    }
                }

                val index = i
                cbTask.setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        cbTask.paintFlags = cbTask.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        cbTask.alpha = 0.6f
                    } else {
                        cbTask.paintFlags = cbTask.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        cbTask.alpha = 1.0f
                    }
                    toggleTaskDone(index, checked)
                }

                rowLayout.addView(cbTask)
                rowLayout.addView(tvPriority)
                layTasksContainer.addView(rowLayout)
            }
        } catch (e: Exception) {
            LogBus.error("TaskChecklistController: Error parsing tasks JSON", e)
            val errorTv = TextView(activity).apply {
                text = "Error al leer tareas: ${e.message}"
                setTextColor(ContextCompat.getColor(context, R.color.cyber_neon_red))
            }
            layTasksContainer.addView(errorTv)
        }
    }

    private fun toggleTaskDone(index: Int, done: Boolean) {
        try {
            val array = JSONArray(currentTasksJson)
            if (index < array.length()) {
                val item = array.getJSONObject(index)
                item.put("done", done)
                currentTasksJson = array.toString()
                onTasksChanged(currentTasksJson)
            }
        } catch (e: Exception) {
            LogBus.error("TaskChecklistController: Error toggling task done state", e)
        }
    }

    private fun showAddManualTaskDialog() {
        val input = EditText(activity).apply {
            setHint("Descripción de la tarea...")
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_obsidian))
            setHintTextColor(ContextCompat.getColor(context, R.color.outline_obsidian))
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(activity)
            .setTitle("Nueva Tarea de Acción")
            .setView(input)
            .setPositiveButton("Añadir") { _, _ ->
                val taskText = input.text.toString().trim()
                if (taskText.isNotBlank()) {
                    addTask(taskText)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun addTask(text: String) {
        try {
            val array = if (currentTasksJson.isNotBlank() && currentTasksJson.startsWith("[")) {
                JSONArray(currentTasksJson)
            } else {
                JSONArray()
            }
            val newTask = JSONObject().apply {
                put("text", text)
                put("done", false)
                put("priority", "MEDIA")
            }
            array.put(newTask)
            currentTasksJson = array.toString()
            onTasksChanged(currentTasksJson)
            populateTasks(currentTasksJson)
            Toast.makeText(activity, "Tarea añadida", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            LogBus.error("TaskChecklistController: Error adding task", e)
        }
    }

    private fun exportTasksToTodoRepo() {
        try {
            val array = JSONArray(currentTasksJson)
            if (array.length() == 0) {
                Toast.makeText(activity, "No hay tareas para exportar", Toast.LENGTH_SHORT).show()
                return
            }

            var exportedCount = 0
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val text = item.optString("text", "").ifBlank { item.optString("task", "") }
                if (text.isBlank()) continue

                val priority = item.optString("priority", "MEDIA")
                todoRepo.createTodo(
                    title = text,
                    listName = defaultCategory,
                    tags = priority.lowercase()
                )
                exportedCount++
            }

            Toast.makeText(activity, "Se exportaron $exportedCount tareas a Mis Tareas", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            LogBus.error("TaskChecklistController: Error exporting tasks to TodoRepository", e)
            Toast.makeText(activity, "Error al exportar tareas: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
