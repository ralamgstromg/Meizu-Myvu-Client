package com.myvu.client.recorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.myvu.client.core.LogBus
import java.io.File

class MeetingAudioRecorder(private val context: Context) {

    interface Listener {
        fun onRecordingStarted(file: File)
        fun onRecordingProgress(durationMs: Long, amplitude: Int)
        fun onRecordingPaused()
        fun onRecordingResumed()
        fun onRecordingStopped(file: File, durationMs: Long, fileSizeBytes: Long)
        fun onRecordingError(message: String, exception: Throwable?)
    }

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    private var isPaused = false
    private var wakeLock: PowerManager.WakeLock? = null

    private var startTimeMs = 0L
    private var accumulatedDurationMs = 0L
    private var pauseStartTimeMs = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    var listener: Listener? = null

    val isCurrentlyRecording: Boolean get() = isRecording
    val isCurrentlyPaused: Boolean get() = isPaused
    val currentOutputFile: File? get() = outputFile

    fun startRecording(customTitle: String? = null): File? {
        if (isRecording) {
            LogBus.warn("MeetingAudioRecorder: Already recording, stopping previous session")
            stopRecording()
        }

        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val safeTitle = customTitle?.trim()?.replace(Regex("[^a-zA-Z0-9_-]"), "_")?.take(30)
        val filename = if (!safeTitle.isNullOrBlank()) {
            "rec_${safeTitle}_$timestamp.m4a"
        } else {
            "rec_$timestamp.m4a"
        }

        val file = File(dir, filename)
        outputFile = file

        return try {
            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            isRecording = true
            isPaused = false
            startTimeMs = System.currentTimeMillis()
            accumulatedDurationMs = 0L

            acquireWakeLock()
            startProgressPolling()
            LogBus.log("MeetingAudioRecorder: Recording started -> ${file.name}")
            listener?.onRecordingStarted(file)
            file
        } catch (e: Exception) {
            LogBus.error("MeetingAudioRecorder: Failed to start recording", e)
            releaseWakeLock()
            releaseRecorder()
            listener?.onRecordingError("Error al iniciar grabación: ${e.message}", e)
            null
        }
    }

    fun pauseRecording() {
        if (!isRecording || isPaused) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.pause()
                isPaused = true
                pauseStartTimeMs = System.currentTimeMillis()
                accumulatedDurationMs += (pauseStartTimeMs - startTimeMs)
                LogBus.log("MeetingAudioRecorder: Recording paused")
                listener?.onRecordingPaused()
            }
        } catch (e: Exception) {
            LogBus.error("MeetingAudioRecorder: Failed to pause", e)
        }
    }

    fun resumeRecording() {
        if (!isRecording || !isPaused) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.resume()
                isPaused = false
                startTimeMs = System.currentTimeMillis()
                LogBus.log("MeetingAudioRecorder: Recording resumed")
                listener?.onRecordingResumed()
            }
        } catch (e: Exception) {
            LogBus.error("MeetingAudioRecorder: Failed to resume", e)
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null

        stopProgressPolling()
        releaseWakeLock()
        val file = outputFile
        val totalDuration = if (isPaused) {
            accumulatedDurationMs
        } else {
            accumulatedDurationMs + (System.currentTimeMillis() - startTimeMs)
        }

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            LogBus.error("MeetingAudioRecorder: Error stopping MediaRecorder", e)
        } finally {
            releaseRecorder()
        }

        isRecording = false
        isPaused = false

        if (file != null && file.exists() && file.length() > 0) {
            val size = file.length()
            LogBus.log("MeetingAudioRecorder: Saved ${file.name} ($size bytes, ${totalDuration}ms)")
            listener?.onRecordingStopped(file, totalDuration, size)
            return file
        } else {
            LogBus.warn("MeetingAudioRecorder: Recorded file missing or empty")
            listener?.onRecordingError("Archivo de audio inválido o vacío", null)
            return null
        }
    }

    fun cancelRecording() {
        stopProgressPolling()
        releaseWakeLock()
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // ignore
        } finally {
            releaseRecorder()
        }
        isRecording = false
        isPaused = false
        outputFile?.let {
            if (it.exists()) it.delete()
        }
        outputFile = null
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "myvu:meeting_recording")
            }
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire(2 * 60 * 60 * 1000L) // Max 2 hours safety timeout
                    LogBus.log("MeetingAudioRecorder: Acquired PARTIAL_WAKE_LOCK")
                }
            }
        } catch (e: Exception) {
            LogBus.warn("MeetingAudioRecorder: Failed to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    LogBus.log("MeetingAudioRecorder: Released PARTIAL_WAKE_LOCK")
                }
            }
        } catch (e: Exception) {
            LogBus.warn("MeetingAudioRecorder: Failed to release WakeLock: ${e.message}")
        }
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        progressRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val currentDuration = if (isPaused) {
                        accumulatedDurationMs
                    } else {
                        accumulatedDurationMs + (System.currentTimeMillis() - startTimeMs)
                    }

                    val amplitude = try {
                        if (!isPaused) mediaRecorder?.maxAmplitude ?: 0 else 0
                    } catch (e: Exception) {
                        0
                    }

                    listener?.onRecordingProgress(currentDuration, amplitude)
                    mainHandler.postDelayed(this, 100)
                }
            }
        }
        mainHandler.post(progressRunnable!!)
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            LogBus.warn("MeetingAudioRecorder: Error releasing MediaRecorder: ${e.message}")
        }
        mediaRecorder = null
    }

    companion object {
        const val DIR_NAME = "voice_recordings"
    }
}
