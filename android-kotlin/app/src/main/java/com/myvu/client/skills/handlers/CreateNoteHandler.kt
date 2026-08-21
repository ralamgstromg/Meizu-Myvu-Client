package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.database.NoteRepository
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

class CreateNoteHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val title = args.optString("title", "").trim()
            val body = args.optString("body", "").trim()
            val tags = args.optString("tags", "").trim()

            if (title.isEmpty() && body.isEmpty()) {
                return SkillResult(false, "Falta ingresar un título o contenido para la nota.")
            }

            val repository = NoteRepository(context)
            val noteId = repository.createNote(
                title = title.ifEmpty { "Nota sin título" },
                body = body,
                type = "TEXT",
                tags = tags
            )

            if (noteId > 0L) {
                SkillResult(true, "Nota guardada con éxito (ID: #$noteId)")
            } else {
                SkillResult(false, "No se pudo guardar la nota en la base de datos.")
            }
        } catch (e: Exception) {
            LogBus.error("CreateNoteHandler -> Exception during execution", e)
            SkillResult(false, "Error al guardar la nota: ${e.message}")
        }
    }
}
