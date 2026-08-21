package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.database.NoteRepository
import com.myvu.client.database.VoiceRecordingRepository
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

/**
 * Native RAG History Search Handler:
 * Performs cross-cutting search across voice recording transcripts, summaries, and text notes stored in SQLite.
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

            val matchingRecordings = if (scope == "all" || scope == "recordings") {
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

            val totalMatches = matchingRecordings.size + matchingNotes.size

            val sb = StringBuilder()
            sb.append("🔍 **Búsqueda en Historial Local para '$query'** ($totalMatches coincidencias):\n\n")

            if (matchingRecordings.isNotEmpty()) {
                sb.append("🎙️ **Grabaciones de Voz (${matchingRecordings.size})**:\n")
                matchingRecordings.take(5).forEach { rec ->
                    val snippet = if (rec.summary.isNotBlank()) {
                        rec.summary.take(120) + "..."
                    } else if (rec.rawTranscript.isNotBlank()) {
                        rec.rawTranscript.take(120) + "..."
                    } else "Sin transcripción."

                    sb.append("• **${rec.title}** (#${rec.id})\n")
                    sb.append("  *Extracto*: \"$snippet\"\n")
                }
                sb.append("\n")
            }

            if (matchingNotes.isNotEmpty()) {
                sb.append("📝 **Notas Guardadas (${matchingNotes.size})**:\n")
                matchingNotes.take(5).forEach { note ->
                    val snippet = if (note.body.isNotBlank()) note.body.take(120) + "..." else "Nota vacía."
                    sb.append("• **${note.title}** (#${note.id})\n")
                    sb.append("  *Extracto*: \"$snippet\"\n")
                }
            }

            if (totalMatches == 0) {
                sb.append("ℹ️ No se encontraron grabaciones ni notas que coincidan con '$query'.")
            }

            SkillResult(
                success = true,
                message = sb.toString(),
                payload = mapOf(
                    "recordingsCount" to matchingRecordings.size,
                    "notesCount" to matchingNotes.size,
                    "totalMatches" to totalMatches
                )
            )
        } catch (e: Exception) {
            LogBus.error("RagHistorySearchHandler -> Exception searching local storage", e)
            SkillResult(false, "Error al buscar en el historial local: ${e.message}")
        }
    }
}
