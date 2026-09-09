package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.media.MediaPlaybackHelper
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

/**
 * Skill Handler for Media and Playback Control:
 * Controls playback, searches, and status queries across NewPipe, OpenTune, Spotify, YouTube Music, etc.
 */
class AppMediaControlHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val action = args.optString("action", "now_playing").lowercase().trim()
        val query = args.optString("query", "").trim()
        val targetApp = args.optString("target_app", "").trim()

        return when (action) {
            "play_search", "play", "reproduce" -> {
                if (query.isBlank()) {
                    MediaPlaybackHelper.resume(context)
                    SkillResult(true, "▶️ **Reproduciendo música**.")
                } else {
                    val resp = MediaPlaybackHelper.playFromSearch(context, targetApp.ifBlank { null }, query)
                    SkillResult(true, "🎵 **$resp**")
                }
            }
            "search", "buscar" -> {
                if (query.isBlank()) {
                    SkillResult(false, "Falta especificar el término de búsqueda.")
                } else {
                    val resp = MediaPlaybackHelper.searchInApp(context, targetApp.ifBlank { null }, query)
                    SkillResult(true, "🔍 **$resp**")
                }
            }
            "pause", "pausar" -> {
                MediaPlaybackHelper.pause(context)
                SkillResult(true, "⏸️ **Música pausada**.")
            }
            "resume", "reanudar" -> {
                MediaPlaybackHelper.resume(context)
                SkillResult(true, "▶️ **Música reanudada**.")
            }
            "next", "siguiente", "skip" -> {
                MediaPlaybackHelper.skipToNext(context)
                SkillResult(true, "⏭️ **Siguiente canción**.")
            }
            "previous", "prev", "anterior" -> {
                MediaPlaybackHelper.skipToPrevious(context)
                SkillResult(true, "⏮️ **Canción anterior**.")
            }
            "stop", "detener" -> {
                MediaPlaybackHelper.stop(context)
                SkillResult(true, "⏹️ **Música detenida**.")
            }
            "now_playing", "info", "estado" -> {
                val info = MediaPlaybackHelper.queryNowPlaying(context)
                SkillResult(true, "🎧 $info")
            }
            else -> {
                val info = MediaPlaybackHelper.queryNowPlaying(context)
                SkillResult(true, "🎧 $info")
            }
        }
    }
}
