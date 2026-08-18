package com.myvu.client.plugin.tasker.event

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.myvu.client.app.feature.TouchGestureManager
import com.myvu.client.plugin.tasker.TaskerBundleManager
import com.myvu.client.plugin.tasker.TaskerConstants
import com.myvu.client.service.ConnectionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TaskerEventBroadcasterTest {

    private lateinit var context: Context
    private val broadcastedIntents = mutableListOf<Intent>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        broadcastedIntents.clear()
        TaskerEventBroadcaster.customBroadcaster = { _, intent ->
            broadcastedIntents.add(intent)
        }
    }

    @After
    fun tearDown() {
        TaskerEventBroadcaster.customBroadcaster = null
        broadcastedIntents.clear()
    }

    @Test
    fun sendGestureEventBroadcastsDirectAndTaskerIntents() {
        TaskerEventBroadcaster.sendGestureEvent(context, 2, "Toque Doble")

        assertEquals(2, broadcastedIntents.size)

        val directIntent = broadcastedIntents[0]
        assertEquals(TaskerConstants.BROADCAST_EVENT, directIntent.action)
        assertEquals(TaskerConstants.EVENT_TOUCH_GESTURE, directIntent.getStringExtra(TaskerConstants.KEY_EVENT_TYPE))
        assertEquals(2, directIntent.getIntExtra(TaskerConstants.KEY_GESTURE_CODE, -1))
        assertEquals("Toque Doble", directIntent.getStringExtra(TaskerConstants.KEY_GESTURE_NAME))
        assertEquals(TaskerConstants.EVENT_TOUCH_GESTURE, directIntent.getStringExtra(TaskerConstants.VAR_EVENT))
        assertEquals("Toque Doble", directIntent.getStringExtra(TaskerConstants.VAR_GESTURE))

        val bundle = directIntent.getBundleExtra(TaskerConstants.EXTRA_BUNDLE)
        assertNotNull(bundle)
        val event = TaskerBundleManager.parseEvent(bundle)
        assertEquals(TaskerConstants.EVENT_TOUCH_GESTURE, event.eventType)
        assertEquals(2, event.gestureCode)
        assertEquals("Toque Doble", event.gestureName)

        val taskerIntent = broadcastedIntents[1]
        assertEquals(TaskerConstants.TASKER_ACTION_OPEN_EVENT, taskerIntent.action)
        assertEquals(TaskerConstants.EVENT_TOUCH_GESTURE, taskerIntent.getStringExtra(TaskerConstants.KEY_EVENT_TYPE))
        assertEquals("Toque Doble", taskerIntent.getStringExtra(TaskerConstants.VAR_GESTURE))
    }

    @Test
    fun sendAiButtonEventBroadcastsCorrectPayload() {
        TaskerEventBroadcaster.sendAiButtonEvent(context, 3)

        assertEquals(2, broadcastedIntents.size)

        val directIntent = broadcastedIntents[0]
        assertEquals(TaskerConstants.BROADCAST_EVENT, directIntent.action)
        assertEquals(TaskerConstants.EVENT_AI_BUTTON, directIntent.getStringExtra(TaskerConstants.KEY_EVENT_TYPE))
        assertEquals(3, directIntent.getIntExtra(TaskerConstants.KEY_BUTTON_CODE, -1))
        assertEquals(TaskerConstants.EVENT_AI_BUTTON, directIntent.getStringExtra(TaskerConstants.VAR_EVENT))

        val bundle = directIntent.getBundleExtra(TaskerConstants.EXTRA_BUNDLE)
        assertNotNull(bundle)
        val event = TaskerBundleManager.parseEvent(bundle)
        assertEquals(TaskerConstants.EVENT_AI_BUTTON, event.eventType)
        assertEquals(3, event.buttonCode)

        val taskerIntent = broadcastedIntents[1]
        assertEquals(TaskerConstants.TASKER_ACTION_OPEN_EVENT, taskerIntent.action)
        assertEquals(TaskerConstants.EVENT_AI_BUTTON, taskerIntent.getStringExtra(TaskerConstants.KEY_EVENT_TYPE))
    }

    @Test
    fun sendConnectionStateEventWithReadyState() {
        TaskerEventBroadcaster.sendConnectionStateEvent(context, ConnectionState.READY)

        assertEquals(2, broadcastedIntents.size)

        val directIntent = broadcastedIntents[0]
        assertEquals(TaskerConstants.BROADCAST_EVENT, directIntent.action)
        assertEquals(TaskerConstants.EVENT_CONNECTED, directIntent.getStringExtra(TaskerConstants.KEY_EVENT_TYPE))
        assertEquals("READY", directIntent.getStringExtra(TaskerConstants.KEY_STATE))
        assertEquals("READY", directIntent.getStringExtra(TaskerConstants.VAR_STATE))

        val taskerIntent = broadcastedIntents[1]
        assertEquals(TaskerConstants.TASKER_ACTION_OPEN_EVENT, taskerIntent.action)
        assertEquals(TaskerConstants.EVENT_CONNECTED, taskerIntent.getStringExtra(TaskerConstants.KEY_EVENT_TYPE))
    }

    @Test
    fun sendConnectionStateEventWithFailedOrDisconnectedState() {
        TaskerEventBroadcaster.sendConnectionStateEvent(context, ConnectionState.FAILED)

        assertEquals(2, broadcastedIntents.size)

        val directIntent = broadcastedIntents[0]
        assertEquals(TaskerConstants.BROADCAST_EVENT, directIntent.action)
        assertEquals(TaskerConstants.EVENT_DISCONNECTED, directIntent.getStringExtra(TaskerConstants.KEY_EVENT_TYPE))
        assertEquals("FAILED", directIntent.getStringExtra(TaskerConstants.KEY_STATE))
        assertEquals("FAILED", directIntent.getStringExtra(TaskerConstants.VAR_STATE))

        val taskerIntent = broadcastedIntents[1]
        assertEquals(TaskerConstants.TASKER_ACTION_OPEN_EVENT, taskerIntent.action)
        assertEquals(TaskerConstants.EVENT_DISCONNECTED, taskerIntent.getStringExtra(TaskerConstants.KEY_EVENT_TYPE))
    }

    @Test
    fun sendConnectionStateEventBooleanOverload() {
        TaskerEventBroadcaster.sendConnectionStateEvent(context, true)
        assertEquals(2, broadcastedIntents.size)
        assertEquals(TaskerConstants.EVENT_CONNECTED, broadcastedIntents[0].getStringExtra(TaskerConstants.KEY_EVENT_TYPE))
        assertEquals("CONNECTED", broadcastedIntents[0].getStringExtra(TaskerConstants.KEY_STATE))

        broadcastedIntents.clear()
        TaskerEventBroadcaster.sendConnectionStateEvent(context, false)
        assertEquals(2, broadcastedIntents.size)
        assertEquals(TaskerConstants.EVENT_DISCONNECTED, broadcastedIntents[0].getStringExtra(TaskerConstants.KEY_EVENT_TYPE))
        assertEquals("DISCONNECTED", broadcastedIntents[0].getStringExtra(TaskerConstants.KEY_STATE))
    }

    @Test
    fun sendBatteryEventBroadcastsLevelAndChargingStatus() {
        TaskerEventBroadcaster.sendBatteryEvent(context, 85, true)

        assertEquals(2, broadcastedIntents.size)

        val directIntent = broadcastedIntents[0]
        assertEquals(TaskerConstants.BROADCAST_EVENT, directIntent.action)
        assertEquals(TaskerConstants.EVENT_BATTERY_CHANGED, directIntent.getStringExtra(TaskerConstants.KEY_EVENT_TYPE))
        assertEquals(85, directIntent.getIntExtra(TaskerConstants.KEY_BATTERY_LEVEL, -1))
        assertTrue(directIntent.getBooleanExtra(TaskerConstants.KEY_IS_CHARGING, false))
        assertEquals("85", directIntent.getStringExtra(TaskerConstants.VAR_BATTERY))

        val bundle = directIntent.getBundleExtra(TaskerConstants.EXTRA_BUNDLE)
        assertNotNull(bundle)
        val event = TaskerBundleManager.parseEvent(bundle)
        assertEquals(TaskerConstants.EVENT_BATTERY_CHANGED, event.eventType)
        assertEquals(85, event.batteryLevel)
        assertEquals(true, event.isCharging)

        val taskerIntent = broadcastedIntents[1]
        assertEquals(TaskerConstants.TASKER_ACTION_OPEN_EVENT, taskerIntent.action)
        assertEquals(TaskerConstants.EVENT_BATTERY_CHANGED, taskerIntent.getStringExtra(TaskerConstants.KEY_EVENT_TYPE))
        assertEquals("85", taskerIntent.getStringExtra(TaskerConstants.VAR_BATTERY))
    }

    @Test
    fun touchGestureManagerDispatchesEventsToBroadcaster() {
        var aiExecuted = false
        val executor = TouchGestureManager.ActionExecutor {
            aiExecuted = true
        }

        TouchGestureManager.handleTrigger(context, 3, executor)

        // Should have emitted gesture event (2 intents) + AI button event (2 intents) = 4 intents
        assertEquals(4, broadcastedIntents.size)

        val gestureDirect = broadcastedIntents[0]
        assertEquals(TaskerConstants.BROADCAST_EVENT, gestureDirect.action)
        assertEquals(TaskerConstants.EVENT_TOUCH_GESTURE, gestureDirect.getStringExtra(TaskerConstants.KEY_EVENT_TYPE))
        assertEquals(3, gestureDirect.getIntExtra(TaskerConstants.KEY_GESTURE_CODE, -1))

        val aiDirect = broadcastedIntents[2]
        assertEquals(TaskerConstants.BROADCAST_EVENT, aiDirect.action)
        assertEquals(TaskerConstants.EVENT_AI_BUTTON, aiDirect.getStringExtra(TaskerConstants.KEY_EVENT_TYPE))
        assertEquals(3, aiDirect.getIntExtra(TaskerConstants.KEY_BUTTON_CODE, -1))

        assertTrue(aiExecuted)
    }
}
