package com.myvu.client.plugin.tasker.event

import android.content.Context
import android.content.Intent
import com.myvu.client.core.LogBus
import com.myvu.client.plugin.tasker.TaskerBundleManager
import com.myvu.client.plugin.tasker.TaskerConstants
import com.myvu.client.plugin.tasker.TaskerEvent
import com.myvu.client.service.ConnectionState

/**
 * Centralized dispatcher for glasses-generated events to Tasker and Android Broadcasts.
 */
object TaskerEventBroadcaster {

    /**
     * Testing hook to intercept broadcast intents without sending real Android broadcasts.
     */
    var customBroadcaster: ((Context, Intent) -> Unit)? = null

    /**
     * Dispatches a touch gesture or hardware trigger event.
     */
    fun sendGestureEvent(context: Context, gestureCode: Int, gestureName: String) {
        val event = TaskerEvent(
            eventType = TaskerConstants.EVENT_TOUCH_GESTURE,
            gestureCode = gestureCode,
            gestureName = gestureName
        )
        broadcastEvent(context, event)
    }

    /**
     * Dispatches an AI button or wake word trigger event.
     */
    fun sendAiButtonEvent(context: Context, buttonCode: Int) {
        val event = TaskerEvent(
            eventType = TaskerConstants.EVENT_AI_BUTTON,
            buttonCode = buttonCode
        )
        broadcastEvent(context, event)
    }

    /**
     * Dispatches a connection state change event based on ConnectionState.
     */
    fun sendConnectionStateEvent(context: Context, state: ConnectionState) {
        val isConnected = state == ConnectionState.READY
        val eventType = if (isConnected) TaskerConstants.EVENT_CONNECTED else TaskerConstants.EVENT_DISCONNECTED
        val event = TaskerEvent(
            eventType = eventType,
            connectionState = state.name
        )
        broadcastEvent(context, event)
    }

    /**
     * Dispatches a connection state change event based on a boolean.
     */
    fun sendConnectionStateEvent(context: Context, connected: Boolean) {
        val eventType = if (connected) TaskerConstants.EVENT_CONNECTED else TaskerConstants.EVENT_DISCONNECTED
        val event = TaskerEvent(
            eventType = eventType,
            connectionState = if (connected) "CONNECTED" else "DISCONNECTED"
        )
        broadcastEvent(context, event)
    }

    /**
     * Dispatches a glasses battery change event.
     */
    fun sendBatteryEvent(context: Context, batteryLevel: Int, isCharging: Boolean) {
        val event = TaskerEvent(
            eventType = TaskerConstants.EVENT_BATTERY_CHANGED,
            batteryLevel = batteryLevel,
            isCharging = isCharging
        )
        broadcastEvent(context, event)
    }

    /**
     * Constructs and emits standard Tasker and direct broadcast intents.
     */
    fun broadcastEvent(context: Context, event: TaskerEvent) {
        val bundle = TaskerBundleManager.buildEventBundle(event)
        val blurb = TaskerBundleManager.generateEventBlurb(event)

        // 1. Direct Intent Broadcast (com.myvu.client.TASKER_EVENT)
        val directIntent = Intent(TaskerConstants.BROADCAST_EVENT).apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, bundle)
            putExtra(TaskerConstants.EXTRA_BLURB, blurb)
            putExtra(TaskerConstants.KEY_EVENT_TYPE, event.eventType)

            event.gestureCode?.let { putExtra(TaskerConstants.KEY_GESTURE_CODE, it) }
            event.gestureName?.let { putExtra(TaskerConstants.KEY_GESTURE_NAME, it) }
            event.buttonCode?.let { putExtra(TaskerConstants.KEY_BUTTON_CODE, it) }
            event.batteryLevel?.let { putExtra(TaskerConstants.KEY_BATTERY_LEVEL, it) }
            event.isCharging?.let { putExtra(TaskerConstants.KEY_IS_CHARGING, it) }
            event.connectionState?.let { putExtra(TaskerConstants.KEY_STATE, it) }

            // Tasker Dynamic Variables
            putExtra(TaskerConstants.VAR_EVENT, event.eventType)
            event.gestureName?.let { putExtra(TaskerConstants.VAR_GESTURE, it) }
            event.batteryLevel?.let { putExtra(TaskerConstants.VAR_BATTERY, it.toString()) }
            event.connectionState?.let { putExtra(TaskerConstants.VAR_STATE, it) }
        }
        send(context, directIntent)

        // 2. Tasker Open Event Broadcast (net.dinglisch.android.tasker.ACTION_OPEN_EVENT)
        val taskerIntent = Intent(TaskerConstants.TASKER_ACTION_OPEN_EVENT).apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, bundle)
            putExtra(TaskerConstants.EXTRA_BLURB, blurb)
            putExtra(TaskerConstants.KEY_EVENT_TYPE, event.eventType)

            event.gestureCode?.let { putExtra(TaskerConstants.KEY_GESTURE_CODE, it) }
            event.gestureName?.let { putExtra(TaskerConstants.KEY_GESTURE_NAME, it) }
            event.buttonCode?.let { putExtra(TaskerConstants.KEY_BUTTON_CODE, it) }
            event.batteryLevel?.let { putExtra(TaskerConstants.KEY_BATTERY_LEVEL, it) }
            event.isCharging?.let { putExtra(TaskerConstants.KEY_IS_CHARGING, it) }
            event.connectionState?.let { putExtra(TaskerConstants.KEY_STATE, it) }

            // Tasker Dynamic Variables
            putExtra(TaskerConstants.VAR_EVENT, event.eventType)
            event.gestureName?.let { putExtra(TaskerConstants.VAR_GESTURE, it) }
            event.batteryLevel?.let { putExtra(TaskerConstants.VAR_BATTERY, it.toString()) }
            event.connectionState?.let { putExtra(TaskerConstants.VAR_STATE, it) }
        }
        send(context, taskerIntent)
    }

    private fun send(context: Context, intent: Intent) {
        LogBus.log("TaskerEventBroadcaster: broadcasting action=${intent.action} type=${intent.getStringExtra(TaskerConstants.KEY_EVENT_TYPE)}")
        val custom = customBroadcaster
        if (custom != null) {
            custom.invoke(context, intent)
        } else {
            context.sendBroadcast(intent)
        }
    }
}
