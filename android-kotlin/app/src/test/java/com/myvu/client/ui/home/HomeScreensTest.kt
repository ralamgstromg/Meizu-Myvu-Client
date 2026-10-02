package com.myvu.client.ui.home

import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.myvu.client.R
import com.myvu.client.routines.RoutineStore
import com.myvu.client.routines.ScheduledRoutine
import com.myvu.client.ui.PermissionsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreensTest {

    @Test
    fun allTabsOpenWithoutCrashing() {
        val activity = Robolectric.buildActivity(HomeActivity::class.java).setup().get()
        val nav: BottomNavigationView = activity.findViewById(R.id.homeBottomNav)
        for (tab in listOf(R.id.navLibrary, R.id.navAgent, R.id.navMore, R.id.navToday)) {
            nav.selectedItemId = tab
            ShadowLooper.idleMainLooper()
            assertTrue(activity.supportFragmentManager.findFragmentById(R.id.homeContainer) != null)
        }
    }

    @Test
    fun agentTabListsSavedRoutines() {
        RoutineStore.save(RuntimeEnvironment.getApplication(), ScheduledRoutine.templates().first())
        val activity = Robolectric.buildActivity(HomeActivity::class.java).setup().get()
        activity.selectTab(R.id.navAgent)
        ShadowLooper.idleMainLooper()

        val list: LinearLayout = activity.findViewById(R.id.listAgentRoutines)
        assertEquals(1, list.childCount)
        assertEquals("Buenos días", list.getChildAt(0).findViewById<TextView>(R.id.txtRoutineName).text.toString())
    }

    @Test
    fun scheduleDescriptionIsReadable() {
        val r = ScheduledRoutine.templates().first()
        assertEquals("7:00 a. m. · lunes a viernes", AgentFragment.describeSchedule(r))
    }

    @Test
    fun permissionsCenterRendersEntries() {
        val activity = Robolectric.buildActivity(PermissionsActivity::class.java).setup().get()
        val root = (activity.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            as android.widget.ScrollView).getChildAt(0) as LinearLayout
        assertTrue(root.childCount > 8)
    }
}
