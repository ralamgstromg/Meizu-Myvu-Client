package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed class WhisperDownloadState {
    object Idle : WhisperDownloadState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : WhisperDownloadState()
    object Completed : WhisperDownloadState()
    data class Error(val message: String) : WhisperDownloadState()
}

class WhisperModelDownloader(
    private val context: Context,
    private val modelOption: WhisperModelOption
) {
    private val downloadExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "whisper-downloader").apply { isDaemon = true }
    }

    private val targetFile: File get() = WhisperLocalClient.getModelFile(context, modelOption.fileName)

    fun getInitialState(): WhisperDownloadState {
        return if (targetFile.exists() && targetFile.length() > 0) {
            WhisperDownloadState.Completed
        } else {
            WhisperDownloadState.Idle
        }
    }

    fun deleteModel(): Boolean {
        if (targetFile.exists()) {
            return targetFile.delete()
        }
        return false
    }

    fun startDownload(
        hfToken: String? = null,
        customUrl: String? = null,
        onProgress: (WhisperDownloadState) -> Unit
    ) {
        if (targetFile.exists() && targetFile.length() > 0) {
            onProgress(WhisperDownloadState.Completed)
            return
        }

        downloadExecutor.execute {
            var input: InputStream? = null
            var output: FileOutputStream? = null
            var connection: HttpURLConnection? = null
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.download")

            try {
                val urlString = if (!customUrl.isNullOrBlank()) customUrl.trim() else modelOption.downloadUrl
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true

                val token = if (!hfToken.isNullOrBlank()) hfToken.trim() else Prefs.gemmaHfToken(context).trim()
                if (token.isNotEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer $token")
                }

                connection.connect()

                if (connection.responseCode !in 200..299) {
                    val msg = "Error al descargar (HTTP ${connection.responseCode}): ${connection.responseMessage}"
                    LogBus.error(msg)
                    onProgress(WhisperDownloadState.Error(msg))
                    return@execute
                }

                val fileLength = connection.contentLengthLong
                input = connection.inputStream
                output = FileOutputStream(tempFile)

                val data = ByteArray(8192)
                var total: Long = 0
                var count: Int
                var lastProgress = 0

                while (input.read(data).also { count = it } != -1) {
                    output.write(data, 0, count)
                    total += count
                    if (fileLength > 0) {
                        val progress = ((total * 100) / fileLength).toInt()
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(WhisperDownloadState.Downloading(progress, total, fileLength))
                        }
                    }
                }

                output.flush()
                output.close()
                output = null
                input.close()
                input = null

                if (tempFile.renameTo(targetFile)) {
                    LogBus.log("Whisper model download complete: ${targetFile.absolutePath}")
                    onProgress(WhisperDownloadState.Completed)
                } else {
                    val msg = "No se pudo renombrar el archivo temporal de descarga de Whisper"
                    LogBus.error(msg)
                    onProgress(WhisperDownloadState.Error(msg))
                }

            } catch (e: Exception) {
                LogBus.error("Error durante descarga del modelo Whisper", e)
                if (tempFile.exists()) tempFile.delete()
                onProgress(WhisperDownloadState.Error(e.message ?: "Error desconocido al descargar Whisper"))
            } finally {
                try { output?.close() } catch (_: Exception) {}
                try { input?.close() } catch (_: Exception) {}
                connection?.disconnect()
            }
        }
    }
}
