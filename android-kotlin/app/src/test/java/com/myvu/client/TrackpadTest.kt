package com.myvu.client

import com.myvu.client.app.AppLayer
import com.myvu.client.app.feature.Trackpad
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the "phonepad" wire format against the official app's TouchpadUtil:
 * {"action":"phonepad","data":{...}} to the launcher, taps as click/doubleClick/
 * longPress, swipes as "gestureMode" with actionType = direction key code.
 */
class TrackpadTest {

    private fun dataOf(actionJson: String): JSONObject {
        val env = JSONObject(actionJson)
        assertEquals("phonepad", env.getString("action"))
        return env.getJSONObject("data")
    }

    @Test
    fun testClickShape() {
        val d = dataOf(Trackpad.click())
        assertEquals("click", d.getString("action"))
        assertTrue("carries timestamp", d.has("time"))
    }

    @Test
    fun testDoubleClickShape() {
        val d = dataOf(Trackpad.doubleClick())
        assertEquals("doubleClick", d.getString("action"))
        assertTrue(d.has("time"))
    }

    @Test
    fun testLongPressShape() {
        val d = dataOf(Trackpad.longPress())
        assertEquals("longPress", d.getString("action"))
        assertTrue(d.has("time"))
    }

    @Test
    fun testStartShape() {
        val d = dataOf(Trackpad.start())
        assertEquals("start", d.getString("action"))
        assertTrue(d.has("time"))
    }

    @Test
    fun testStopShape() {
        val d = dataOf(Trackpad.stop())
        assertEquals("stop", d.getString("action"))
        assertTrue(d.has("time"))
    }

    @Test
    fun testSwipeDirectionsAreKeyCodes() {
        assertEquals(19, Trackpad.SWIPE_UP)
        assertEquals(20, Trackpad.SWIPE_DOWN)
        assertEquals(21, Trackpad.SWIPE_LEFT)
        assertEquals(22, Trackpad.SWIPE_RIGHT)
    }

    @Test
    fun testSwipePayloadShape() {
        val d = dataOf(Trackpad.swipe(Trackpad.SWIPE_UP, 10f, 20f, 30f, 40f, 0.5f, 0.6f))
        assertEquals("gestureMode", d.getString("action"))
        assertEquals(19, d.getInt("actionType"))
        assertEquals(10.0, d.getDouble("startX"), 0.001)
        assertEquals(20.0, d.getDouble("startY"), 0.001)
        assertEquals(30.0, d.getDouble("endX"), 0.001)
        assertEquals(40.0, d.getDouble("endY"), 0.001)
        assertEquals(0.5, d.getDouble("speedX"), 0.001)
        assertEquals(0.6, d.getDouble("speedY"), 0.001)
        assertTrue(d.has("time"))
    }

    @Test
    fun testAppLayerLauncherRoutingConstant() {
        assertEquals("com.upuphone.star.launcher", AppLayer.PKG_LAUNCHER)
    }

    @Test
    fun testSwipeDirectionPolarityCalculation() {
        // Horizontal swipes
        val rightDir = calculateSwipeDirection(100f, 0f)
        val leftDir = calculateSwipeDirection(-100f, 0f)
        assertEquals(Trackpad.SWIPE_RIGHT, rightDir)
        assertEquals(Trackpad.SWIPE_LEFT, leftDir)

        // Vertical swipes
        val downDir = calculateSwipeDirection(0f, 100f)
        val upDir = calculateSwipeDirection(0f, -100f)
        assertEquals(Trackpad.SWIPE_DOWN, downDir)
        assertEquals(Trackpad.SWIPE_UP, upDir)
    }

    private fun calculateSwipeDirection(dx: Float, dy: Float): Int {
        return if (Math.abs(dx) > Math.abs(dy)) {
            if (dx > 0) Trackpad.SWIPE_RIGHT else Trackpad.SWIPE_LEFT
        } else {
            if (dy > 0) Trackpad.SWIPE_DOWN else Trackpad.SWIPE_UP
        }
    }
}
