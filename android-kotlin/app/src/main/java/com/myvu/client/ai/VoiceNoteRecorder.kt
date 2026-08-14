package com.myvu.client.ai

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * VoiceNoteRecorder manages MediaRecorder audio recording, file saving to
 * context.filesDir/voice_notes/voice_note_<timestamp>.m4a, automatic STT transcription
 * via SttProvider / OpenAiTranscriptionClient, and audio playback via MediaPlayer.
 */
class VoiceNoteRecorder(private val context: Context) {

    fun interface TranscriptionCallback {
        fun onComplete(audioPath: String, transcript: String)
    }

    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null
    private var isRecording: Boolean = false
    private var recordingStartTime: Long = 0L
    private var mediaPlayer: MediaPlayer? = null
    private val androidSpeech: AndroidSpeechEngine = AndroidSpeechRecognizer(context)
    private var nativeTranscript: String? = null
    private var nativeFinished = false
    private var pendingNativeResult: ((String, String) -> Unit)? = null
    private var nativeSessionId = 0L

    private fun nextNativeSession(): Long {
        nativeSessionId++
        return nativeSessionId
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    val isCurrentlyRecording: Boolean get() = isRecording
    val activeAudioFile: File? get() = currentAudioFile

    /**
     * Starts audio recording to context.filesDir/voice_notes/voice_note_<timestamp>.m4a
     * @return Output File if recording started successfully, null otherwise.
     */
    fun startRecording(): File? {
        if (isRecording) {
            LogBus.warn("VoiceNoteRecorder: Already recording, stopping previous session")
            stopRecording { _, _ -> }
        }

        val voiceNotesDir = File(context.filesDir, VOICE_NOTES_DIR)
        if (!voiceNotesDir.exists()) {
            voiceNotesDir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val file = File(voiceNotesDir, "voice_note_$timestamp.m4a")

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
            currentAudioFile = file
            isRecording = true
            recordingStartTime = System.currentTimeMillis()

            if (SttProvider.fromId(Prefs.sttProvider(context)).isNative) {
                val session = nextNativeSession()
                nativeTranscript = null
                nativeFinished = false
                val started = androidSpeech.start(
                    java.util.Locale.getDefault().toLanguageTag(),
                    onResult = { text ->
                        if (session != nativeSessionId) return@start
                        nativeTranscript = text.trim().ifEmpty { FALLBACK_TRANSCRIPT }
                        nativeFinished = true
                        completeNativeIfReady()
                    },
                    onError = { _, message ->
                        if (session != nativeSessionId) return@start
                        LogBus.warn("VoiceNoteRecorder: Android STT failed: $message")
                        nativeTranscript = FALLBACK_TRANSCRIPT
                        nativeFinished = true
                        completeNativeIfReady()
                    }
                )
                if (!started) {
                    nativeTranscript = FALLBACK_TRANSCRIPT
                    nativeFinished = true
                }
            }

            LogBus.log("VoiceNoteRecorder: Recording started -> ${file.name}")
            file
        } catch (e: Exception) {
            LogBus.error("VoiceNoteRecorder: Failed to start recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            currentAudioFile = null
            null
        }
    }

    /**
     * Stops audio recording and triggers asynchronous STT transcription.
     */
    fun stopRecording(callback: TranscriptionCallback) {
        stopRecording { audioPath, transcript ->
            callback.onComplete(audioPath, transcript)
        }
    }

    /**
     * Stops audio recording and triggers asynchronous STT transcription with lambda callback.
     */
    fun stopRecording(onResult: (audioPath: String, transcript: String) -> Unit) {
        if (!isRecording && currentAudioFile == null) {
            onResult("", FALLBACK_TRANSCRIPT)
            return
        }

        try {
            mediaRecorder?.stop()
            LogBus.log("VoiceNoteRecorder: Recording stopped successfully")
        } catch (e: Exception) {
            LogBus.error("VoiceNoteRecorder: Error stopping MediaRecorder", e)
        } finally {
            try {
                mediaRecorder?.release()
            } catch (ignored: Exception) {}
            mediaRecorder = null
            isRecording = false
        }

        val file = currentAudioFile
        if (file == null || !file.exists() || file.length() == 0L) {
            LogBus.warn("VoiceNoteRecorder: Audio file is missing or empty")
            onResult("", FALLBACK_TRANSCRIPT)
            return
        }

        val audioPath = file.absolutePath
        if (SttProvider.fromId(Prefs.sttProvider(context)).isNative) {
            pendingNativeResult = onResult
            completeNativeIfReady(audioPath)
            return
        }

        executor.execute {
            val transcript = performSttTranscription(file)
            mainHandler.post {
                onResult(audioPath, transcript)
            }
        }
    }

    private fun completeNativeIfReady(audioPath: String = currentAudioFile?.absolutePath ?: "") {
        if (!nativeFinished || pendingNativeResult == null) return
        val callback = pendingNativeResult ?: return
        pendingNativeResult = null
        callback(audioPath, nativeTranscript ?: FALLBACK_TRANSCRIPT)
    }

    /**
     * Cancels recording and deletes the temporary audio file if it exists.
     */
    fun cancelRecording() {
        nextNativeSession()
        androidSpeech.cancel()
        pendingNativeResult = null
        nativeFinished = false
        if (isRecording) {
            try {
                mediaRecorder?.stop()
            } catch (ignored: Exception) {
            } finally {
                try {
                    mediaRecorder?.release()
                } catch (ignored: Exception) {}
                mediaRecorder = null
                isRecording = false
            }
        }

        currentAudioFile?.let { file ->
            if (file.exists()) {
                file.delete()
                LogBus.log("VoiceNoteRecorder: Deleted cancelled recording file ${file.name}")
            }
        }
        currentAudioFile = null
    }

    /**
     * Releases all resources held by this recorder.
     * Call from Activity.onDestroy() to prevent thread leaks.
     */
    fun shutdown() {
        cancelRecording()
        stopPlayback()
        androidSpeech.destroy()
        executor.shutdownNow()
    }

    /**
     * Returns the duration of the current recording in seconds.
     */
    fun getRecordingDurationSeconds(): Int {
        if (!isRecording || recordingStartTime == 0L) return 0
        return ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
    }

    /**
     * Plays audio from the given file path using MediaPlayer.
     */
    fun playAudio(audioPath: String, onCompletion: (() -> Unit)? = null) {
        stopPlayback()
        mediaPlayer = playAudio(context, audioPath, onCompletion)
    }

    /**
     * Stops any ongoing audio playback.
     */
    fun stopPlayback() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (e: Exception) {
                LogBus.warn("VoiceNoteRecorder: Error releasing MediaPlayer: ${e.message}")
            }
        }
        mediaPlayer = null
    }

    private fun performSttTranscription(file: File): String {
        return try {
            val providerId = Prefs.sttProvider(context)
            val provider = SttProvider.fromId(providerId)
            val apiKey = Prefs.sttApiKey(context, providerId)
            val storedModel = Prefs.sttModel(context, providerId).trim()
            val model = if (storedModel.isEmpty()) provider.defaultModel else storedModel
            val storedEndpoint = Prefs.sttEndpoint(context, providerId).trim()
            val endpoint = if (storedEndpoint.isEmpty()) provider.defaultEndpoint else storedEndpoint
            val ignoreSsl = Prefs.ignoreSsl(context)

            val client = OpenAiTranscriptionClient(
                endpoint, model, apiKey, provider.label, ignoreSsl
            )

            if (!client.isConfigured()) {
                LogBus.warn("VoiceNoteRecorder: STT client $providerId is not fully configured")
                return FALLBACK_TRANSCRIPT
            }

            val text = client.transcribeAudioFile(file)
            if (text.isBlank()) {
                FALLBACK_TRANSCRIPT
            } else {
                text.trim()
            }
        } catch (e: Exception) {
            LogBus.error("VoiceNoteRecorder: STT transcription failed", e)
            FALLBACK_TRANSCRIPT
        }
    }

    companion object {
        const val VOICE_NOTES_DIR = "voice_notes"
        const val FALLBACK_TRANSCRIPT = "[Nota de voz sin transcripción]"

        /**
         * Helper method to play audio using MediaPlayer.
         */
        @JvmStatic
        @JvmOverloads
        fun playAudio(
            context: Context,
            audioPath: String,
            onCompletion: (() -> Unit)? = null
        ): MediaPlayer? {
            val file = File(audioPath)
            if (!file.exists()) {
                LogBus.warn("VoiceNoteRecorder: Audio file does not exist at $audioPath")
                onCompletion?.invoke()
                return null
            }

            return try {
                val player = MediaPlayer()
                try {
                    player.setDataSource(audioPath)
                    player.prepare()
                    player.setOnCompletionListener {
                        onCompletion?.invoke()
                        it.release()
                    }
                    player.setOnErrorListener { mp, _, _ ->
                        onCompletion?.invoke()
                        mp.release()
                        true
                    }
                    player.start()
                    player
                } catch (e: Exception) {
                    LogBus.error("VoiceNoteRecorder: Failed to play audio at $audioPath", e)
                    player.release()
                    onCompletion?.invoke()
                    null
                }
            } catch (e: Exception) {
                LogBus.error("VoiceNoteRecorder: Failed to create MediaPlayer for $audioPath", e)
                onCompletion?.invoke()
                null
            }
        }
    }
}
