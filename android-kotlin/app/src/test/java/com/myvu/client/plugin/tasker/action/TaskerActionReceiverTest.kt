package com.myvu.client.plugin.tasker.action

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.myvu.client.core.GlassesConfig
import com.myvu.client.core.Prefs
import com.myvu.client.plugin.tasker.TaskerAction
import com.myvu.client.plugin.tasker.TaskerBundleManager
import com.myvu.client.plugin.tasker.TaskerConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TaskerActionReceiverTest {

    private lateinit var context: Context
    private val executedActions = mutableListOf<TaskerAction>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        executedActions.clear()
        TaskerActionReceiver.customExecutor = TaskerActionReceiver.ActionExecutor { _, action, _ ->
            executedActions.add(action)
            true
        }
    }

    @After
    fun tearDown() {
        TaskerActionReceiver.customExecutor = null
        executedActions.clear()
    }

    @Test
    fun receivesFireSettingWithHudMessage() {
        val receiver = TaskerActionReceiver()
        val bundle = TaskerBundleManager.buildHudBundle(
            title = "Tasker Alert",
            content = "Server rebooted"
        )
        val intent = Intent(TaskerConstants.ACTION_FIRE_SETTING).apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, bundle)
        }

        receiver.onReceive(context, intent)

        assertEquals(1, executedActions.size)
        val action = executedActions[0]
        assertEquals(TaskerConstants.TYPE_SHOW_HUD, action.type)
        assertEquals("Tasker Alert", action.title)
        assertEquals("Server rebooted", action.content)
    }

    @Test
    fun receivesFireSettingWithTeleprompter() {
        val receiver = TaskerActionReceiver()
        val bundle = TaskerBundleManager.buildTeleprompterBundle(
            text = "Welcome to the presentation",
            title = "Intro"
        )
        val intent = Intent(TaskerConstants.ACTION_FIRE_SETTING).apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, bundle)
        }

        receiver.onReceive(context, intent)

        assertEquals(1, executedActions.size)
        val action = executedActions[0]
        assertEquals(TaskerConstants.TYPE_SHOW_TELEPROMPTER, action.type)
        assertEquals("Intro", action.title)
        assertEquals("Welcome to the presentation", action.content)
    }

    @Test
    fun receivesFireSettingWithBrightnessAndVolume() {
        val receiver = TaskerActionReceiver()

        val brightnessBundle = TaskerBundleManager.buildBrightnessBundle(4)
        val brightnessIntent = Intent(TaskerConstants.ACTION_FIRE_SETTING).apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, brightnessBundle)
        }
        receiver.onReceive(context, brightnessIntent)

        val volumeBundle = TaskerBundleManager.buildVolumeBundle(14)
        val volumeIntent = Intent(TaskerConstants.ACTION_FIRE_SETTING).apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, volumeBundle)
        }
        receiver.onReceive(context, volumeIntent)

        assertEquals(2, executedActions.size)
        assertEquals(TaskerConstants.TYPE_SET_BRIGHTNESS, executedActions[0].type)
        assertEquals(4, executedActions[0].valueInt)

        assertEquals(TaskerConstants.TYPE_SET_VOLUME, executedActions[1].type)
        assertEquals(14, executedActions[1].valueInt)
    }

    @Test
    fun receivesFireSettingWithSystemToggles() {
        val receiver = TaskerActionReceiver()

        val wifiIntent = Intent(TaskerConstants.ACTION_FIRE_SETTING).apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, TaskerBundleManager.buildWifiBundle(false))
        }
        receiver.onReceive(context, wifiIntent)

        val zenIntent = Intent(TaskerConstants.ACTION_FIRE_SETTING).apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, TaskerBundleManager.buildZenModeBundle(true))
        }
        receiver.onReceive(context, zenIntent)

        val airIntent = Intent(TaskerConstants.ACTION_FIRE_SETTING).apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, TaskerBundleManager.buildAirModeBundle(false))
        }
        receiver.onReceive(context, airIntent)

        val standbyIntent = Intent(TaskerConstants.ACTION_FIRE_SETTING).apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, TaskerBundleManager.buildStandbyPosBundle(3))
        }
        receiver.onReceive(context, standbyIntent)

        assertEquals(4, executedActions.size)
        assertEquals(TaskerConstants.TYPE_TOGGLE_WIFI, executedActions[0].type)
        assertEquals(false, executedActions[0].valueBoolean)

        assertEquals(TaskerConstants.TYPE_SET_ZEN_MODE, executedActions[1].type)
        assertEquals(true, executedActions[1].valueBoolean)

        assertEquals(TaskerConstants.TYPE_SET_AIR_MODE, executedActions[2].type)
        assertEquals(false, executedActions[2].valueBoolean)

        assertEquals(TaskerConstants.TYPE_SET_STANDBY_POS, executedActions[3].type)
        assertEquals(3, executedActions[3].valueInt)
    }

    @Test
    fun receivesFireSettingWithRawJson() {
        val receiver = TaskerActionReceiver()
        val rawJson = "{\"action\":\"system\",\"data\":{\"action\":\"get_device_info\"}}"
        val bundle = TaskerBundleManager.buildRawJsonBundle(rawJson)
        val intent = Intent(TaskerConstants.ACTION_FIRE_SETTING).apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, bundle)
        }

        receiver.onReceive(context, intent)

        assertEquals(1, executedActions.size)
        assertEquals(TaskerConstants.TYPE_SEND_RAW, executedActions[0].type)
        assertEquals(rawJson, executedActions[0].rawJson)
    }

    @Test
    fun receivesDirectBroadcastAction() {
        val receiver = TaskerActionReceiver()
        val bundle = TaskerBundleManager.buildHudBundle("Direct Title", "Direct Message")
        val intent = Intent(TaskerConstants.BROADCAST_ACTION).apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, bundle)
        }

        receiver.onReceive(context, intent)

        assertEquals(1, executedActions.size)
        assertEquals(TaskerConstants.TYPE_SHOW_HUD, executedActions[0].type)
        assertEquals("Direct Title", executedActions[0].title)
        assertEquals("Direct Message", executedActions[0].content)
    }

    @Test
    fun resolvesDynamicVariablesFromPassThroughBundle() {
        val receiver = TaskerActionReceiver()
        val bundle = TaskerBundleManager.buildHudBundle(
            title = "Aviso de %user",
            content = "Mensaje: %body_text"
        )
        val passThrough = Bundle().apply {
            putString("%user", "Roberto")
            putString("%body_text", "Batería baja al 15%")
        }

        val intent = Intent(TaskerConstants.ACTION_FIRE_SETTING).apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, bundle)
            putExtra(TaskerConstants.EXTRA_TASKER_PASS_THROUGH, passThrough)
        }

        receiver.onReceive(context, intent)

        assertEquals(1, executedActions.size)
        val action = executedActions[0]
        assertEquals("Aviso de Roberto", action.title)
        assertEquals("Mensaje: Batería baja al 15%", action.content)
    }

    @Test
    fun ignoresUnrelatedActionIntents() {
        val receiver = TaskerActionReceiver()
        val bundle = TaskerBundleManager.buildHudBundle("Title", "Message")
        val intent = Intent("android.intent.action.BATTERY_CHANGED").apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, bundle)
        }

        receiver.onReceive(context, intent)

        assertTrue(executedActions.isEmpty())
    }

    @Test
    fun ignoresEmptyOrInvalidActionBundle() {
        val receiver = TaskerActionReceiver()
        val intent = Intent(TaskerConstants.ACTION_FIRE_SETTING)

        receiver.onReceive(context, intent)

        assertTrue(executedActions.isEmpty())
    }

    @Test
    fun defaultExecutorUpdatesPrefsAndConfig() {
        TaskerActionReceiver.customExecutor = null

        val brightnessAction = TaskerAction(
            type = TaskerConstants.TYPE_SET_BRIGHTNESS,
            valueInt = 5
        )
        val volumeAction = TaskerAction(
            type = TaskerConstants.TYPE_SET_VOLUME,
            valueInt = 8
        )
        val wifiAction = TaskerAction(
            type = TaskerConstants.TYPE_TOGGLE_WIFI,
            valueBoolean = true
        )
        val zenAction = TaskerAction(
            type = TaskerConstants.TYPE_SET_ZEN_MODE,
            valueBoolean = false
        )
        val standbyAction = TaskerAction(
            type = TaskerConstants.TYPE_SET_STANDBY_POS,
            valueInt = 2
        )

        assertTrue(TaskerActionReceiver.defaultExecutor.execute(context, brightnessAction, null))
        assertEquals(5, GlassesConfig.getBrightness(context))

        assertTrue(TaskerActionReceiver.defaultExecutor.execute(context, volumeAction, null))
        assertEquals(8, GlassesConfig.getVolume(context))

        assertTrue(TaskerActionReceiver.defaultExecutor.execute(context, wifiAction, null))
        assertTrue(Prefs.wifiEnabled(context))

        assertTrue(TaskerActionReceiver.defaultExecutor.execute(context, zenAction, null))
        assertFalse(Prefs.zenModeEnabled(context))

        assertTrue(TaskerActionReceiver.defaultExecutor.execute(context, standbyAction, null))
        assertEquals(2, GlassesConfig.getStandbyPosition(context))
    }
}
