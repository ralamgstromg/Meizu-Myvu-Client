package com.myvu.client.media

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import com.myvu.client.service.MirrorNotificationListener
import com.myvu.client.ui.SendTrampolineActivity
import com.myvu.client.core.LockScreenHelper
import com.myvu.client.core.LogBus
import java.net.URLEncoder

enum class PlaybackAction {
    PLAY,
    PAUSE,
    NEXT,
    PREVIOUS,
    STOP
}

data class NowPlayingInfo(
    val title: String,
    val artist: String,
    val album: String,
    val packageName: String,
    val isPlaying: Boolean
)

object MediaPlaybackHelper {

    // Lista de paquetes conocidos para NewPipe y forks libres
    private val NEWPIPE_PACKAGES = listOf(
        "org.schabi.newpipe",
        "org.schabi.newpipe.debug",
        "org.polymorphicshade.tubular",
        "org.blayfeed.bravenewpipe"
    )

    // Lista de paquetes conocidos para OpenTune, InnerTune, RiMusic y ViMusic
    private val OPENTUNE_PACKAGES = listOf(
        "tune.music.opentune",
        "com.opentune.app",
        "org.opentune.android",
        "com.opentune.music",
        "com.zionhuang.music",
        "com.zionhuang.innertune",
        "it.fast4x.rimusic",
        "it.fast4x.vimusic",
        "oss.krtirtho.spotube",
        "com.gokadzev.musify"
    )

    private val SPOTIFY_PACKAGES = listOf("com.spotify.music", "com.spotify.lite")
    private val YOUTUBE_MUSIC_PACKAGES = listOf("com.google.android.apps.youtube.music")
    private val YOUTUBE_PACKAGES = listOf("com.google.android.youtube")
    private val DEEZER_PACKAGES = listOf("deezer.android.app")
    private val VLC_PACKAGES = listOf("org.videolan.vlc")

    /**
     * Obtiene el MediaController activo más relevante utilizando MediaSessionManager.
     */
    fun getActiveController(context: Context): MediaController? {
        return try {
            val mm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return null
            val comp = ComponentName(context, MirrorNotificationListener::class.java)
            val controllers = mm.getActiveSessions(comp) ?: return null
            if (controllers.isEmpty()) return null

            // Priorizar el que esté reproduciendo activamente
            controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_BUFFERING }
                ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
                ?: controllers.firstOrNull()
        } catch (e: Exception) {
            LogBus.warn("MediaPlaybackHelper -> Could not get active media sessions: ${e.message}")
            null
        }
    }

    /**
     * Extrae información de la canción que está sonando actualmente.
     */
    fun getNowPlaying(context: Context): NowPlayingInfo? {
        val controller = getActiveController(context) ?: return null
        return try {
            val metadata = controller.metadata
            val state = controller.playbackState
            val isPlaying = state?.state == PlaybackState.STATE_PLAYING

            val rawTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            val rawArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            val rawAlbum = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)

            if (rawTitle.isNullOrBlank() && rawArtist.isNullOrBlank()) {
                return null
            }

            NowPlayingInfo(
                title = rawTitle?.trim() ?: "Desconocido",
                artist = rawArtist?.trim() ?: "Artista desconocido",
                album = rawAlbum?.trim() ?: "",
                packageName = controller.packageName,
                isPlaying = isPlaying
            )
        } catch (e: Exception) {
            LogBus.warn("MediaPlaybackHelper -> Error reading now playing metadata: ${e.message}")
            null
        }
    }

    /**
     * Responde con texto amigable para TTS y visualización en HUD de la canción actual.
     */
    fun queryNowPlaying(context: Context): String {
        val info = getNowPlaying(context)
        if (info == null) {
            return "No hay ninguna canción reproduciéndose en este momento."
        }
        val appLabel = getAppLabel(context, info.packageName)
        val statusText = if (info.isPlaying) "Reproduciendo" else "En pausa"
        return if (info.artist.isNotBlank() && info.artist != "Artista desconocido") {
            "$statusText: ${info.title} de ${info.artist} en $appLabel."
        } else {
            "$statusText: ${info.title} en $appLabel."
        }
    }

    /**
     * Ejecuta una acción de reproducción sobre la sesión activa, o envía keycode por AudioManager como fallback.
     */
    fun executePlaybackAction(context: Context, action: PlaybackAction): Boolean {
        val controller = getActiveController(context)
        if (controller != null) {
            try {
                val tc = controller.transportControls
                when (action) {
                    PlaybackAction.PLAY -> tc.play()
                    PlaybackAction.PAUSE -> tc.pause()
                    PlaybackAction.NEXT -> tc.skipToNext()
                    PlaybackAction.PREVIOUS -> tc.skipToPrevious()
                    PlaybackAction.STOP -> tc.stop()
                }
                LogBus.log("MediaPlaybackHelper -> Executed $action on ${controller.packageName}")
                return true
            } catch (e: Exception) {
                LogBus.warn("MediaPlaybackHelper -> TransportControls $action failed: ${e.message}")
            }
        }

        // Fallback vía KeyEvent
        val keyCode = when (action) {
            PlaybackAction.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            PlaybackAction.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            PlaybackAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            PlaybackAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            PlaybackAction.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
        }
        sendMediaKeyEvent(context, keyCode)
        return true
    }

    fun pause(context: Context): Boolean = executePlaybackAction(context, PlaybackAction.PAUSE)
    fun resume(context: Context): Boolean = executePlaybackAction(context, PlaybackAction.PLAY)
    fun skipToNext(context: Context): Boolean = executePlaybackAction(context, PlaybackAction.NEXT)
    fun skipToPrevious(context: Context): Boolean = executePlaybackAction(context, PlaybackAction.PREVIOUS)
    fun stop(context: Context): Boolean = executePlaybackAction(context, PlaybackAction.STOP)

    /**
     * Realiza búsqueda y reproducción en una aplicación objetivo (NewPipe, OpenTune, Spotify, etc.)
     */
    fun playFromSearch(context: Context, appTarget: String?, query: String): String {
        if (query.isBlank()) return "Consulta de canción vacía."
        val resolvedApp = resolveAppPackage(context, appTarget)
        val appLabel = resolvedApp?.label ?: (appTarget?.replaceFirstChar { it.uppercase() } ?: "Música")

        LogBus.log("MediaPlaybackHelper -> playFromSearch query='$query' in app='${resolvedApp?.packageName ?: "generic"}'")

        try {
            when {
                // 1. NewPipe y forks libres
                resolvedApp != null && isNewPipePackage(resolvedApp.packageName) -> {
                    // NewPipe responde excelentemente al intent VIEW con URL de búsqueda de YouTube
                    val encoded = URLEncoder.encode(query, "UTF-8")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                        setPackage(resolvedApp.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    dispatchIntentSafely(context, intent)
                }

                // 2. OpenTune, InnerTune, RiMusic, ViMusic
                resolvedApp != null && isOpenTuneOrForks(resolvedApp.packageName) -> {
                    val mediaIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        putExtra(SearchManager.QUERY, query)
                        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                        setPackage(resolvedApp.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        dispatchIntentSafely(context, mediaIntent)
                    } catch (e: Exception) {
                        // Fallback a search intent
                        val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                            putExtra(SearchManager.QUERY, query)
                            setPackage(resolvedApp.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        dispatchIntentSafely(context, searchIntent)
                    }
                }

                // 3. Spotify
                resolvedApp != null && (resolvedApp.packageName.contains("spotify")) -> {
                    val spotUri = Uri.parse("spotify:search:" + URLEncoder.encode(query, "UTF-8"))
                    val spotIntent = Intent(Intent.ACTION_VIEW, spotUri).apply {
                        setPackage(resolvedApp.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    dispatchIntentSafely(context, spotIntent)
                }

                // 4. YouTube Music
                resolvedApp != null && (resolvedApp.packageName.contains("youtube.music")) -> {
                    val ytMusicUri = Uri.parse("https://music.youtube.com/search?q=" + URLEncoder.encode(query, "UTF-8"))
                    val ytIntent = Intent(Intent.ACTION_VIEW, ytMusicUri).apply {
                        setPackage(resolvedApp.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    dispatchIntentSafely(context, ytIntent)
                }

                // 5. YouTube oficial
                resolvedApp != null && (resolvedApp.packageName == "com.google.android.youtube") -> {
                    val ytUri = Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8"))
                    val ytIntent = Intent(Intent.ACTION_VIEW, ytUri).apply {
                        setPackage(resolvedApp.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    dispatchIntentSafely(context, ytIntent)
                }

                // 6. Aplicación resuelta genérica
                resolvedApp != null -> {
                    val mediaIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        putExtra(SearchManager.QUERY, query)
                        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                        setPackage(resolvedApp.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    dispatchIntentSafely(context, mediaIntent)
                }

                // 7. Lanzador genérico del sistema
                else -> {
                    val mediaIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        putExtra(SearchManager.QUERY, query)
                        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    dispatchIntentSafely(context, mediaIntent)
                }
            }

            // Enviar un KEYCODE_MEDIA_PLAY diferido a los 1200ms para asegurar el autoplay si la app lo requiere
            Handler(Looper.getMainLooper()).postDelayed({
                sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY)
            }, 1200L)

            return "Abriendo $appLabel y reproduciendo $query..."
        } catch (e: Exception) {
            LogBus.error("MediaPlaybackHelper -> Failed to play '$query' in $appLabel", e)
            return "No se pudo reproducir en $appLabel: ${e.message}"
        }
    }

    /**
     * Realiza búsqueda directa dentro de una app sin forzar inicio inmediato de reproducción.
     */
    fun searchInApp(context: Context, appTarget: String?, query: String): String {
        if (query.isBlank()) return "Término de búsqueda vacío."
        val resolvedApp = resolveAppPackage(context, appTarget)
        val appLabel = resolvedApp?.label ?: (appTarget?.replaceFirstChar { it.uppercase() } ?: "la app")

        LogBus.log("MediaPlaybackHelper -> searchInApp query='$query' in app='${resolvedApp?.packageName ?: "generic"}'")

        try {
            when {
                // NewPipe
                resolvedApp != null && isNewPipePackage(resolvedApp.packageName) -> {
                    val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                        putExtra(SearchManager.QUERY, query)
                        setPackage(resolvedApp.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        dispatchIntentSafely(context, searchIntent)
                    } catch (e: Exception) {
                        val encoded = URLEncoder.encode(query, "UTF-8")
                        val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                            setPackage(resolvedApp.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        dispatchIntentSafely(context, uriIntent)
                    }
                }

                // OpenTune / Clientes libres
                resolvedApp != null && isOpenTuneOrForks(resolvedApp.packageName) -> {
                    val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                        putExtra(SearchManager.QUERY, query)
                        setPackage(resolvedApp.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    dispatchIntentSafely(context, searchIntent)
                }

                // Spotify
                resolvedApp != null && resolvedApp.packageName.contains("spotify") -> {
                    val spotUri = Uri.parse("spotify:search:" + URLEncoder.encode(query, "UTF-8"))
                    val spotIntent = Intent(Intent.ACTION_VIEW, spotUri).apply {
                        setPackage(resolvedApp.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    dispatchIntentSafely(context, spotIntent)
                }

                // YouTube / YouTube Music
                resolvedApp != null && resolvedApp.packageName.contains("youtube") -> {
                    val base = if (resolvedApp.packageName.contains("music")) "https://music.youtube.com/search?q=" else "https://www.youtube.com/results?search_query="
                    val ytUri = Uri.parse(base + URLEncoder.encode(query, "UTF-8"))
                    val ytIntent = Intent(Intent.ACTION_VIEW, ytUri).apply {
                        setPackage(resolvedApp.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    dispatchIntentSafely(context, ytIntent)
                }

                else -> {
                    // Búsqueda genérica
                    val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                        putExtra(SearchManager.QUERY, query)
                        resolvedApp?.let { setPackage(it.packageName) }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    dispatchIntentSafely(context, searchIntent)
                }
            }
            return "Buscando $query en $appLabel..."
        } catch (e: Exception) {
            LogBus.error("MediaPlaybackHelper -> Search failed for '$query' in $appLabel", e)
            return "No se pudo buscar en $appLabel: ${e.message}"
        }
    }

    private fun dispatchIntentSafely(context: Context, intent: Intent) {
        val isLocked = LockScreenHelper.isDeviceLocked(context)
        if (isLocked) {
            LockScreenHelper.wakeUpScreen(context, "MYVU:Media")
            SendTrampolineActivity.launchWithKeyguardDismiss(context, intent)
        } else {
            context.startActivity(intent)
        }
    }

    fun sendMediaKeyEvent(context: Context, keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
        am.dispatchMediaKeyEvent(down)
        am.dispatchMediaKeyEvent(up)
        LogBus.log("MediaPlaybackHelper -> Sent media key $keyCode")
    }

    data class ResolvedApp(val packageName: String, val label: String)

    private fun resolveAppPackage(context: Context, targetName: String?): ResolvedApp? {
        val pm = context.packageManager
        val cleanName = (targetName ?: "").trim().lowercase()

        // 1. Mapeos de nombres directos
        val directCandidates = when {
            cleanName.contains("newpipe") || cleanName.contains("tubular") -> NEWPIPE_PACKAGES
            cleanName.contains("opentune") || cleanName.contains("open tune") ||
                    cleanName.contains("innertune") || cleanName.contains("vimusic") ||
                    cleanName.contains("rimusic") || cleanName.contains("spotube") -> OPENTUNE_PACKAGES
            cleanName.contains("spotify") -> SPOTIFY_PACKAGES
            cleanName.contains("youtube music") || cleanName.contains("yt music") -> YOUTUBE_MUSIC_PACKAGES
            cleanName.contains("youtube") -> YOUTUBE_PACKAGES
            cleanName.contains("deezer") -> DEEZER_PACKAGES
            cleanName.contains("vlc") -> VLC_PACKAGES
            else -> emptyList()
        }

        for (pkg in directCandidates) {
            try {
                val info = pm.getPackageInfo(pkg, 0)
                val label = pm.getApplicationLabel(info.applicationInfo ?: continue).toString()
                return ResolvedApp(pkg, label)
            } catch (ignored: Exception) { }
        }

        // 2. Si no hubo coincidencia directa pero se pasó un nombre, buscar en paquetes instalados
        if (cleanName.isNotBlank()) {
            val packages = pm.getInstalledPackages(0)
            for (p in packages) {
                val appInfo = p.applicationInfo ?: continue
                val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                val pkgName = p.packageName.lowercase()

                if (label.contains(cleanName) || pkgName.contains(cleanName)) {
                    return ResolvedApp(p.packageName, pm.getApplicationLabel(appInfo).toString())
                }
            }
        }

        return null
    }

    private fun isNewPipePackage(packageName: String): Boolean =
        NEWPIPE_PACKAGES.any { packageName.startsWith(it) }

    private fun isOpenTuneOrForks(packageName: String): Boolean =
        OPENTUNE_PACKAGES.any { packageName.startsWith(it) }

    private fun getAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}
