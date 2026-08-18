package com.myvu.client.plugin.tasker.action

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.myvu.client.app.feature.Notifications
import com.myvu.client.core.GlassesConfig
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.plugin.tasker.TaskerAction
import com.myvu.client.plugin.tasker.TaskerBundleManager
import com.myvu.client.plugin.tasker.TaskerConstants
import com.myvu.client.service.ConnectionManager
import com.myvu.client.service.MyvuService

class TaskerActionReceiver : BroadcastReceiver() {

    fun interface ActionExecutor {
        fun execute(context: Context, action: TaskerAction, connection: ConnectionManager?): Boolean
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val actionName = intent.action
        if (actionName != TaskerConstants.ACTION_FIRE_SETTING &&
            actionName != TaskerConstants.BROADCAST_ACTION
        ) {
            LogBus.trace("TaskerActionReceiver: ignoring action $actionName")
            return
        }

        val rawBundle = intent.getBundleExtra(TaskerConstants.EXTRA_BUNDLE) ?: intent.extras
        var action = TaskerBundleManager.extractAction(rawBundle)
        if (action == null || action.type.isBlank()) {
            LogBus.warn("TaskerActionReceiver: received fire setting without valid action bundle")
            return
        }

        // Variable resolution (if pass-through bundle exists)
        val passThrough = intent.getBundleExtra(TaskerConstants.EXTRA_TASKER_PASS_THROUGH)
        val variables = extractVariablesFromBundles(passThrough, intent.extras)
        if (variables.isNotEmpty()) {
            action = TaskerBundleManager.resolveActionVariables(action, variables)
        }

        LogBus.log("TaskerActionReceiver: executing action type '${action.type}'")
        val executor = customExecutor ?: defaultExecutor
        val conn = MyvuService.activeConnection()
        executor.execute(context, action, conn)
    }

    @Suppress("DEPRECATION")
    private fun extractVariablesFromBundles(vararg bundles: Bundle?): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (b in bundles) {
            if (b == null) continue
            for (key in b.keySet()) {
                val value = b.get(key)?.toString()
                if (value != null) {
                    if (key.startsWith("%")) {
                        map[key] = value
                    } else {
                        map["%$key"] = value
                    }
                }
            }
        }
        return map
    }

    companion object {
        @Volatile
        var customExecutor: ActionExecutor? = null

        val defaultExecutor: ActionExecutor = ActionExecutor { context, action, conn ->
            when (action.type) {
                TaskerConstants.TYPE_SHOW_HUD -> {
                    val title = action.title?.ifBlank { null } ?: "MYVU"
                    val content = action.content ?: ""
                    if (content.isNotBlank()) {
                        try {
                            val json = Notifications.buildShow(title, content)
                            conn?.sendAction(json)
                        } catch (e: Exception) {
                            LogBus.error("TaskerActionReceiver: failed to build HUD notification", e)
                            return@ActionExecutor false
                        }
                    }
                }
                TaskerConstants.TYPE_SHOW_TELEPROMPTER -> {
                    val title = action.title?.ifBlank { null } ?: "Prompter"
                    val content = action.content ?: ""
                    if (content.isNotBlank()) {
                        conn?.openTeleprompter(content, title)
                    }
                }
                TaskerConstants.TYPE_SET_BRIGHTNESS -> {
                    val value = action.valueInt ?: GlassesConfig.DEFAULT_BRIGHTNESS
                    GlassesConfig.setBrightness(context, value)
                    conn?.setBrightness(value)
                }
                TaskerConstants.TYPE_SET_VOLUME -> {
                    val value = action.valueInt ?: GlassesConfig.DEFAULT_VOLUME
                    GlassesConfig.setVolume(context, value)
                    conn?.setVolume(value)
                }
                TaskerConstants.TYPE_TOGGLE_WIFI -> {
                    val enabled = action.valueBoolean ?: true
                    Prefs.setWifiEnabled(context, enabled)
                    conn?.toggleWifi(enabled)
                }
                TaskerConstants.TYPE_SET_ZEN_MODE -> {
                    val enabled = action.valueBoolean ?: true
                    Prefs.setZenModeEnabled(context, enabled)
                    conn?.setZenMode(enabled)
                }
                TaskerConstants.TYPE_SET_AIR_MODE -> {
                    val enabled = action.valueBoolean ?: true
                    Prefs.setAirModeEnabled(context, enabled)
                    conn?.setAirMode(enabled)
                }
                TaskerConstants.TYPE_SET_STANDBY_POS -> {
                    val pos = action.valueInt ?: GlassesConfig.DEFAULT_STANDBY_POS
                    GlassesConfig.setStandbyPosition(context, pos)
                    conn?.setStandbyPosition(pos)
                }
                TaskerConstants.TYPE_SEND_RAW -> {
                    val raw = action.rawJson
                    if (!raw.isNullOrBlank()) {
                        conn?.sendRaw(raw)
                    }
                }
                else -> {
                    LogBus.warn("TaskerActionReceiver: unknown action type '${action.type}'")
                    return@ActionExecutor false
                }
            }
            true
        }
    }
}
