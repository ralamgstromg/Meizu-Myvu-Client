package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.database.NoteRepository
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

class CreateNoteHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val title = args.optString("title", "").trim()
        val body = args.optString("body", "").trim()
        val tags = args.optString("tags", "").trim()

        if (title.isEmpty() && body.isEmpty()) {
            return SkillResult(false, "Falta ingresar un título o contenido para la nota.")
        }

        val repository = NoteRepository(context)
        val note = repository.createNote(
            title = title.ifEmpty { "Nota sin título" },
            body = body,
            type = "TEXT",
            tags = tags
        )

        return if (note != null && note.id > 0L) {
            SkillResult(true, "Nota guardada con éxito: '${note.title}'")
        } else {
            SkillResult(false, "No se pudo guardar la nota en la base de datos.")
        }
    }
}
