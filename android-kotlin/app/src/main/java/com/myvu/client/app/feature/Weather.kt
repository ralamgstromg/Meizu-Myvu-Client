package com.myvu.client.app.feature

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Weather sync.
 */
object Weather {
    const val ACTION: String = "weather"
    const val SYNC_REQUEST_ACTION: String = "syncWeather"
    private const val TS_FORMAT = "yyyy-MM-dd HH:mm:ss"

    /** One entry of the 7-day forecast (ArFutureDay). */
    class Day {
        @JvmField var date: String? = null
        @JvmField var tempMax: Int? = null
        @JvmField var tempMin: Int? = null
        @JvmField var condition: String? = null
        @JvmField var iconCode: String? = null
    }

    /** ArWeatherModel. Null fields are omitted from the wire. */
    class Reading {
        @JvmField var temp: Int? = null
        @JvmField var condition: String? = null
        @JvmField var dayTempMax: Int? = null
        @JvmField var dayTempMin: Int? = null
        @JvmField var areaName: String? = null
        @JvmField var iconCode: String = "0"
        @JvmField var lastUpdate: String? = null
        @JvmField var sunriseTime: String? = null
        @JvmField var sunsetTime: String? = null
        @JvmField var aqi: Int = 0
        @JvmField var quality: String = ""
        @JvmField val futureDay: MutableList<Day> = ArrayList()
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun build(r: Reading): String {
        val data = JSONObject()
        putIfPresent(data, "temp", r.temp)
        putIfPresent(data, "weather", r.condition)
        putIfPresent(data, "dayTempMax", r.dayTempMax)
        putIfPresent(data, "dayTempMin", r.dayTempMin)
        putIfPresent(data, "areaName", r.areaName)
        data.put("iconCode", r.iconCode)
        putIfPresent(data, "lastUpdate", r.lastUpdate)
        putIfPresent(data, "sunriseTime", r.sunriseTime)
        putIfPresent(data, "sunsetTime", r.sunsetTime)
        data.put("aqi", r.aqi)
        data.put("quality", r.quality)

        val days = JSONArray()
        for (d in r.futureDay) {
            val o = JSONObject()
            putIfPresent(o, "date", d.date)
            putIfPresent(o, "dayTempMax", d.tempMax)
            putIfPresent(o, "dayTempMin", d.tempMin)
            putIfPresent(o, "weather", d.condition)
            putIfPresent(o, "iconCode", d.iconCode)
            days.put(o)
        }
        data.put("futureDay", days)

        return JSONObject()
            .put("action", ACTION)
            .put("data", data)
            .toString()
    }

    @JvmStatic
    fun timestamp(epochMs: Long): String {
        return SimpleDateFormat(TS_FORMAT, Locale.US).format(Date(epochMs))
    }

    @JvmStatic
    fun isSyncRequest(action: JSONObject?): Boolean {
        if (action == null) return false
        val act = action.optString("action")
        return SYNC_REQUEST_ACTION == act || "query_weather" == act
    }

    private fun putIfPresent(o: JSONObject, key: String, value: Any?) {
        if (value != null) o.put(key, value)
    }
}
