package com.myvu.client.app.feature

import org.json.JSONException
import org.json.JSONObject

/**
 * Builds "phonepad" (trackpad) messages.
 */
object Trackpad {
    const val SWIPE_UP: Int = 19
    const val SWIPE_DOWN: Int = 20
    const val SWIPE_LEFT: Int = 21
    const val SWIPE_RIGHT: Int = 22

    @JvmStatic
    fun start(): String = simple("start")

    @JvmStatic
    fun stop(): String = simple("stop")

    @JvmStatic
    fun click(): String = simple("click")

    @JvmStatic
    fun doubleClick(): String = simple("doubleClick")

    @JvmStatic
    fun longPress(): String = simple("longPress")

    @JvmStatic
    fun swipe(
        direction: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        speedX: Float,
        speedY: Float
    ): String {
        try {
            val data = JSONObject()
            data.put("action", "gestureMode")
            data.put("actionType", direction)
            data.put("startX", startX.toDouble())
            data.put("startY", startY.toDouble())
            data.put("endX", endX.toDouble())
            data.put("endY", endY.toDouble())
            data.put("speedX", speedX.toDouble())
            data.put("speedY", speedY.toDouble())
            data.put("time", System.currentTimeMillis())
            return wrap(data)
        } catch (e: JSONException) {
            throw IllegalStateException(e)
        }
    }

    private fun simple(action: String): String {
        try {
            val data = JSONObject()
            data.put("action", action)
            data.put("time", System.currentTimeMillis())
            return wrap(data)
        } catch (e: JSONException) {
            throw IllegalStateException(e)
        }
    }

    private fun wrap(data: JSONObject): String {
        val o = JSONObject()
        o.put("action", "phonepad")
        o.put("data", data)
        return o.toString()
    }
}
