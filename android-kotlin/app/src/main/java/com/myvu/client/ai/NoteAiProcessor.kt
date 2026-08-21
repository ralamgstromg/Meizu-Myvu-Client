package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.database.Attachment
import com.myvu.client.database.Note
import com.myvu.client.database.NoteRepository
import com.myvu.client.database.Reminder
import com.myvu.client.database.ReminderRepository
import com.myvu.client.database.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class NoteAiProcessor(private val context: Context) {

    private val noteRepo = NoteRepository(context)
    private val reminderRepo = ReminderRepository(context)
    private val todoRepo = TodoRepository(context)

    private fun getAiClient(systemPrompt: String): AiClient {
        val aiProviderId = Prefs.aiProvider(context)
        val provider = AiProvider.fromId(aiProviderId)
        val aiApiKey = Prefs.aiApiKey(context, aiProviderId)
        val aiModel = Prefs.aiModel(context, aiProviderId)
        val aiEndpoint = Prefs.aiEndpoint(context, aiProviderId)
        return provider.newClient(context, aiApiKey, aiModel, aiEndpoint, systemPrompt)
    }

    private fun formatAttachmentsForPrompt(attachments: List<Attachment>): String {
        if (attachments.isEmpty()) return ""
        val sb = StringBuilder("\n\n=== ARCHIVOS Y DOCUMENTOS ADJUNTOS ===\n")
        for (att in attachments) {
            sb.append("📎 [${att.fileType.name}] ${att.fileName}:\n")
            if (att.extractedText.isNotBlank()) {
                sb.append(att.extractedText).append("\n\n")
            }
        }
        return sb.toString()
    }

    fun processNote(
        noteId: Long,
        onProgress: (String) -> Unit = {},
        callback: (Result<Note>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val note = noteRepo.getById(noteId)
                if (note == null) {
                    callback(Result.failure(Exception("Nota #$noteId no encontrada")))
                    return@launch
                }

                val baseContent = if (note.title.isNotBlank()) "${note.title}\n\n${note.body}" else note.body
                val attachmentsText = formatAttachmentsForPrompt(note.getAttachments())
                val content = (baseContent + attachmentsText).trim()

                if (content.isBlank()) {
                    callback(Result.failure(Exception("La nota no tiene contenido para analizar")))
                    return@launch
                }

                // 1. Análisis Multimodal Unificado con IA en una sola llamada
                onProgress("🧠 Analizando nota, adjuntos, tareas y mapa mental con IA...")
                val aiPrompt = """
                    Eres un asistente ejecutivo inteligente para usuarios de gafas inteligentes AR.
                    Analiza la siguiente nota y sus documentos/imágenes adjuntos y genera un JSON ESTRICTO con la siguiente estructura exacta:
                    {
                      "summary": "### 🎯 Resumen\n...\n\n### 💬 Puntos Clave\n...",
                      "action_items": [
                        {"task": "Descripción de tarea", "owner": "Responsable o vacío", "deadline": "Plazo o vacío", "completed": false}
                      ],
                      "mindmap": "mindmap\n  root((Título))\n    Tema 1\n      Subtema A\n    Tema 2\n      Subtema B",
                      "tags": ["etiqueta1", "etiqueta2"]
                    }
                    Reglas estrictas:
                    - Responde ÚNICAMENTE el bloque JSON válido, sin delimitadores adicionales ni texto antes o después.
                    - En 'summary' usa sintaxis Markdown clara con viñetas y negritas.
                    - Si no hay tareas pendientes responde [] en 'action_items'.
                    - En 'tags' sugiere de 2 a 4 etiquetas útiles en español.
                """.trimIndent()

                val aiClient = getAiClient(aiPrompt)
                val response = aiClient.ask("CONTENIDO DE LA NOTA Y ADJUNTOS:\n\n$content")
                val cleanJson = sanitizeJsonObject(response)

                var summary = ""
                var actionItems = "[]"
                var mindmap = ""
                var tags = ""

                try {
                    val json = JSONObject(cleanJson)
                    summary = com.myvu.client.core.MarkdownUtils.sanitizeToMarkdown(json.optString("summary", ""))
                    actionItems = json.optJSONArray("action_items")?.toString() ?: "[]"
                    mindmap = json.optString("mindmap", "").trim()
                    val tagsArr = json.optJSONArray("tags")
                    if (tagsArr != null && tagsArr.length() > 0) {
                        val tagList = mutableListOf<String>()
                        for (i in 0 until tagsArr.length()) {
                            tagList.add(tagsArr.getString(i).replace("#", "").trim())
                        }
                        tags = tagList.joinToString(", ")
                    } else {
                        tags = json.optString("tags", "").replace("#", "").trim()
                    }
                } catch (e: Exception) {
                    LogBus.warn("NoteAiProcessor -> Failed to parse pure JSON response, using text fallback")
                    summary = com.myvu.client.core.MarkdownUtils.sanitizeToMarkdown(response)
                }

                // Guardar en base de datos
                note.summary = if (summary.isNotBlank()) summary else note.body
                note.actionItems = actionItems
                note.mindmapData = mindmap
                if (note.tags.isBlank()) {
                    note.tags = tags
                }
                noteRepo.update(note)

                LogBus.log("NoteAiProcessor -> Successfully processed note #$noteId with AI")
                callback(Result.success(note))
            } catch (e: Exception) {
                LogBus.error("NoteAiProcessor -> Error processing note #$noteId", e)
                callback(Result.failure(e))
            }
        }
    }

    fun processReminder(
        reminderId: Long,
        onProgress: (String) -> Unit = {},
        callback: (Result<Reminder>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = reminderRepo.getReminder(reminderId)
                if (reminder == null) {
                    callback(Result.failure(Exception("Recordatorio #$reminderId no encontrado")))
                    return@launch
                }

                val baseContent = if (reminder.title.isNotBlank()) "${reminder.title}\n\n${reminder.body}" else reminder.body
                val attachmentsText = formatAttachmentsForPrompt(reminder.getAttachments())
                val content = (baseContent + attachmentsText).trim()

                // Análisis unificado con IA
                onProgress("🧠 Analizando contexto del recordatorio y adjuntos con IA...")
                val aiPrompt = """
                    Eres un asistente ejecutivo experto. Analiza el recordatorio y sus adjuntos y genera un JSON ESTRICTO:
                    {
                      "summary": "### ⏰ Contexto y Detalles\n...",
                      "action_items": [
                        {"task": "Sub-tarea", "owner": "", "deadline": "", "completed": false}
                      ],
                      "mindmap": "mindmap\n  root((Recordatorio))\n    Paso 1\n    Paso 2"
                    }
                    Reglas estrictas:
                    - Responde ÚNICAMENTE con el objeto JSON válido.
                    - En 'summary' escribe el contenido ÚNICAMENTE en formato Markdown estructurado (con listas y negritas). NO utilices JSON en el campo summary.
                """.trimIndent()

                val aiClient = getAiClient(aiPrompt)
                val response = aiClient.ask("Fecha programada: ${reminder.formattedTriggerDate()}\nDetalle:\n$content")
                val cleanJson = sanitizeJsonObject(response)

                var summary = ""
                var actionItems = "[]"
                var mindmap = ""

                try {
                    val json = JSONObject(cleanJson)
                    summary = com.myvu.client.core.MarkdownUtils.sanitizeToMarkdown(json.optString("summary", ""))
                    actionItems = json.optJSONArray("action_items")?.toString() ?: "[]"
                    mindmap = json.optString("mindmap", "").trim()
                } catch (e: Exception) {
                    summary = com.myvu.client.core.MarkdownUtils.sanitizeToMarkdown(response)
                }

                reminder.summary = if (summary.isNotBlank()) summary else reminder.body
                reminder.actionItems = actionItems
                reminder.mindmapData = mindmap
                reminderRepo.update(reminder)

                LogBus.log("NoteAiProcessor -> Successfully processed reminder #$reminderId with AI")
                callback(Result.success(reminder))
            } catch (e: Exception) {
                LogBus.error("NoteAiProcessor -> Error processing reminder #$reminderId", e)
                callback(Result.failure(e))
            }
        }
    }

    fun askQuestionAboutNote(
        note: Note,
        question: String,
        callback: (Result<String>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val attachmentsText = formatAttachmentsForPrompt(note.getAttachments())
                val client = getAiClient("Eres un asistente inteligente para el usuario de las gafas inteligentes MEIZU MYVU. Tienes acceso al contenido de la nota y a los archivos/documentos adjuntos (PDF, Word, Excel, fotos, texto). Responde la pregunta con precisión usando formato Markdown elegante.")
                val prompt = """
                    === NOTA ===
                    Título: ${note.title}
                    Cuerpo: ${note.body}
                    Resumen: ${note.summary}
                    Tareas: ${note.actionItems}
                    $attachmentsText
                    ============

                    Pregunta del usuario: $question
                """.trimIndent()

                val answer = client.ask(prompt)
                callback(Result.success(answer.trim()))
            } catch (e: Exception) {
                LogBus.error("NoteAiProcessor -> askQuestionAboutNote failed", e)
                callback(Result.failure(e))
            }
        }
    }

    fun askQuestionAboutReminder(
        reminder: Reminder,
        question: String,
        callback: (Result<String>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val attachmentsText = formatAttachmentsForPrompt(reminder.getAttachments())
                val client = getAiClient("Eres un asistente inteligente para el usuario de las gafas inteligentes MEIZU MYVU. Tienes acceso al recordatorio y a los documentos adjuntos. Responde la pregunta en formato Markdown.")
                val prompt = """
                    === RECORDATORIO ===
                    Título: ${reminder.title}
                    Detalle: ${reminder.body}
                    Fecha: ${reminder.formattedTriggerDate()}
                    Resumen: ${reminder.summary}
                    Tareas: ${reminder.actionItems}
                    $attachmentsText
                    ====================

                    Pregunta del usuario: $question
                """.trimIndent()

                val answer = client.ask(prompt)
                callback(Result.success(answer.trim()))
            } catch (e: Exception) {
                LogBus.error("NoteAiProcessor -> askQuestionAboutReminder failed", e)
                callback(Result.failure(e))
            }
        }
    }

    fun exportActionItemsToTodos(actionItemsJson: String, defaultListName: String = "Notas"): Int {
        if (actionItemsJson.isBlank() || actionItemsJson == "[]") return 0
        var exported = 0
        try {
            val array = JSONArray(actionItemsJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val task = obj.optString("task", "")
                val owner = obj.optString("owner", "")
                val deadline = obj.optString("deadline", "")
                val fullTitle = buildString {
                    append(task)
                    if (owner.isNotBlank()) append(" (Resp: $owner)")
                    if (deadline.isNotBlank()) append(" [Vence: $deadline]")
                }
                if (task.isNotBlank()) {
                    todoRepo.createTodo(listName = defaultListName, title = fullTitle, tags = "nota,ia")
                    exported++
                }
            }
        } catch (e: Exception) {
            LogBus.error("NoteAiProcessor -> Error exporting action items to todos", e)
        }
        return exported
    }

    private fun sanitizeJsonObject(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) {
            raw.substring(start, end + 1).trim()
        } else {
            "{}"
        }
    }

    private fun sanitizeJsonArray(raw: String): String {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        return if (start != -1 && end != -1 && end > start) {
            raw.substring(start, end + 1).trim()
        } else {
            "[]"
        }
    }
}
