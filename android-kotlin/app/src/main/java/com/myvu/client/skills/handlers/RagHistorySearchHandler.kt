package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.database.NoteRepository
import com.myvu.client.database.ReminderRepository
import com.myvu.client.database.TodoRepository
import com.myvu.client.database.VoiceRecordingRepository
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

/**
 * Enhanced RAG & Local Memory Search Handler:
 * Conducts contextual search across voice recording transcripts, AI summaries,
 * user notes, reminders, and todo lists stored locally in SQLite Room database.
 */
class RagHistorySearchHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val query = args.optString("query", "").trim()
            val scope = args.optString("search_scope", "all").lowercase().trim()

            if (query.isEmpty()) {
                return SkillResult(false, "Falta especificar el término de búsqueda ('query').")
            }

            val recordingRepo = VoiceRecordingRepository(context)
            val noteRepo = NoteRepository(context)
            val reminderRepo = ReminderRepository(context)
            val todoRepo = TodoRepository(context)

            val matchingRecordings = if (scope == "all" || scope == "recordings" || scope == "audio") {
                recordingRepo.getAllRecordings().filter { rec ->
                    rec.title.contains(query, ignoreCase = true) ||
                    rec.rawTranscript.contains(query, ignoreCase = true) ||
                    rec.summary.contains(query, ignoreCase = true) ||
                    rec.tags.contains(query, ignoreCase = true)
                }
            } else emptyList()

            val matchingNotes = if (scope == "all" || scope == "notes") {
                noteRepo.getAllNotes().filter { note ->
                    note.title.contains(query, ignoreCase = true) ||
                    note.body.contains(query, ignoreCase = true) ||
                    note.tags.contains(query, ignoreCase = true)
                }
            } else emptyList()

            val matchingReminders = if (scope == "all" || scope == "reminders") {
                reminderRepo.getAllReminders().filter { rem ->
                    rem.title.contains(query, ignoreCase = true) ||
                    rem.body.contains(query, ignoreCase = true)
                }
            } else emptyList()

            val matchingTodos = if (scope == "all" || scope == "todos") {
                todoRepo.getAllTodos().filter { todo ->
                    todo.title.contains(query, ignoreCase = true) ||
                    todo.tags.contains(query, ignoreCase = true)
                }
            } else emptyList()

            val totalMatches = matchingRecordings.size + matchingNotes.size + matchingReminders.size + matchingTodos.size

            if (totalMatches == 0) {
                return SkillResult(true, "No se encontraron registros en tu historial para '$query'.")
            }

            val sb = StringBuilder()
            sb.append("🔍 **Búsqueda en Historial Local para '$query'** ($totalMatches coincidencias):\n\n")

            if (matchingRecordings.isNotEmpty()) {
                sb.append("🎙️ **Grabaciones de Voz (${matchingRecordings.size})**:\n")
                matchingRecordings.take(3).forEach { rec ->
                    val textSource = rec.summary.ifBlank { rec.rawTranscript }
                    val snippet = extractSnippet(textSource, query)
                    sb.append("• **${rec.title}** (#${rec.id})\n  \"$snippet\"\n")
                }
                sb.append("\n")
            }

            if (matchingNotes.isNotEmpty()) {
                sb.append("📝 **Notas de Texto (${matchingNotes.size})**:\n")
                matchingNotes.take(3).forEach { note ->
                    val snippet = extractSnippet(note.body, query)
                    sb.append("• **${note.title}**: \"$snippet\"\n")
                }
                sb.append("\n")
            }

            if (matchingReminders.isNotEmpty()) {
                sb.append("⏰ **Recordatorios (${matchingReminders.size})**:\n")
                matchingReminders.take(2).forEach { rem ->
                    sb.append("• **${rem.title}** (${rem.state})\n")
                }
                sb.append("\n")
            }

            if (matchingTodos.isNotEmpty()) {
                sb.append("✅ **Tareas (${matchingTodos.size})**:\n")
                matchingTodos.take(2).forEach { todo ->
                    val status = if (todo.completed) "Completada" else "Pendiente"
                    sb.append("• **${todo.title}** [$status]\n")
                }
            }

            SkillResult(
                success = true,
                message = sb.toString().trim(),
                payload = mapOf(
                    "query" to query,
                    "totalMatches" to totalMatches,
                    "recordingsCount" to matchingRecordings.size,
                    "notesCount" to matchingNotes.size
                )
            )
        } catch (e: Exception) {
            LogBus.error("RagHistorySearchHandler -> Error executing RAG search", e)
            SkillResult(false, "Error al buscar en el historial local: ${e.message}")
        }
    }

    private fun extractSnippet(text: String, query: String, maxChars: Int = 120): String {
        if (text.isBlank()) return "Sin contenido."
        val idx = text.indexOf(query, ignoreCase = true)
        return if (idx >= 0) {
            val start = maxOf(0, idx - 30)
            val end = minOf(text.length, idx + query.length + 60)
            val prefix = if (start > 0) "..." else ""
            val suffix = if (end < text.length) "..." else ""
            prefix + text.substring(start, end).replace("\n", " ").trim() + suffix
        } else {
            text.take(maxChars).replace("\n", " ").trim() + if (text.length > maxChars) "..." else ""
        }
    }
}
