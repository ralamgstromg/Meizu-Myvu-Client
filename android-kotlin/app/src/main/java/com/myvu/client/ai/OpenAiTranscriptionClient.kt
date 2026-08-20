package com.myvu.client.ai

import com.myvu.client.core.SslUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets

/** Uploads a WAV or audio file to an OpenAI-compatible audio-transcription endpoint. */
class OpenAiTranscriptionClient @JvmOverloads constructor(
    endpoint: String?,
    model: String?,
    apiKey: String?,
    private val serviceLabel: String,
    private val ignoreSsl: Boolean = false,
    private val customLanguage: String? = "es"
) {
    private val endpoint: String = endpoint?.trim() ?: ""
    private val model: String = model?.trim() ?: ""
    private val apiKey: String = apiKey?.trim() ?: ""

    fun isConfigured(): Boolean {
        return endpoint.isNotEmpty()
    }

    @Throws(IOException::class)
    fun transcribe(pcm: ByteArray, sampleRate: Int, channels: Int): String {
        if (!isConfigured()) {
            throw IOException("$serviceLabel is not fully configured")
        }
        if (pcm.size < MIN_PCM_BYTES) return ""

        val wav = OpusStream.toWav(pcm, sampleRate, channels)
        return HttpRetry.execute(serviceLabel) {
            transcribeWithWriter { out ->
                writeFilePart(out, "file", "speech.wav", "audio/wav", wav)
            }
        }
    }

    @Throws(IOException::class)
    fun transcribeAudioFile(file: File): String {
        if (!isConfigured()) {
            throw IOException("$serviceLabel is not fully configured")
        }
        if (!file.exists() || file.length() == 0L) return ""

        val filename = file.name
        val contentType = when {
            filename.endsWith(".m4a", ignoreCase = true) -> "audio/m4a"
            filename.endsWith(".mp3", ignoreCase = true) -> "audio/mp3"
            filename.endsWith(".wav", ignoreCase = true) -> "audio/wav"
            filename.endsWith(".aac", ignoreCase = true) -> "audio/aac"
            else -> "audio/m4a"
        }
        return HttpRetry.execute(serviceLabel) {
            transcribeWithWriter { out ->
                writeFileStreamPart(out, "file", filename, contentType, file)
            }
        }
    }

    @Throws(IOException::class)
    private fun transcribeWithWriter(
        writeAudioPart: (DataOutputStream) -> Unit
    ): String {
        val url = HttpEndpoint.parse(endpoint, "$serviceLabel endpoint")
        val conn = url.openConnection() as HttpURLConnection
        if (ignoreSsl) {
            SslUtils.applySslBypass(conn)
        }
        try {
            conn.requestMethod = "POST"
            if (apiKey.isNotEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
            val timeout = if (endpoint.contains("127.0.0.1") || endpoint.contains("localhost") || endpoint.contains("10.0.0.2")) 12000 else TIMEOUT_MS
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.doOutput = true

            val lang = customLanguage?.ifBlank { "es" } ?: java.util.Locale.getDefault().language.ifBlank { "es" }
            val whisperPrompt = "Comandos de voz en español: llamar a, marca a, enviar whatsapp a, mensaje para, toma nota, agrega a la lista, clima, recordatorio."

            DataOutputStream(conn.outputStream).use { out ->
                writeAudioPart(out)
                if (model.isNotEmpty()) {
                    writeTextPart(out, "model", model)
                }
                writeTextPart(out, "language", lang)
                writeTextPart(out, "prompt", whisperPrompt)
                writeTextPart(out, "temperature", "0.0")
                writeTextPart(out, "response_format", "json")
                out.writeBytes("--$BOUNDARY--\r\n")
                out.flush()
            }

            val status = conn.responseCode
            val body = readAll(if (status >= 400) conn.errorStream else conn.inputStream)
            if (status >= 400) {
                throw HttpRetry.statusError(
                    status,
                    "$serviceLabel returned $status: ${body.substring(0, Math.min(500, body.length))}"
                )
            }
            return extractText(body)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val BOUNDARY = "----myvuclientboundary"
        private const val TIMEOUT_MS = 30000
        private const val MIN_PCM_BYTES = 8000

        @Throws(IOException::class)
        private fun extractText(body: String): String {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) return ""
            try {
                val json = JSONObject(trimmed)
                val text = json.optString("text", "")
                if (text.isNotEmpty()) return text.trim()
                val transcription = json.optString("transcription", "")
                if (transcription.isNotEmpty()) return transcription.trim()
                return text.trim()
            } catch (e: JSONException) {
                if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                    return trimmed
                }
                throw IOException("unparseable transcription response: ${e.message}", e)
            }
        }

        @Throws(IOException::class)
        private fun writeFilePart(
            out: DataOutputStream,
            name: String,
            filename: String,
            contentType: String,
            data: ByteArray
        ) {
            out.writeBytes("--$BOUNDARY\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\n")
            out.writeBytes("Content-Type: $contentType\r\n\r\n")
            out.write(data)
            out.writeBytes("\r\n")
        }

        @Throws(IOException::class)
        private fun writeFileStreamPart(
            out: DataOutputStream,
            name: String,
            filename: String,
            contentType: String,
            file: File
        ) {
            out.writeBytes("--$BOUNDARY\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\n")
            out.writeBytes("Content-Type: $contentType\r\n\r\n")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                }
            }
            out.writeBytes("\r\n")
        }

        @Throws(IOException::class)
        private fun writeTextPart(out: DataOutputStream, name: String, value: String) {
            out.writeBytes("--$BOUNDARY\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
            out.write(value.toByteArray(StandardCharsets.UTF_8))
            out.writeBytes("\r\n")
        }

        @Throws(IOException::class)
        private fun readAll(input: InputStream?): String {
            if (input == null) return ""
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var count: Int
            while (input.read(buf).also { count = it } > 0) out.write(buf, 0, count)
            return String(out.toByteArray(), StandardCharsets.UTF_8)
        }
    }
}
