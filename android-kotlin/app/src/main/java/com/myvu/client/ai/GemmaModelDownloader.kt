package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

sealed class GemmaDownloadState {
    object NotDownloaded : GemmaDownloadState()
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long, val progressPercent: Int) : GemmaDownloadState()
    object Completed : GemmaDownloadState()
    data class Error(val message: String) : GemmaDownloadState()
}

class GemmaModelDownloader(
    private val context: Context,
    private val modelOption: GemmaModelOption = GemmaLocalClient.DEFAULT_OPTION
) {
    private val isCancelled = AtomicBoolean(false)
    private var downloadThread: Thread? = null

    val targetFile: File get() = GemmaLocalClient.getModelFile(context, modelOption.fileName)

    fun getInitialState(): GemmaDownloadState {
        return if (targetFile.exists() && targetFile.length() > 0) {
            GemmaDownloadState.Completed
        } else {
            GemmaDownloadState.NotDownloaded
        }
    }

    fun startDownload(
        onProgress: (GemmaDownloadState) -> Unit
    ) {
        if (targetFile.exists() && targetFile.length() > 0) {
            onProgress(GemmaDownloadState.Completed)
            return
        }

        isCancelled.set(false)
        val tempFile = File(targetFile.parentFile, "${modelOption.fileName}.tmp")

        downloadThread = thread(name = "gemma-downloader") {
            var connection: HttpURLConnection? = null
            try {
                onProgress(GemmaDownloadState.Downloading(0, modelOption.sizeBytes, 0))
                val customUrl = Prefs.gemmaCustomUrl(context).trim()
                var currentUrl = if (customUrl.isNotBlank()) customUrl else modelOption.downloadUrl
                val hfToken = Prefs.gemmaHfToken(context).trim()
                var redirects = 0

                while (redirects < 5) {
                    val url = URL(currentUrl)
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 30_000
                        readTimeout = 30_000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
                        setRequestProperty("Accept", "*/*")
                        if (hfToken.isNotBlank()) {
                            setRequestProperty("Authorization", "Bearer $hfToken")
                        }
                    }
                    connection.connect()

                    val code = connection.responseCode
                    if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                        val loc = connection.getHeaderField("Location")
                        connection.disconnect()
                        if (loc.isNullOrEmpty()) break
                        currentUrl = loc
                        redirects++
                        continue
                    }
                    break
                }

                val activeConnection = connection ?: throw IOException("HTTP connection failed")
                val responseCode = activeConnection.responseCode
                if (responseCode !in 200..299) {
                    throw IOException("HTTP error $responseCode: ${activeConnection.responseMessage}")
                }

                val contentLength = activeConnection.contentLengthLong.let { if (it > 0) it else modelOption.sizeBytes }
                val inputStream = activeConnection.inputStream
                val outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var totalDownloaded = 0L
                var lastReportPercent = -1

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled.get()) {
                        outputStream.close()
                        inputStream.close()
                        if (tempFile.exists()) tempFile.delete()
                        onProgress(GemmaDownloadState.NotDownloaded)
                        return@thread
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    totalDownloaded += bytesRead

                    val percent = if (contentLength > 0) ((totalDownloaded * 100) / contentLength).toInt() else 0
                    if (percent != lastReportPercent) {
                        lastReportPercent = percent
                        onProgress(GemmaDownloadState.Downloading(totalDownloaded, contentLength, percent))
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                if (isCancelled.get()) {
                    if (tempFile.exists()) tempFile.delete()
                    onProgress(GemmaDownloadState.NotDownloaded)
                    return@thread
                }

                if (tempFile.exists()) {
                    if (targetFile.exists()) targetFile.delete()
                    val success = tempFile.renameTo(targetFile)
                    if (!success) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                }

                LogBus.log("GEMMA_DOWNLOAD_SUCCESS model=${modelOption.fileName} bytes=$totalDownloaded")
                onProgress(GemmaDownloadState.Completed)

            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                if (isCancelled.get()) {
                    onProgress(GemmaDownloadState.NotDownloaded)
                } else {
                    LogBus.error("GEMMA_DOWNLOAD_ERROR model=${modelOption.fileName}", e)
                    onProgress(GemmaDownloadState.Error(e.message ?: "Download failed"))
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun cancelDownload() {
        isCancelled.set(true)
        downloadThread?.interrupt()
    }

    fun deleteModel(): Boolean {
        cancelDownload()
        val deleted = if (targetFile.exists()) targetFile.delete() else true
        val tempFile = File(targetFile.parentFile, "${modelOption.fileName}.tmp")
        if (tempFile.exists()) tempFile.delete()
        return deleted
    }
}
