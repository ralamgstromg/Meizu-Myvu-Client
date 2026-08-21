package com.myvu.client.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.database.TodoRepository
import com.myvu.client.database.VoiceRecording
import com.myvu.client.database.VoiceRecordingRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MeetingAiProcessor(private val context: Context) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val repository = VoiceRecordingRepository(context)

    fun interface ProgressCallback {
        fun onProgress(stage: String)
    }

    fun interface CompletionCallback<T> {
        fun onResult(result: Result<T>)
    }

    /**
     * Transcribes an audio file using the configured STT provider (Groq/Whisper, Local, or Cloud).
     */
    fun transcribeAudio(file: File, callback: CompletionCallback<String>) {
        executor.execute {
            try {
                val sttProviderId = Prefs.sttProvider(context)
                val apiKey = Prefs.sttApiKey(context, sttProviderId).ifBlank {
                    Prefs.aiApiKey(context, "groq")
                }
                val model = Prefs.sttModel(context, sttProviderId).ifBlank { "whisper-large-v3" }
                val endpoint = Prefs.sttEndpoint(context, sttProviderId).ifBlank {
                    "https://api.groq.com/openai/v1/audio/transcriptions"
                }
                val ignoreSsl = Prefs.ignoreSsl(context)

                val client = OpenAiTranscriptionClient(
                    endpoint = endpoint,
                    model = model,
                    apiKey = apiKey,
                    serviceLabel = "STT-$sttProviderId",
                    ignoreSsl = ignoreSsl,
                    customLanguage = "es"
                )

                if (!client.isConfigured()) {
                    mainHandler.post {
                        callback.onResult(Result.failure(IllegalStateException("Proveedor STT no configurado. Configura tu API Key en Ajustes de IA.")))
                    }
                    return@execute
                }

                LogBus.log("MeetingAiProcessor: Transcribing file ${file.name} via $endpoint ($model)...")
                val transcript = client.transcribeAudioFile(file)
                LogBus.log("MeetingAiProcessor: Transcription completed (${transcript.length} chars)")

                mainHandler.post {
                    callback.onResult(Result.success(transcript))
                }
            } catch (e: Exception) {
                LogBus.error("MeetingAiProcessor: STT Transcription failed", e)
                mainHandler.post {
                    callback.onResult(Result.failure(e))
                }
            }
        }
    }

    /**
     * Full AI analysis pipeline: Transcribes if needed, diarizes speakers, generates executive summary,
     * extracts action items/todos, generates a mind map, and suggests tags.
     */
    fun processFullMeeting(
        recordingId: Long,
        onProgress: ProgressCallback? = null,
        callback: CompletionCallback<VoiceRecording>
    ) {
        executor.execute {
            val recording = repository.getRecordingById(recordingId)
            if (recording == null) {
                mainHandler.post {
                    callback.onResult(Result.failure(IllegalArgumentException("Grabación no encontrada ID=$recordingId")))
                }
                return@execute
            }

            try {
                // Step 1: Transcription
                var rawTranscript = recording.rawTranscript
                if (rawTranscript.isBlank()) {
                    postProgress(onProgress, "🎙️ Transcribiendo audio con STT...")
                    repository.updateStatus(recordingId, VoiceRecording.STATUS_TRANSCRIBING)

                    val audioFile = File(recording.audioPath)
                    if (!audioFile.exists() || audioFile.length() == 0L) {
                        throw IllegalStateException("El archivo de audio no existe o está vacío: ${recording.audioPath}")
                    }

                    val sttProviderId = Prefs.sttProvider(context)
                    val apiKey = Prefs.sttApiKey(context, sttProviderId).ifBlank {
                        Prefs.aiApiKey(context, "groq")
                    }
                    val model = Prefs.sttModel(context, sttProviderId).ifBlank { "whisper-large-v3" }
                    val endpoint = Prefs.sttEndpoint(context, sttProviderId).ifBlank {
                        "https://api.groq.com/openai/v1/audio/transcriptions"
                    }
                    val ignoreSsl = Prefs.ignoreSsl(context)

                    val sttClient = OpenAiTranscriptionClient(
                        endpoint = endpoint,
                        model = model,
                        apiKey = apiKey,
                        serviceLabel = "STT-$sttProviderId",
                        ignoreSsl = ignoreSsl,
                        customLanguage = "es"
                    )

                    if (!sttClient.isConfigured()) {
                        throw IllegalStateException("STT no configurado. Revisa la clave API de Groq/Whisper en Ajustes de IA.")
                    }

                    rawTranscript = sttClient.transcribeAudioFile(audioFile)
                    if (rawTranscript.isBlank()) {
                        throw IllegalStateException("No se detectó voz o la transcripción resultó vacía.")
                    }
                    recording.rawTranscript = rawTranscript
                }

                // Step 2: Comprehensive Multi-dimensional AI analysis
                postProgress(onProgress, "🧠 Analizando con IA (Diarización, Resumen, Tareas y Mapa Mental)...")
                repository.updateStatus(recordingId, VoiceRecording.STATUS_ANALYZING)

                val aiProviderId = Prefs.aiProvider(context)
                val provider = AiProvider.fromId(aiProviderId)
                val aiApiKey = Prefs.aiApiKey(context, aiProviderId)
                val aiModel = Prefs.aiModel(context, aiProviderId)
                val aiEndpoint = Prefs.aiEndpoint(context, aiProviderId)

                val aiPrompt = """
                    Eres un asistente ejecutivo experto en análisis de reuniones, entrevistas e ideas.
                    Analiza la siguiente transcripción de audio y genera un JSON ESTRICTO con la siguiente estructura exacta:
                    {
                      "diarized": [
                        {"speaker": "Hablante 1", "text": "Fragmento del diálogo..."},
                        {"speaker": "Hablante 2", "text": "Respuesta..."}
                      ],
                      "summary": "### 🎯 Objetivo\n...\n\n### 💬 Puntos Clave\n...\n\n### 🤝 Acuerdos y Decisiones\n...",
                      "action_items": [
                        {"task": "Descripción de tarea", "owner": "Nombre o Rol", "deadline": "Fecha o plazo"}
                      ],
                      "mindmap_mermaid": "mindmap\n  root((Título))\n    Tema 1\n      Subtema A\n      Subtema B\n    Tema 2\n      Subtema C",
                      "tags": ["reunion", "proyecto", "tema_clave"]
                    }
                    Reglas estrictas:
                    - Responde ÚNICAMENTE el bloque JSON válido, sin delimitadores ```json adicionales ni texto antes o después.
                    - Si sólo habla una persona (nota de voz o monólogo de idea), usa 'Hablante 1' para todo.
                    - En 'mindmap_mermaid' genera sintaxis válida de Mermaid mindmap o graph TD.
                    - En 'tags' sugiere de 2 a 5 tags cortos y útiles en español.
                """.trimIndent()

                val aiClient = provider.newClient(context, aiApiKey, aiModel, aiEndpoint, aiPrompt)
                if (!aiClient.isConfigured()) {
                    throw IllegalStateException("Cliente de IA ($aiProviderId) no configurado.")
                }

                val attachments = recording.getAttachments()
                val attachmentsText = if (attachments.isNotEmpty()) {
                    val sb = StringBuilder("\n\n=== ARCHIVOS Y DOCUMENTOS ADJUNTOS ===\n")
                    for (att in attachments) {
                        sb.append("📎 [${att.fileType.name}] ${att.fileName}:\n")
                        if (att.extractedText.isNotBlank()) {
                            sb.append(att.extractedText).append("\n\n")
                        }
                    }
                    sb.toString()
                } else ""

                val fullRaw = "TRANSCRIPCIÓN DE LA REUNIÓN / GRABACIÓN:\n\n$rawTranscript$attachmentsText"
                val fullContent = if (fullRaw.length > MAX_TRANSCRIPT_CHARS) {
                    val half = MAX_TRANSCRIPT_CHARS / 2
                    val startPart = fullRaw.substring(0, half)
                    val endPart = fullRaw.substring(fullRaw.length - half)
                    "$startPart\n\n... [CONTENIDO INTERMEDIO TRUNCADO POR LONGITUD PARA EVITAR TIMEOUT] ...\n\n$endPart"
                } else fullRaw

                val aiResponse = aiClient.ask(fullContent)
                LogBus.log("MeetingAiProcessor: Received AI analysis (${aiResponse.length} chars)")

                // Parse AI JSON response
                val cleanJson = cleanJsonString(aiResponse)
                val json = try {
                    JSONObject(cleanJson)
                } catch (e: Exception) {
                    LogBus.warn("MeetingAiProcessor: Failed to parse pure JSON, creating structured fallback")
                    createFallbackAnalysis(rawTranscript, aiResponse)
                }

                val diarizedArray = json.optJSONArray("diarized")
                val diarizedString = diarizedArray?.toString() ?: ""
                val summaryString = com.myvu.client.core.MarkdownUtils.sanitizeToMarkdown(json.optString("summary", rawTranscript))
                val actionItemsArray = json.optJSONArray("action_items")
                val actionItemsString = actionItemsArray?.toString() ?: "[]"
                val mindmapString = json.optString("mindmap_mermaid", generateSimpleMindmap(recording.title, rawTranscript))

                val tagsArray = json.optJSONArray("tags")
                val suggestedTags = if (tagsArray != null && tagsArray.length() > 0) {
                    val list = mutableListOf<String>()
                    for (i in 0 until tagsArray.length()) {
                        list.add(tagsArray.optString(i).replace("#", "").trim())
                    }
                    list.joinToString(",")
                } else {
                    recording.tags.ifBlank { "reunión,notas" }
                }

                recording.diarizedTranscript = diarizedString
                recording.summary = summaryString
                recording.actionItems = actionItemsString
                recording.mindmapData = mindmapString
                recording.tags = suggestedTags
                recording.status = VoiceRecording.STATUS_READY

                repository.updateRecording(recording)
                LogBus.log("MeetingAiProcessor: Full meeting analysis successfully saved for #${recording.id}")

                mainHandler.post {
                    callback.onResult(Result.success(recording))
                }
            } catch (e: Exception) {
                LogBus.error("MeetingAiProcessor: Pipeline error", e)
                repository.updateStatus(recordingId, VoiceRecording.STATUS_ERROR)
                mainHandler.post {
                    callback.onResult(Result.failure(e))
                }
            }
        }
    }

    /**
     * Ask a specific question grounded strictly in this recording's content and its attachments.
     */
    fun askQuestionAboutRecording(
        recording: VoiceRecording,
        question: String,
        callback: CompletionCallback<String>
    ) {
        executor.execute {
            try {
                val aiProviderId = Prefs.aiProvider(context)
                val provider = AiProvider.fromId(aiProviderId)
                val aiApiKey = Prefs.aiApiKey(context, aiProviderId)
                val aiModel = Prefs.aiModel(context, aiProviderId)
                val aiEndpoint = Prefs.aiEndpoint(context, aiProviderId)

                val attachments = recording.getAttachments()
                val attachmentsText = if (attachments.isNotEmpty()) {
                    val sb = StringBuilder("\n\n=== ARCHIVOS Y DOCUMENTOS ADJUNTOS ===\n")
                    for (att in attachments) {
                        sb.append("📎 [${att.fileType.name}] ${att.fileName}:\n")
                        if (att.extractedText.isNotBlank()) {
                            sb.append(att.extractedText).append("\n\n")
                        }
                    }
                    sb.toString()
                } else ""

                val prompt = """
                    Eres un asistente inteligente que responde preguntas sobre una grabación de audio y sus documentos adjuntos.
                    Responde de forma clara, directa y estructurada en Markdown.
                    
                    DATOS DE LA GRABACIÓN:
                    Título: ${recording.title}
                    Categoría: ${recording.category}
                    
                    RESUMEN:
                    ${recording.summary}
                    
                    TRANSCRIPCIÓN COMPLETA:
                    ${recording.rawTranscript}
                    $attachmentsText
                """.trimIndent()

                val client = provider.newClient(context, aiApiKey, aiModel, aiEndpoint, prompt)
                val answer = client.ask(question)

                mainHandler.post {
                    callback.onResult(Result.success(answer))
                }
            } catch (e: Exception) {
                LogBus.error("MeetingAiProcessor: Q&A failed", e)
                mainHandler.post {
                    callback.onResult(Result.failure(e))
                }
            }
        }
    }

    /**
     * Exports action items from JSON directly into the App's TodoRepository.
     */
    fun exportActionItemsToTodos(actionItemsJson: String, listName: String = "Reuniones"): Int {
        if (actionItemsJson.isBlank()) return 0
        return try {
            val todoRepo = TodoRepository(context)
            val jsonArray = JSONArray(actionItemsJson)
            var count = 0
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val task = item.optString("task", "").trim()
                if (task.isNotEmpty()) {
                    val owner = item.optString("owner", "").trim()
                    val deadline = item.optString("deadline", "").trim()
                    val extra = listOfNotNull(
                        if (owner.isNotBlank()) "Responsable: $owner" else null,
                        if (deadline.isNotBlank()) "Plazo: $deadline" else null
                    ).joinToString(" | ")

                    val fullTitle = if (extra.isNotBlank()) "$task ($extra)" else task
                    todoRepo.createTodo(listName = listName, title = fullTitle, tags = "voz,reunion")
                    count++
                }
            }
            LogBus.log("MeetingAiProcessor: Exported $count action items to Todos")
            count
        } catch (e: Exception) {
            LogBus.error("MeetingAiProcessor: Failed to export todos", e)
            0
        }
    }

    private fun postProgress(callback: ProgressCallback?, stage: String) {
        mainHandler.post {
            callback?.onProgress(stage)
        }
    }

    private fun cleanJsonString(raw: String): String {
        var str = raw.trim()
        if (str.startsWith("```json")) {
            str = str.removePrefix("```json")
        } else if (str.startsWith("```")) {
            str = str.removePrefix("```")
        }
        if (str.endsWith("```")) {
            str = str.removeSuffix("```")
        }
        return str.trim()
    }

    private fun createFallbackAnalysis(rawTranscript: String, aiResponse: String): JSONObject {
        val json = JSONObject()
        val diarized = JSONArray()
        val parts = rawTranscript.split("\n\n").filter { it.isNotBlank() }
        for (i in parts.indices) {
            val partObj = JSONObject()
            partObj.put("speaker", "Hablante ${(i % 2) + 1}")
            partObj.put("text", parts[i])
            diarized.put(partObj)
        }
        json.put("diarized", diarized)
        json.put("summary", com.myvu.client.core.MarkdownUtils.sanitizeToMarkdown(aiResponse))
        json.put("action_items", JSONArray())
        json.put("mindmap_mermaid", generateSimpleMindmap("Reunión", rawTranscript))
        json.put("tags", JSONArray().put("reunion").put("audio"))
        return json
    }

    private fun generateSimpleMindmap(title: String, text: String): String {
        val safeTitle = title.ifBlank { "Reunión" }
        return """
            mindmap
              root(($safeTitle))
                Transcripción
                  Puntos Generales
                Ideas Clave
                  Temas Tratados
        """.trimIndent()
    }

    companion object {
        private const val MAX_TRANSCRIPT_CHARS = 24000
    }
}
