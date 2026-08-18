package com.myvu.client.plugin.tasker.event

import android.content.Context
import android.content.Intent
import com.myvu.client.plugin.tasker.TaskerBundleManager
import com.myvu.client.plugin.tasker.TaskerConstants
import com.myvu.client.plugin.tasker.TaskerEvent
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TaskerConditionReceiverTest {

    private lateinit var context: Context
    private lateinit var receiver: TaskerConditionReceiver

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        receiver = TaskerConditionReceiver()
    }

    @Test
    fun queryConditionSatisfiedForTouchGestureEvent() {
        val event = TaskerEvent(
            eventType = TaskerConstants.EVENT_TOUCH_GESTURE,
            gestureCode = 3,
            gestureName = "Pulsación Larga"
        )
        val bundle = TaskerBundleManager.buildEventBundle(event)
        val intent = Intent(TaskerConstants.ACTION_QUERY_CONDITION).apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, bundle)
        }

        receiver.onReceive(context, intent)
        assertEquals(TaskerConstants.RESULT_CONDITION_SATISFIED, receiver.lastResultCode)
    }

    @Test
    fun queryConditionSatisfiedForDisconnectedWhenNoActiveConnection() {
        val event = TaskerEvent(
            eventType = TaskerConstants.EVENT_DISCONNECTED,
            connectionState = "DISCONNECTED"
        )
        val bundle = TaskerBundleManager.buildEventBundle(event)
        val intent = Intent(TaskerConstants.ACTION_QUERY_CONDITION).apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, bundle)
        }

        receiver.onReceive(context, intent)
        assertEquals(TaskerConstants.RESULT_CONDITION_SATISFIED, receiver.lastResultCode)
    }

    @Test
    fun queryConditionUnknownForInvalidBundle() {
        val intent = Intent(TaskerConstants.ACTION_QUERY_CONDITION)
        receiver.onReceive(context, intent)
        assertEquals(TaskerConstants.RESULT_CONDITION_UNKNOWN, receiver.lastResultCode)
    }
}
