package com.myvu.client.skills.handlers

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
import com.myvu.client.core.ContactHelper
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

/**
 * Enhanced Native App Launcher Handler:
 * Opens installed Android applications by voice label, alias matrix, or package name.
 * Includes direct intent shortcuts for system apps (Camera, Settings, Clock, Alarms).
 */
class OpenAppHandler : SkillHandler {

    private val commonPackageAliases = mapOf(
        // Social & Messaging
        "whatsapp" to "com.whatsapp",
        "whatsapp business" to "com.whatsapp.w4b",
        "telegram" to "org.telegram.messenger",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "tiktok" to "com.zhiliaoapp.musically",
        "x" to "com.twitter.android",
        "twitter" to "com.twitter.android",
        "linkedin" to "com.linkedin.android",

        // Media & Entertainment
        "spotify" to "com.spotify.music",
        "opentune" to "com.zionhuang.music",
        "youtube" to "com.google.android.youtube",
        "youtube music" to "com.google.android.apps.youtube.music",
        "netflix" to "com.netflix.ninja",
        "disney" to "com.disney.disneyplus",
        "twitch" to "tv.twitch.android.app",

        // Navigation & Mobility
        "maps" to "com.google.android.apps.maps",
        "mapas" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "waze" to "com.waze",
        "uber" to "com.ubercab",
        "didi" to "com.didiglobal.passenger",
        "indrive" to "sinet.bm.transporter",
        "rappi" to "com.grability.rappi",

        // Productivity & Tools
        "chrome" to "com.android.chrome",
        "navegador" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "correo" to "com.google.android.gm",
        "outlook" to "com.microsoft.office.outlook",
        "calendario" to "com.google.android.calendar",
        "calendar" to "com.google.android.calendar",
        "calculadora" to "com.google.android.calculator",
        "fotos" to "com.google.android.apps.photos",
        "galeria" to "com.google.android.apps.photos",
        "reloj" to "com.google.android.deskclock",
        "contactos" to "com.google.android.contacts",
        "archivos" to "com.google.android.apps.nbu.files"
    )

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val appName = args.optString("app_name", "").trim()
            val packageName = args.optString("package_name", "").trim()

            if (appName.isEmpty() && packageName.isEmpty()) {
                return SkillResult(false, "Falta indicar el nombre o identificador de la aplicación a abrir.")
            }

            val pm = context.packageManager
            val normalizedApp = ContactHelper.normalize(appName)

            // 1. Direct system intent shortcuts
            when (normalizedApp) {
                "camara", "camera", "fotos", "tomar foto" -> {
                    val camIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (camIntent.resolveActivity(pm) != null) {
                        context.startActivity(camIntent)
                        return SkillResult(true, "📸 **Cámara abierta**.")
                    }
                }
                "ajustes", "configuracion", "settings" -> {
                    val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return SkillResult(true, "⚙️ **Ajustes del sistema abiertos**.")
                }
            }

            // 2. Explicit package_name
            if (packageName.isNotEmpty()) {
                val launchIntent = pm.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return SkillResult(true, "🚀 **Aplicación abierta**: Se lanzó `$packageName`.")
                }
            }

            // 3. Check alias dictionary
            val aliasPkg = commonPackageAliases[normalizedApp]
            if (aliasPkg != null) {
                val launchIntent = pm.getLaunchIntentForPackage(aliasPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return SkillResult(true, "🚀 **Aplicación abierta**: Se abrió **$appName**.")
                }
            }

            // 4. Search all installed launcher activities with normalized fuzzy matching
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            var bestTargetPkg: String? = null
            var bestLabel: String? = null

            for (ri in resolveInfos) {
                val rawLabel = ri.loadLabel(pm).toString()
                val normLabel = ContactHelper.normalize(rawLabel)
                val targetPkg = ri.activityInfo.packageName

                if (normLabel == normalizedApp) {
                    bestTargetPkg = targetPkg
                    bestLabel = rawLabel
                    break
                }
                if (normLabel.contains(normalizedApp) || normalizedApp.contains(normLabel)) {
                    if (bestTargetPkg == null) {
                        bestTargetPkg = targetPkg
                        bestLabel = rawLabel
                    }
                }
            }

            if (bestTargetPkg != null) {
                val launchIntent = pm.getLaunchIntentForPackage(bestTargetPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return SkillResult(true, "🚀 **Aplicación abierta**: Se abrió **$bestLabel**.")
                }
            }

            SkillResult(false, "No se encontró ninguna aplicación instalada con el nombre '$appName'.")
        } catch (e: Exception) {
            LogBus.error("OpenAppHandler -> Error launching application", e)
            SkillResult(false, "Error al abrir la aplicación: ${e.message}")
        }
    }
}
