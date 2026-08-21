package com.myvu.client.skills.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

/**
 * Native App Launcher Handler:
 * Opens installed Android applications by app label or package name.
 */
class OpenAppHandler : SkillHandler {

    private val commonPackageAliases = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "spotify" to "com.spotify.music",
        "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "mapas" to "com.google.android.apps.maps",
        "maps" to "com.google.android.apps.maps",
        "fotos" to "com.google.android.apps.photos",
        "galeria" to "com.google.android.apps.photos",
        "calendario" to "com.google.android.calendar",
        "calculadora" to "com.google.android.calculator"
    )

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val appName = args.optString("app_name", "").trim()
            val packageName = args.optString("package_name", "").trim()

            if (appName.isEmpty() && packageName.isEmpty()) {
                return SkillResult(false, "Falta indicar el nombre de la aplicación ('app_name').")
            }

            val pm = context.packageManager

            // 1. Try explicit package_name if specified
            if (packageName.isNotEmpty()) {
                val launchIntent = pm.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return SkillResult(true, "🚀 **Aplicación abierta**: Se lanzó `$packageName`.")
                }
            }

            // 2. Try alias map
            val cleanAppName = appName.lowercase().trim()
            val aliasPkg = commonPackageAliases[cleanAppName]
            if (aliasPkg != null) {
                val launchIntent = pm.getLaunchIntentForPackage(aliasPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return SkillResult(true, "🚀 **Aplicación abierta**: Se abrió **$appName**.")
                }
            }

            // 3. Scan all launcher activities for matching label
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            for (ri in resolveInfos) {
                val label = ri.loadLabel(pm).toString().lowercase()
                if (label.contains(cleanAppName) || cleanAppName.contains(label)) {
                    val targetPkg = ri.activityInfo.packageName
                    val launchIntent = pm.getLaunchIntentForPackage(targetPkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        val actualLabel = ri.loadLabel(pm).toString()
                        return SkillResult(true, "🚀 **Aplicación abierta**: Se lanzó **$actualLabel**.")
                    }
                }
            }

            SkillResult(false, "No se encontró ninguna aplicación instalada con el nombre '$appName'.")
        } catch (e: Exception) {
            LogBus.error("OpenAppHandler -> Error launching application", e)
            SkillResult(false, "Error al abrir la aplicación: ${e.message}")
        }
    }
}
