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

                // 1. Resumen Ejecutivo
                onProgress("Generando resumen ejecutivo multimodal...")
                val summaryClient = getAiClient("Eres un asistente ejecutivo para usuarios con gafas inteligentes AR. Resume de forma estructurada en formato Markdown claro, considerando tanto el texto de la nota como los documentos/imágenes adjuntos.")
                val summary = summaryClient.ask("Sintetiza la siguiente nota y sus adjuntos usando negritas, viñetas y encabezados concisos:\n\n$content").trim()

                // 2. Extracción de Tareas
                onProgress("Detectando tareas y compromisos...")
                val taskClient = getAiClient("Analiza la nota y sus documentos adjuntos. Responde ÚNICAMENTE con un array JSON válido con la estructura: [{\"task\": \"descripción\", \"owner\": \"persona o vacío\", \"deadline\": \"plazo o vacío\", \"completed\": false}]. Si no hay tareas responde [].")
                val taskRaw = taskClient.ask(content).trim()
                val actionItems = sanitizeJsonArray(taskRaw)

                // 3. Mapa Mental
                onProgress("Estructurando mapa mental...")
                val mindmapClient = getAiClient("Crea un mapa mental jerárquico estructurado a partir de esta nota y sus adjuntos usando sangría con guiones para ramas y sub-ramas.")
                val mindmap = mindmapClient.ask(content).trim()

                // 4. Tags
                onProgress("Generando etiquetas...")
                val tagsClient = getAiClient("Genera de 2 a 4 tags cortos separados por comas para esta nota. Responde solo los tags sin texto adicional.")
                val tags = tagsClient.ask(content).replace("#", "").trim()

                // Guardar en base de datos
                note.summary = summary
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

                // 1. Resumen y contexto
                onProgress("Analizando contexto del recordatorio y adjuntos...")
                val summaryClient = getAiClient("Genera un resumen y desglose ejecutivo en formato Markdown para este recordatorio y sus archivos adjuntos.")
                val summary = summaryClient.ask("Fecha programada: ${reminder.formattedTriggerDate()}\nDetalle: $content").trim()

                // 2. Sub-tareas
                onProgress("Extrayendo acciones necesarias...")
                val taskClient = getAiClient("Extrae las sub-tareas necesarias para cumplir con este recordatorio. Responde ÚNICAMENTE con un array JSON: [{\"task\": \"descripción\", \"owner\": \"\", \"deadline\": \"\", \"completed\": false}].")
                val taskRaw = taskClient.ask(content).trim()
                val actionItems = sanitizeJsonArray(taskRaw)

                // 3. Mapa Mental
                onProgress("Estructurando mapa mental...")
                val mindmapClient = getAiClient("Crea un mapa mental jerárquico con sangría y guiones para este recordatorio.")
                val mindmap = mindmapClient.ask(content).trim()

                reminder.summary = summary
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
