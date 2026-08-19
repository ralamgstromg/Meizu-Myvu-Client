package com.myvu.client.ui

import android.widget.AutoCompleteTextView
import com.myvu.client.R
import com.myvu.client.app.feature.GestureAction
import com.myvu.client.core.Prefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
class SettingsGestureConfigTest {

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().apply()
    }

    @After
    fun tearDown() {
        val context = RuntimeEnvironment.getApplication()
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().apply()
    }

    @Test
    fun defaultGestureActionsMatchPrefsAndUi() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()

        val actTap = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadTap)
        val actDoubleTap = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadDoubleTap)
        val actTripleTap = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadTripleTap)
        val actSwipeFwd = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadSwipeForward)
        val actSwipeBwd = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadSwipeBackward)
        val actLongPress = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadLongPress)

        assertNotNull("actTouchpadTap should be present in layout", actTap)
        assertNotNull("actTouchpadDoubleTap should be present in layout", actDoubleTap)
        assertNotNull("actTouchpadTripleTap should be present in layout", actTripleTap)
        assertNotNull("actTouchpadSwipeForward should be present in layout", actSwipeFwd)
        assertNotNull("actTouchpadSwipeBackward should be present in layout", actSwipeBwd)
        assertNotNull("actTouchpadLongPress should be present in layout", actLongPress)

        assertEquals(GestureAction.NONE.displayName, actTap.text.toString())
        assertEquals(GestureAction.MEDIA_PLAY_PAUSE.displayName, actDoubleTap.text.toString())
        assertEquals(GestureAction.LAUNCH_PHONE_ASSISTANT.displayName, actTripleTap.text.toString())
        assertEquals(GestureAction.MEDIA_NEXT.displayName, actSwipeFwd.text.toString())
        assertEquals(GestureAction.MEDIA_PREV.displayName, actSwipeBwd.text.toString())
        assertEquals(GestureAction.LAUNCH_LOCAL_AI.displayName, actLongPress.text.toString())
    }

    @Test
    fun initializesWithCustomSavedPreferences() {
        val context = RuntimeEnvironment.getApplication()
        Prefs.setTouchpadTapAction(context, GestureAction.WEATHER_SYNC.id)
        Prefs.setTouchpadDoubleTapAction(context, GestureAction.ZEN_MODE.id)
        Prefs.setTouchpadTripleTapAction(context, GestureAction.OPEN_TELEPROMPTER.id)
        Prefs.setTouchpadSwipeForwardAction(context, GestureAction.TOGGLE_MIRROR.id)
        Prefs.setTouchpadSwipeBackwardAction(context, GestureAction.LAUNCH_LOCAL_AI.id)
        Prefs.setTouchpadLongPressAction(context, GestureAction.MEDIA_PLAY_PAUSE.id)

        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()

        val actTap = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadTap)
        val actDoubleTap = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadDoubleTap)
        val actTripleTap = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadTripleTap)
        val actSwipeFwd = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadSwipeForward)
        val actSwipeBwd = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadSwipeBackward)
        val actLongPress = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadLongPress)

        assertEquals(GestureAction.WEATHER_SYNC.displayName, actTap.text.toString())
        assertEquals(GestureAction.ZEN_MODE.displayName, actDoubleTap.text.toString())
        assertEquals(GestureAction.OPEN_TELEPROMPTER.displayName, actTripleTap.text.toString())
        assertEquals(GestureAction.TOGGLE_MIRROR.displayName, actSwipeFwd.text.toString())
        assertEquals(GestureAction.LAUNCH_LOCAL_AI.displayName, actSwipeBwd.text.toString())
        assertEquals(GestureAction.MEDIA_PLAY_PAUSE.displayName, actLongPress.text.toString())
    }

    @Test
    fun selectingNewOptionUpdatesPreferencesForEveryGesture() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()

        val actTap = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadTap)
        val actDoubleTap = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadDoubleTap)
        val actTripleTap = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadTripleTap)
        val actSwipeFwd = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadSwipeForward)
        val actSwipeBwd = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadSwipeBackward)
        val actLongPress = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadLongPress)

        val actions = GestureAction.entries.toTypedArray()
        fun indexOfAction(target: GestureAction): Int = actions.indexOfFirst { it == target }

        // 1. Select OPEN_TELEPROMPTER on Tap
        val posTeleprompter = indexOfAction(GestureAction.OPEN_TELEPROMPTER)
        actTap.onItemClickListener?.onItemClick(null, null, posTeleprompter, posTeleprompter.toLong())
        assertEquals(GestureAction.OPEN_TELEPROMPTER.id, Prefs.touchpadTapAction(activity))

        // 2. Select LAUNCH_PHONE_ASSISTANT on Double Tap
        val posPhone = indexOfAction(GestureAction.LAUNCH_PHONE_ASSISTANT)
        actDoubleTap.onItemClickListener?.onItemClick(null, null, posPhone, posPhone.toLong())
        assertEquals(GestureAction.LAUNCH_PHONE_ASSISTANT.id, Prefs.touchpadDoubleTapAction(activity))

        // 3. Select ZEN_MODE on Triple Tap
        val posZen = indexOfAction(GestureAction.ZEN_MODE)
        actTripleTap.onItemClickListener?.onItemClick(null, null, posZen, posZen.toLong())
        assertEquals(GestureAction.ZEN_MODE.id, Prefs.touchpadTripleTapAction(activity))

        // 4. Select WEATHER_SYNC on Swipe Forward
        val posWeather = indexOfAction(GestureAction.WEATHER_SYNC)
        actSwipeFwd.onItemClickListener?.onItemClick(null, null, posWeather, posWeather.toLong())
        assertEquals(GestureAction.WEATHER_SYNC.id, Prefs.touchpadSwipeForwardAction(activity))

        // 5. Select TOGGLE_MIRROR on Swipe Backward
        val posMirror = indexOfAction(GestureAction.TOGGLE_MIRROR)
        actSwipeBwd.onItemClickListener?.onItemClick(null, null, posMirror, posMirror.toLong())
        assertEquals(GestureAction.TOGGLE_MIRROR.id, Prefs.touchpadSwipeBackwardAction(activity))

        // 6. Select NONE on Long Press
        val posNone = indexOfAction(GestureAction.NONE)
        actLongPress.onItemClickListener?.onItemClick(null, null, posNone, posNone.toLong())
        assertEquals(GestureAction.NONE.id, Prefs.touchpadLongPressAction(activity))
    }

    @Test
    fun adapterContainsAllAvailableGestureActions() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()

        val actTap = activity.findViewById<AutoCompleteTextView>(R.id.actTouchpadTap)
        val adapter = actTap.adapter
        assertNotNull(adapter)
        assertEquals(GestureAction.entries.size, adapter.count)

        for (i in 0 until adapter.count) {
            val item = adapter.getItem(i).toString()
            assertTrue(GestureAction.entries.any { it.displayName == item })
        }
    }
}
