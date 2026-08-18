package com.myvu.client.plugin.tasker.event

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.myvu.client.R
import com.myvu.client.plugin.tasker.TaskerBundleManager
import com.myvu.client.plugin.tasker.TaskerConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class TaskerEventActivityTest {

    @Test
    fun activityLoadsWithDefaultGestureStateAndSaves() {
        val controller = Robolectric.buildActivity(TaskerEventActivity::class.java).setup()
        val activity = controller.get()

        val btnEventSave = activity.findViewById<MaterialButton>(R.id.btnEventSave)
        val txtBlurb = activity.findViewById<TextView>(R.id.txtBlurbPreview)

        assertEquals("Gesto Táctil: Cualquier Gesto", txtBlurb.text.toString())

        btnEventSave.performClick()

        assertTrue(activity.isFinishing)

        val shadow = Shadows.shadowOf(activity)
        assertEquals(Activity.RESULT_OK, shadow.resultCode)

        val resultIntent = shadow.resultIntent
        assertNotNull(resultIntent)

        val bundle = resultIntent.getBundleExtra(TaskerConstants.EXTRA_BUNDLE)
        assertNotNull(bundle)

        val event = TaskerBundleManager.parseEvent(bundle)
        assertEquals(TaskerConstants.EVENT_TOUCH_GESTURE, event.eventType)

        val blurb = resultIntent.getStringExtra(TaskerConstants.EXTRA_BLURB)
        assertEquals("Gesto Táctil: Cualquier Gesto", blurb)
    }

    @Test
    fun activityLoadsExistingBatteryBundle() {
        val initialBundle = TaskerBundleManager.buildBatteryEventBundle(
            batteryLevel = 15,
            isCharging = true
        )
        val intent = Intent().apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, initialBundle)
        }

        val controller = Robolectric.buildActivity(TaskerEventActivity::class.java, intent).setup()
        val activity = controller.get()

        val swChargingOnly = activity.findViewById<MaterialSwitch>(R.id.swChargingOnly)
        val swFilterBattery = activity.findViewById<MaterialSwitch>(R.id.swFilterBattery)
        val sliderBatteryLevel = activity.findViewById<Slider>(R.id.sliderBatteryLevel)
        val txtBlurb = activity.findViewById<TextView>(R.id.txtBlurbPreview)

        assertTrue(swChargingOnly.isChecked)
        assertTrue(swFilterBattery.isChecked)
        assertEquals(15f, sliderBatteryLevel.value)
        assertEquals("Batería: 15% (Cargando)", txtBlurb.text.toString())

        val btnEventSave = activity.findViewById<MaterialButton>(R.id.btnEventSave)
        btnEventSave.performClick()

        val shadow = Shadows.shadowOf(activity)
        assertEquals(Activity.RESULT_OK, shadow.resultCode)

        val resultIntent = shadow.resultIntent
        assertNotNull(resultIntent)
        val savedBundle = resultIntent.getBundleExtra(TaskerConstants.EXTRA_BUNDLE)
        assertNotNull(savedBundle)
        val parsed = TaskerBundleManager.parseEvent(savedBundle)
        assertEquals(TaskerConstants.EVENT_BATTERY_CHANGED, parsed.eventType)
        assertEquals(15, parsed.batteryLevel)
        assertEquals(true, parsed.isCharging)
    }

    @Test
    fun activityLoadsExistingConnectionBundle() {
        val initialBundle = TaskerBundleManager.buildConnectionEventBundle(connected = true)
        val intent = Intent().apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, initialBundle)
        }

        val controller = Robolectric.buildActivity(TaskerEventActivity::class.java, intent).setup()
        val activity = controller.get()

        val txtBlurb = activity.findViewById<TextView>(R.id.txtBlurbPreview)
        assertEquals("Gafas Conectadas", txtBlurb.text.toString())

        val btnEventSave = activity.findViewById<MaterialButton>(R.id.btnEventSave)
        btnEventSave.performClick()

        val shadow = Shadows.shadowOf(activity)
        assertEquals(Activity.RESULT_OK, shadow.resultCode)
        val resultBundle = shadow.resultIntent.getBundleExtra(TaskerConstants.EXTRA_BUNDLE)
        val parsed = TaskerBundleManager.parseEvent(resultBundle)
        assertEquals(TaskerConstants.EVENT_CONNECTED, parsed.eventType)
        assertEquals("CONNECTED", parsed.connectionState)
    }

    @Test
    fun cancelButtonFinishesWithResultCanceled() {
        val controller = Robolectric.buildActivity(TaskerEventActivity::class.java).setup()
        val activity = controller.get()

        val btnEventBack = activity.findViewById<MaterialButton>(R.id.btnEventBack)
        btnEventBack.performClick()

        assertTrue(activity.isFinishing)
        val shadow = Shadows.shadowOf(activity)
        assertEquals(Activity.RESULT_CANCELED, shadow.resultCode)
    }
}
