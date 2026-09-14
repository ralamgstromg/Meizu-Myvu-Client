package com.myvu.client.ui

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.Spinner
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.myvu.client.R
import com.myvu.client.ai.AiResponseMode
import com.myvu.client.core.GlassesConfig
import com.myvu.client.core.Prefs
import com.myvu.client.database.AppDatabase
import com.myvu.client.data.BluetoothDeviceEntity
import com.myvu.client.data.BluetoothDeviceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.LooperMode

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsGestureConfigTest {

    private val testMac = "11:22:33:44:55:66"

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().apply()
        runBlocking {
            val dao = AppDatabase.getInstance(context).bluetoothDeviceDao()
            dao.insertOrUpdate(
                BluetoothDeviceEntity(
                    macAddress = testMac,
                    name = "MYVU Test Glasses",
                    deviceType = BluetoothDeviceType.SMART_GLASSES.name,
                    tap1Action = "NONE",
                    tap2Action = "LAUNCH_GEMINI",
                    tap3Action = "LAUNCH_PHONE_ASSISTANT",
                    swipeForwardAction = "MEDIA_NEXT",
                    swipeBackwardAction = "MEDIA_PREV",
                    longPressAction = "VOICE_AGENT_AURA"
                )
            )
        }
    }

    @After
    fun tearDown() {
        val context = RuntimeEnvironment.getApplication()
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().apply()
    }

    @Test
    fun verifyGlassesSettingsUiComponentsAndLiveValues() {
        val intent = Intent(RuntimeEnvironment.getApplication(), GlassesSettingsActivity::class.java).apply {
            putExtra(GlassesSettingsActivity.EXTRA_MAC, testMac)
        }

        val controller = Robolectric.buildActivity(GlassesSettingsActivity::class.java, intent).setup()
        val activity = controller.get()

        // Verify Spinners
        val spinnerTap = activity.findViewById<Spinner>(R.id.spinnerTap)
        val spinnerDoubleTap = activity.findViewById<Spinner>(R.id.spinnerDoubleTap)
        val spinnerTripleTap = activity.findViewById<Spinner>(R.id.spinnerTripleTap)
        val spinnerSwipeForward = activity.findViewById<Spinner>(R.id.spinnerSwipeForward)
        val spinnerSwipeBackward = activity.findViewById<Spinner>(R.id.spinnerSwipeBackward)
        val spinnerLongPress = activity.findViewById<Spinner>(R.id.spinnerLongPress)

        assertNotNull("spinnerTap should be present", spinnerTap)
        assertNotNull("spinnerDoubleTap should be present", spinnerDoubleTap)
        assertNotNull("spinnerTripleTap should be present", spinnerTripleTap)
        assertNotNull("spinnerSwipeForward should be present", spinnerSwipeForward)
        assertNotNull("spinnerSwipeBackward should be present", spinnerSwipeBackward)
        assertNotNull("spinnerLongPress should be present", spinnerLongPress)

        // Verify Sliders
        val sliderHudBrightness = activity.findViewById<Slider>(R.id.sliderHudBrightness)
        val sliderGlassesVolume = activity.findViewById<Slider>(R.id.sliderGlassesVolume)
        val sliderStandbyPos = activity.findViewById<Slider>(R.id.sliderStandbyPos)
        val sliderScreenOff = activity.findViewById<Slider>(R.id.sliderScreenOff)
        val sliderNotifDuration = activity.findViewById<Slider>(R.id.sliderNotifDuration)

        assertNotNull("sliderHudBrightness should be present", sliderHudBrightness)
        assertNotNull("sliderGlassesVolume should be present", sliderGlassesVolume)
        assertNotNull("sliderStandbyPos should be present", sliderStandbyPos)
        assertNotNull("sliderScreenOff should be present", sliderScreenOff)
        assertNotNull("sliderNotifDuration should be present", sliderNotifDuration)

        assertEquals(1f, sliderHudBrightness.valueFrom)
        assertEquals(5f, sliderHudBrightness.valueTo)

        // Verify AI Response Mode & Switches
        val btnAiResponseModeGroup = activity.findViewById<MaterialButtonToggleGroup>(R.id.btnAiResponseModeGroup)
        val swContinuousDialogue = activity.findViewById<MaterialSwitch>(R.id.swContinuousDialogue)
        val swVoiceWakeup = activity.findViewById<MaterialSwitch>(R.id.swVoiceWakeup)
        val swForceGeminiSco = activity.findViewById<MaterialSwitch>(R.id.swForceGeminiSco)

        assertNotNull("btnAiResponseModeGroup should be present", btnAiResponseModeGroup)
        assertNotNull("swContinuousDialogue should be present", swContinuousDialogue)
        assertNotNull("swVoiceWakeup should be present", swVoiceWakeup)
        assertNotNull("swForceGeminiSco should be present", swForceGeminiSco)

        // Test saving settings: change brightness to 4 and volume to 10
        sliderHudBrightness.value = 4f
        sliderGlassesVolume.value = 10f
        sliderStandbyPos.value = 2f
        sliderScreenOff.value = 15f
        sliderNotifDuration.value = 7f
        btnAiResponseModeGroup.check(R.id.btnAiResponseVoice)
        swContinuousDialogue.isChecked = true
        swVoiceWakeup.isChecked = false
        swForceGeminiSco.isChecked = true

        val btnSave = activity.findViewById<Button>(R.id.btnSave)
        assertNotNull(btnSave)
        btnSave.performClick()

        // Verify persistence in GlassesConfig & Prefs
        assertEquals(4, GlassesConfig.getBrightness(activity))
        assertEquals(10, GlassesConfig.getVolume(activity))
        assertEquals(2, GlassesConfig.getStandbyPosition(activity))
        assertEquals(15, GlassesConfig.getScreenOffTime(activity))
        assertEquals(7, GlassesConfig.getNotificationDuration(activity))

        assertEquals(AiResponseMode.VOICE_ONLY.id, Prefs.aiResponseMode(activity))
        assertTrue(Prefs.continuousDialogueEnabled(activity))
        assertEquals(false, Prefs.voiceWakeupEnabled(activity))
        assertTrue(Prefs.isGeminiForceScoEnabled(activity))

        // Verify persistence directly in Room Database
        runBlocking {
            val dao = AppDatabase.getInstance(activity).bluetoothDeviceDao()
            val savedEntity = dao.getDevice(testMac)
            assertNotNull("Device should be saved in Room", savedEntity)
            assertEquals(80, savedEntity?.hudBrightness)
        }
    }

    @Test
    fun testSavingWithoutIntentMacFallsBackAndPersists() {
        val context = RuntimeEnvironment.getApplication()
        Prefs.setTargetMac(context, testMac)

        // Launch without extra
        val controller = Robolectric.buildActivity(GlassesSettingsActivity::class.java).setup()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val activity = controller.get()

        val sliderHudBrightness = activity.findViewById<Slider>(R.id.sliderHudBrightness)
        sliderHudBrightness.value = 5f

        val btnSave = activity.findViewById<Button>(R.id.btnSave)
        btnSave.performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals(5, GlassesConfig.getBrightness(activity))
        runBlocking {
            val dao = AppDatabase.getInstance(activity).bluetoothDeviceDao()
            var saved: BluetoothDeviceEntity? = null
            for (i in 0 until 50) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                saved = dao.getDevice(testMac)
                if (saved?.hudBrightness == 100) break
                kotlinx.coroutines.delay(20)
            }
            assertNotNull(saved)
            assertEquals(100, saved?.hudBrightness)
        }
    }

    @Test
    fun testFullReopenReflectsSavedParameters() {
        val intent = Intent(RuntimeEnvironment.getApplication(), GlassesSettingsActivity::class.java).apply {
            putExtra(GlassesSettingsActivity.EXTRA_MAC, testMac)
        }

        // 1. First session: modify and save
        val controller1 = Robolectric.buildActivity(GlassesSettingsActivity::class.java, intent).setup()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val act1 = controller1.get()

        act1.findViewById<Slider>(R.id.sliderHudBrightness).value = 2f
        act1.findViewById<Slider>(R.id.sliderGlassesVolume).value = 8f
        act1.findViewById<Slider>(R.id.sliderStandbyPos).value = 1f
        act1.findViewById<Slider>(R.id.sliderScreenOff).value = 20f
        act1.findViewById<Slider>(R.id.sliderNotifDuration).value = 12f

        act1.findViewById<MaterialButtonToggleGroup>(R.id.btnAiResponseModeGroup).check(R.id.btnAiResponseVisual)
        act1.findViewById<MaterialSwitch>(R.id.swContinuousDialogue).isChecked = true
        act1.findViewById<MaterialSwitch>(R.id.swVoiceWakeup).isChecked = true
        act1.findViewById<MaterialSwitch>(R.id.swForceGeminiSco).isChecked = false

        val spTap = act1.findViewById<Spinner>(R.id.spinnerTap)
        spTap.setSelection(com.myvu.client.data.CommonDeviceActions.getIndexForAction("HUD_DASHBOARD"))

        val spDouble = act1.findViewById<Spinner>(R.id.spinnerDoubleTap)
        spDouble.setSelection(com.myvu.client.data.CommonDeviceActions.getIndexForAction("MEDIA_PLAY_PAUSE"))

        val spNotif = act1.findViewById<Spinner>(R.id.spinnerNotificationMode)
        spNotif.setSelection(com.myvu.client.data.DeviceNotificationMode.getIndex(com.myvu.client.data.DeviceNotificationMode.VISUAL_ONLY.name))

        act1.findViewById<Button>(R.id.btnSave).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // 2. Second session: re-open activity from scratch and verify everything is restored
        val controller2 = Robolectric.buildActivity(GlassesSettingsActivity::class.java, intent).setup()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val act2 = controller2.get()

        assertEquals(2f, act2.findViewById<Slider>(R.id.sliderHudBrightness).value)
        assertEquals(8f, act2.findViewById<Slider>(R.id.sliderGlassesVolume).value)
        assertEquals(1f, act2.findViewById<Slider>(R.id.sliderStandbyPos).value)
        assertEquals(20f, act2.findViewById<Slider>(R.id.sliderScreenOff).value)
        assertEquals(12f, act2.findViewById<Slider>(R.id.sliderNotifDuration).value)

        assertEquals(R.id.btnAiResponseVisual, act2.findViewById<MaterialButtonToggleGroup>(R.id.btnAiResponseModeGroup).checkedButtonId)
        assertTrue(act2.findViewById<MaterialSwitch>(R.id.swContinuousDialogue).isChecked)
        assertTrue(act2.findViewById<MaterialSwitch>(R.id.swVoiceWakeup).isChecked)
        assertEquals(false, act2.findViewById<MaterialSwitch>(R.id.swForceGeminiSco).isChecked)

        val readTapAction = com.myvu.client.data.CommonDeviceActions.getActionId(act2.findViewById<Spinner>(R.id.spinnerTap).selectedItemPosition)
        assertEquals("HUD_DASHBOARD", readTapAction)

        val readDoubleAction = com.myvu.client.data.CommonDeviceActions.getActionId(act2.findViewById<Spinner>(R.id.spinnerDoubleTap).selectedItemPosition)
        assertEquals("MEDIA_PLAY_PAUSE", readDoubleAction)

        val readNotifMode = com.myvu.client.data.DeviceNotificationMode.getModeByIndex(act2.findViewById<Spinner>(R.id.spinnerNotificationMode).selectedItemPosition).name
        assertEquals(com.myvu.client.data.DeviceNotificationMode.VISUAL_ONLY.name, readNotifMode)
    }

    @Test
    fun testHeadphoneSettingsReopenReflectsSavedParameters() {
        val headphoneMac = "AA:BB:CC:DD:EE:FF"
        val intent = Intent().apply {
            putExtra(HeadphoneSettingsActivity.EXTRA_MAC, headphoneMac)
        }

        val controller1 = Robolectric.buildActivity(HeadphoneSettingsActivity::class.java, intent).setup()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val act1 = controller1.get()

        val sp1 = act1.findViewById<Spinner>(R.id.spinnerTap1)
        sp1.setSelection(com.myvu.client.data.CommonDeviceActions.getIndexForAction("LAUNCH_GEMINI"))

        val sp2 = act1.findViewById<Spinner>(R.id.spinnerTap2)
        sp2.setSelection(com.myvu.client.data.CommonDeviceActions.getIndexForAction("LAUNCH_PHONE_ASSISTANT"))

        val sp3 = act1.findViewById<Spinner>(R.id.spinnerTap3)
        sp3.setSelection(com.myvu.client.data.CommonDeviceActions.getIndexForAction("MEDIA_PREV"))

        val spLong = act1.findViewById<Spinner>(R.id.spinnerLongPress)
        spLong.setSelection(com.myvu.client.data.CommonDeviceActions.getIndexForAction("DAILY_BRIEFING"))

        act1.findViewById<MaterialSwitch>(R.id.switchTts).isChecked = true

        val spNotif = act1.findViewById<Spinner>(R.id.spinnerNotificationMode)
        spNotif.setSelection(com.myvu.client.data.DeviceNotificationMode.getIndex(com.myvu.client.data.DeviceNotificationMode.AUDIO_ONLY.name))

        act1.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Reopen activity and verify all fields preserved
        val controller2 = Robolectric.buildActivity(HeadphoneSettingsActivity::class.java, intent).setup()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val act2 = controller2.get()

        val readTap1 = com.myvu.client.data.CommonDeviceActions.getActionId(act2.findViewById<Spinner>(R.id.spinnerTap1).selectedItemPosition)
        assertEquals("LAUNCH_GEMINI", readTap1)

        val readTap2 = com.myvu.client.data.CommonDeviceActions.getActionId(act2.findViewById<Spinner>(R.id.spinnerTap2).selectedItemPosition)
        assertEquals("LAUNCH_PHONE_ASSISTANT", readTap2)

        val readTap3 = com.myvu.client.data.CommonDeviceActions.getActionId(act2.findViewById<Spinner>(R.id.spinnerTap3).selectedItemPosition)
        assertEquals("MEDIA_PREV", readTap3)

        val readLong = com.myvu.client.data.CommonDeviceActions.getActionId(act2.findViewById<Spinner>(R.id.spinnerLongPress).selectedItemPosition)
        assertEquals("DAILY_BRIEFING", readLong)

        assertTrue(act2.findViewById<MaterialSwitch>(R.id.switchTts).isChecked)

        val readNotif = com.myvu.client.data.DeviceNotificationMode.getModeByIndex(act2.findViewById<Spinner>(R.id.spinnerNotificationMode).selectedItemPosition).name
        assertEquals(com.myvu.client.data.DeviceNotificationMode.AUDIO_ONLY.name, readNotif)
    }

    @Test
    fun testHeadphoneSettingsDoesNotOverwriteGlasses() {
        val context = RuntimeEnvironment.getApplication()
        val emptyIntent = Intent()

        // Open HeadphoneSettingsActivity without MAC when glasses exist in database
        val controller = Robolectric.buildActivity(HeadphoneSettingsActivity::class.java, emptyIntent).setup()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val act = controller.get()

        act.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        runBlocking {
            val dao = AppDatabase.getInstance(context).bluetoothDeviceDao()
            val glasses = dao.getDevice(testMac)
            assertNotNull(glasses)
            assertEquals(BluetoothDeviceType.SMART_GLASSES.name, glasses?.deviceType)
        }
    }

    @Test
    fun testBluetoothDeviceManagerUpdateDeviceGesturesCreatesNewIfAbsent() {
        val context = RuntimeEnvironment.getApplication()
        val newMac = "99:88:77:66:55:44"

        runBlocking {
            com.myvu.client.service.BluetoothDeviceManager.getInstance(context).updateDeviceGestures(
                mac = newMac,
                tap1 = "MEDIA_PLAY_PAUSE",
                tap2 = "LAUNCH_GEMINI",
                tap3 = "NONE",
                longPress = "CREATE_AI_NOTE",
                tts = true,
                readNotifs = true,
                notificationMode = com.myvu.client.data.DeviceNotificationMode.AUDIO_ONLY.name
            )

            val dao = AppDatabase.getInstance(context).bluetoothDeviceDao()
            val created = dao.getDevice(newMac)
            assertNotNull(created)
            assertEquals("MEDIA_PLAY_PAUSE", created?.tap1Action)
            assertEquals("LAUNCH_GEMINI", created?.tap2Action)
            assertEquals(com.myvu.client.data.DeviceNotificationMode.AUDIO_ONLY.name, created?.notificationMode)
        }
    }

    @Test
    fun testActiveListeningDisabledByDefaultOnAllDevices() {
        val context = RuntimeEnvironment.getApplication()
        val defaultEntity = BluetoothDeviceEntity(macAddress = "DEFAULT-01", name = "Test Device")
        assertFalse("Active listening must be disabled by default on entity", defaultEntity.activeListeningEnabled)

        runBlocking {
            val dao = AppDatabase.getInstance(context).bluetoothDeviceDao()
            dao.insertOrUpdate(defaultEntity.copy(isConnected = true))
            val isEnabled = com.myvu.client.service.BluetoothDeviceManager.getInstance(context).isActiveListeningEnabled()
            assertFalse("Active listening must be disabled by default for connected device", isEnabled)
        }
    }

    @Test
    fun testHeadphoneSettingsActiveListeningToggleIndependentFromGlasses() {
        val context = RuntimeEnvironment.getApplication()
        val glassesMac = "AA:BB:CC:11:22:33"
        val headphoneMac = "DD:EE:FF:44:55:66"

        runBlocking {
            val dao = AppDatabase.getInstance(context).bluetoothDeviceDao()
            dao.insertOrUpdate(
                BluetoothDeviceEntity(
                    macAddress = glassesMac,
                    name = "MYVU Glasses",
                    deviceType = BluetoothDeviceType.SMART_GLASSES.name,
                    activeListeningEnabled = false
                )
            )
            dao.insertOrUpdate(
                BluetoothDeviceEntity(
                    macAddress = headphoneMac,
                    name = "Sony WH-1000XM5",
                    deviceType = BluetoothDeviceType.HEADPHONES.name,
                    activeListeningEnabled = false
                )
            )
        }

        val intent = Intent(context, HeadphoneSettingsActivity::class.java).apply {
            putExtra(HeadphoneSettingsActivity.EXTRA_MAC, headphoneMac)
        }
        val controller = Robolectric.buildActivity(HeadphoneSettingsActivity::class.java, intent).setup()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val activity = controller.get()

        val switchActiveListening = activity.findViewById<MaterialSwitch>(R.id.switchActiveListening)
        assertNotNull(switchActiveListening)
        assertFalse("Headphone active listening switch should initially be false", switchActiveListening.isChecked)

        // Turn ON active listening only for headphone
        switchActiveListening.isChecked = true
        activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        runBlocking {
            val dao = AppDatabase.getInstance(context).bluetoothDeviceDao()
            val headphone = dao.getDevice(headphoneMac)
            val glasses = dao.getDevice(glassesMac)

            assertNotNull(headphone)
            assertTrue("Headphone active listening should now be true", headphone!!.activeListeningEnabled)

            assertNotNull(glasses)
            assertFalse("Glasses active listening must remain false", glasses!!.activeListeningEnabled)
        }
    }

    @Test
    fun testGlassesSettingsActiveListeningTogglePersistsPerDevice() {
        val context = RuntimeEnvironment.getApplication()
        val glassesMac = "11:22:33:44:55:66"

        runBlocking {
            val dao = AppDatabase.getInstance(context).bluetoothDeviceDao()
            dao.insertOrUpdate(
                BluetoothDeviceEntity(
                    macAddress = glassesMac,
                    name = "MYVU Glasses",
                    deviceType = BluetoothDeviceType.SMART_GLASSES.name,
                    activeListeningEnabled = false
                )
            )
        }

        val intent = Intent(context, GlassesSettingsActivity::class.java).apply {
            putExtra(GlassesSettingsActivity.EXTRA_MAC, glassesMac)
        }
        val controller = Robolectric.buildActivity(GlassesSettingsActivity::class.java, intent).setup()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val activity = controller.get()

        val swContinuousDialogue = activity.findViewById<MaterialSwitch>(R.id.swContinuousDialogue)
        assertNotNull(swContinuousDialogue)
        assertFalse("Glasses active listening switch should initially be false", swContinuousDialogue.isChecked)

        // Enable and save
        swContinuousDialogue.isChecked = true
        activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        runBlocking {
            val dao = AppDatabase.getInstance(context).bluetoothDeviceDao()
            val glasses = dao.getDevice(glassesMac)
            assertNotNull(glasses)
            assertTrue("Glasses active listening should be true after save", glasses!!.activeListeningEnabled)
        }
    }

    @Test
    fun testHeadphoneSettingsCanSelectBothNotificationsAndTriggerButtons() {
        val headphoneMac = "55:44:33:22:11:00"
        val intent = Intent().apply {
            putExtra(HeadphoneSettingsActivity.EXTRA_MAC, headphoneMac)
        }

        val controller = Robolectric.buildActivity(HeadphoneSettingsActivity::class.java, intent).setup()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val act = controller.get()

        val spNotif = act.findViewById<Spinner>(R.id.spinnerNotificationMode)
        val bothIdx = com.myvu.client.data.DeviceNotificationMode.getIndex(com.myvu.client.data.DeviceNotificationMode.BOTH.name)
        spNotif.setSelection(bothIdx)

        val btnTestVoice = act.findViewById<View>(R.id.btnTestVoice)
        assertNotNull(btnTestVoice)
        btnTestVoice.performClick()

        val btnTestNotif = act.findViewById<View>(R.id.btnTestNotification)
        assertNotNull(btnTestNotif)
        btnTestNotif.performClick()

        act.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            val dao = AppDatabase.getInstance(context).bluetoothDeviceDao()
            val dev = dao.getDevice(headphoneMac)
            assertNotNull(dev)
            assertEquals(com.myvu.client.data.DeviceNotificationMode.BOTH.name, dev?.notificationMode)
            assertTrue(dev?.isVisualNotificationEnabled() == true)
            assertTrue(dev?.isAudioNotificationEnabled() == true)
        }
    }
}
