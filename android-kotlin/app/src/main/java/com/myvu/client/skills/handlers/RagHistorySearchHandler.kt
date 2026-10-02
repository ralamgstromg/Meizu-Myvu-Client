package com.myvu.client.skills.handlers

import com.myvu.client.core.errors.userMessage
import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.search.SearchIndex
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

            val kinds = when (scope) {
                "recordings", "audio", "meetings" -> setOf(SearchIndex.Kind.RECORDING)
                "notes" -> setOf(SearchIndex.Kind.NOTE)
                "reminders" -> setOf(SearchIndex.Kind.REMINDER)
                "todos", "tasks" -> setOf(SearchIndex.Kind.TODO)
                "chat" -> setOf(SearchIndex.Kind.CHAT)
                else -> SearchIndex.Kind.values().toSet()
            }
            val hits = SearchIndex.search(context, query, kinds, limit = 12)
            if (hits.isEmpty()) {
                return SkillResult(true, "No se encontraron registros en tu historial para '$query'.")
            }

            val sb = StringBuilder("🔍 **Búsqueda en tu contenido para '$query'** (${hits.size} resultados más relevantes):\n\n")
            hits.groupBy { it.kind }.forEach { (kind, items) ->
                sb.append("**${kind.label}s (${items.size})**:\n")
                items.take(4).forEach { hit ->
                    sb.append("• **${hit.title}**")
                    if (hit.snippet.isNotBlank()) sb.append(": \"${hit.snippet}\"")
                    sb.append("\n")
                }
                sb.append("\n")
            }

            SkillResult(
                success = true,
                message = sb.toString().trim(),
                payload = mapOf("query" to query, "totalMatches" to hits.size)
            )
        } catch (e: Exception) {
            LogBus.error("RagHistorySearchHandler -> Error executing RAG search", e)
            SkillResult(false, "Error al buscar en el historial local. ${e.userMessage("RagHistorySearchHandler")}")
        }
    }
}
