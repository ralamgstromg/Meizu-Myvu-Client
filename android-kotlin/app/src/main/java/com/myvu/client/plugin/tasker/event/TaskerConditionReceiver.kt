package com.myvu.client.plugin.tasker.event

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myvu.client.core.GlassesConfig
import com.myvu.client.core.LogBus
import com.myvu.client.plugin.tasker.TaskerBundleManager
import com.myvu.client.plugin.tasker.TaskerConstants
import com.myvu.client.service.ConnectionState
import com.myvu.client.service.MyvuService

/**
 * Receiver for evaluating Locale/Tasker conditions (QUERY_CONDITION).
 */
class TaskerConditionReceiver : BroadcastReceiver() {

    var lastResultCode: Int = TaskerConstants.RESULT_CONDITION_UNKNOWN
        private set

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val actionName = intent.action
        if (actionName != TaskerConstants.ACTION_QUERY_CONDITION) {
            LogBus.trace("TaskerConditionReceiver: ignoring action $actionName")
            return
        }

        val bundle = intent.getBundleExtra(TaskerConstants.EXTRA_BUNDLE) ?: intent.extras
        val event = TaskerBundleManager.extractEvent(bundle)

        if (event == null) {
            LogBus.warn("TaskerConditionReceiver: received query without valid event bundle")
            postResult(TaskerConstants.RESULT_CONDITION_UNKNOWN)
            return
        }

        val resultCode = evaluateCondition(context, event)
        LogBus.log("TaskerConditionReceiver: condition '${event.eventType}' evaluated to $resultCode")
        postResult(resultCode)
    }

    private fun postResult(code: Int) {
        lastResultCode = code
        try {
            setResultCode(code)
        } catch (ignored: IllegalStateException) {
            // Unordered broadcast or non-broadcast context
        }
    }

    private fun evaluateCondition(context: Context, event: com.myvu.client.plugin.tasker.TaskerEvent): Int {
        val conn = MyvuService.activeConnection()
        val isConnected = conn?.state == ConnectionState.READY

        return when (event.eventType) {
            TaskerConstants.EVENT_CONNECTED -> {
                if (isConnected) TaskerConstants.RESULT_CONDITION_SATISFIED else TaskerConstants.RESULT_CONDITION_UNSATISFIED
            }
            TaskerConstants.EVENT_DISCONNECTED -> {
                if (!isConnected) TaskerConstants.RESULT_CONDITION_SATISFIED else TaskerConstants.RESULT_CONDITION_UNSATISFIED
            }
            TaskerConstants.EVENT_BATTERY_CHANGED -> {
                val currentBattery = conn?.glassesInfo()?.battery ?: -1
                val targetLevel = event.batteryLevel
                if (targetLevel != null && currentBattery in 1..100 && currentBattery < targetLevel) {
                    TaskerConstants.RESULT_CONDITION_UNSATISFIED
                } else {
                    TaskerConstants.RESULT_CONDITION_SATISFIED
                }
            }
            TaskerConstants.EVENT_TOUCH_GESTURE,
            TaskerConstants.EVENT_AI_BUTTON -> {
                // Instantaneous event triggers are satisfied when fired via ACTION_OPEN_EVENT
                TaskerConstants.RESULT_CONDITION_SATISFIED
            }
            else -> TaskerConstants.RESULT_CONDITION_UNKNOWN
        }
    }
}
