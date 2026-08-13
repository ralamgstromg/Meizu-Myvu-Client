package com.myvu.client.weather

import com.myvu.client.app.feature.Weather
import com.myvu.client.core.SslUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

object OpenMeteo {
    private const val BASE = "https://api.open-meteo.com/v1/forecast"
    private const val USER_AGENT = "myvu-android-client/1.0"
    private const val TIMEOUT_MS = 20000
    private const val FORECAST_DAYS = 7

    @JvmStatic
    @Throws(IOException::class, JSONException::class)
    fun fetch(lat: Double, lon: Double, areaName: String?): Weather.Reading {
        val url = (BASE +
                "?latitude=" + fmt(lat) +
                "&longitude=" + fmt(lon) +
                "&current=temperature_2m,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset" +
                "&timezone=auto" +
                "&forecast_days=" + FORECAST_DAYS)

        val root = JSONObject(get(url))
        val r = Weather.Reading()
        r.areaName = areaName
        r.lastUpdate = Weather.timestamp(System.currentTimeMillis())

        val current = root.optJSONObject("current")
        if (current != null) {
            r.temp = Math.round(current.optDouble("temperature_2m", 0.0)).toInt()
            val c = WeatherCodes.of(current.optInt("weather_code", -1))
            r.iconCode = c.iconCode
            r.condition = c.text
        }

        val daily = root.optJSONObject("daily")
        if (daily != null) {
            val dates = daily.optJSONArray("time")
            val max = daily.optJSONArray("temperature_2m_max")
            val min = daily.optJSONArray("temperature_2m_min")
            val codes = daily.optJSONArray("weather_code")
            val sunrise = daily.optJSONArray("sunrise")
            val sunset = daily.optJSONArray("sunset")

            if (max != null && max.length() > 0) r.dayTempMax = Math.round(max.optDouble(0, 0.0)).toInt()
            if (min != null && min.length() > 0) r.dayTempMin = Math.round(min.optDouble(0, 0.0)).toInt()
            if (sunrise != null && sunrise.length() > 0) r.sunriseTime = isoToStamp(sunrise.optString(0))
            if (sunset != null && sunset.length() > 0) r.sunsetTime = isoToStamp(sunset.optString(0))

            val n = dates?.length() ?: 0
            for (i in 0 until n) {
                val d = Weather.Day()
                d.date = dates?.optString(i)
                if (max != null && i < max.length()) d.tempMax = Math.round(max.optDouble(i)).toInt()
                if (min != null && i < min.length()) d.tempMin = Math.round(min.optDouble(i)).toInt()
                if (codes != null && i < codes.length()) {
                    val c = WeatherCodes.of(codes.optInt(i, -1))
                    d.iconCode = c.iconCode
                    d.condition = c.text
                }
                r.futureDay.add(d)
            }
        }
        return r
    }

    private fun isoToStamp(iso: String?): String? {
        if (iso.isNullOrEmpty()) return null
        val s = iso.replace('T', ' ')
        return if (s.length == 16) "$s:00" else s
    }

    private fun fmt(d: Double): String = String.format(Locale.US, "%.4f", d)

    @Throws(IOException::class)
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        SslUtils.applySslBypass(c)
        try {
            c.requestMethod = "GET"
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            val code = c.responseCode
            if (code != 200) throw IOException("weather HTTP $code")
            val input = c.inputStream
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var n: Int
            while (input.read(buf).also { n = it } > 0) out.write(buf, 0, n)
            input.close()
            return out.toString("UTF-8")
        } finally {
            c.disconnect()
        }
    }
}
