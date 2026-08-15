package com.myvu.client.recorder

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.myvu.client.core.LogBus
import java.io.File

class AudioPlayerManager {

    interface Listener {
        fun onPlaybackStarted(path: String, durationMs: Int)
        fun onPlaybackProgress(currentMs: Int, durationMs: Int)
        fun onPlaybackPaused()
        fun onPlaybackResumed()
        fun onPlaybackStopped()
        fun onPlaybackCompleted()
        fun onPlaybackError(message: String)
    }

    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioPath: String? = null
    private var isPaused: Boolean = false
    private var currentSpeed: Float = 1.0f

    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    var listener: Listener? = null

    val isPlaying: Boolean
        get() = try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }

    val isPausedState: Boolean get() = isPaused
    val currentPath: String? get() = currentAudioPath
    val playbackSpeed: Float get() = currentSpeed

    fun play(audioPath: String, startFromMs: Int = 0) {
        val file = File(audioPath)
        if (!file.exists() || file.length() == 0L) {
            LogBus.warn("AudioPlayerManager: File does not exist or empty: $audioPath")
            listener?.onPlaybackError("Archivo de audio no disponible")
            return
        }

        // If already playing the same file and was paused, resume
        if (currentAudioPath == audioPath && isPaused && mediaPlayer != null) {
            resume()
            return
        }

        stop()

        try {
            val player = MediaPlayer()
            player.setDataSource(audioPath)
            player.prepare()

            if (startFromMs > 0 && startFromMs < player.duration) {
                player.seekTo(startFromMs)
            }

            applySpeed(player, currentSpeed)

            player.setOnCompletionListener {
                stopProgressPolling()
                isPaused = false
                LogBus.log("AudioPlayerManager: Playback completed")
                listener?.onPlaybackCompleted()
            }

            player.setOnErrorListener { _, what, extra ->
                LogBus.error("AudioPlayerManager: MediaPlayer error what=$what extra=$extra", null)
                stop()
                listener?.onPlaybackError("Error reproduciendo audio ($what, $extra)")
                true
            }

            player.start()
            mediaPlayer = player
            currentAudioPath = audioPath
            isPaused = false

            val duration = player.duration
            LogBus.log("AudioPlayerManager: Playing $audioPath (duration=${duration}ms)")
            listener?.onPlaybackStarted(audioPath, duration)
            startProgressPolling()

        } catch (e: Exception) {
            LogBus.error("AudioPlayerManager: Failed to play audio", e)
            releasePlayer()
            listener?.onPlaybackError("Error al iniciar reproducción: ${e.message}")
        }
    }

    fun pause() {
        if (isPlaying) {
            try {
                mediaPlayer?.pause()
                isPaused = true
                stopProgressPolling()
                LogBus.log("AudioPlayerManager: Paused playback")
                listener?.onPlaybackPaused()
            } catch (e: Exception) {
                LogBus.error("AudioPlayerManager: Error pausing playback", e)
            }
        }
    }

    fun resume() {
        if (isPaused && mediaPlayer != null) {
            try {
                applySpeed(mediaPlayer!!, currentSpeed)
                mediaPlayer?.start()
                isPaused = false
                LogBus.log("AudioPlayerManager: Resumed playback")
                listener?.onPlaybackResumed()
                startProgressPolling()
            } catch (e: Exception) {
                LogBus.error("AudioPlayerManager: Error resuming playback", e)
            }
        }
    }

    fun togglePlayPause(audioPath: String) {
        if (currentAudioPath == audioPath && isPlaying) {
            pause()
        } else if (currentAudioPath == audioPath && isPaused) {
            resume()
        } else {
            play(audioPath)
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
        } catch (e: Exception) {
            LogBus.warn("AudioPlayerManager: Seek failed: ${e.message}")
        }
    }

    fun setSpeed(speed: Float) {
        currentSpeed = speed
        mediaPlayer?.let {
            if (isPlaying || isPaused) {
                applySpeed(it, speed)
            }
        }
    }

    private fun applySpeed(player: MediaPlayer, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = player.playbackParams ?: PlaybackParams()
                params.speed = speed
                player.playbackParams = params
            } catch (e: Exception) {
                LogBus.warn("AudioPlayerManager: Setting playback speed failed: ${e.message}")
            }
        }
    }

    fun stop() {
        stopProgressPolling()
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            releasePlayer()
        }
        isPaused = false
        currentAudioPath = null
        listener?.onPlaybackStopped()
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        progressRunnable = object : Runnable {
            override fun run() {
                val player = mediaPlayer
                if (player != null && (player.isPlaying || isPaused)) {
                    try {
                        val current = player.currentPosition
                        val total = player.duration
                        listener?.onPlaybackProgress(current, total)
                    } catch (e: Exception) {
                        // ignore
                    }
                    mainHandler.postDelayed(this, 200)
                }
            }
        }
        mainHandler.post(progressRunnable!!)
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // ignore
        }
        mediaPlayer = null
    }
}
