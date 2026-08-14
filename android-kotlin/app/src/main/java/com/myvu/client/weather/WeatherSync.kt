package com.myvu.client.weather

import android.content.Context
import android.os.Handler
import com.myvu.client.app.feature.Weather
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.nav.LocationSource
import com.myvu.client.nav.Osrm
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class WeatherSync(
    context: Context,
    private val conn: Handler,
    private val sender: Sender,
    private val locationSource: LocationSource
) {

    fun interface Sender {
        fun send(actionJson: String)
    }

    private val context: Context = context.applicationContext
    private val net: ExecutorService = Executors.newSingleThreadExecutor()

    private var running: Boolean = false
    private var inFlight: Boolean = false
    private var lastWeatherJson: String? = null

    val refreshMs: Long
        get() {
            val minutes = Prefs.weatherIntervalMinutes(context)
            return Math.max(15, minutes) * 60 * 1000L
        }

    fun start() {
        running = true
        refresh()
    }

    fun stop() {
        running = false
        conn.removeCallbacks(refreshTick)
        locationSource.stop()
    }

    private val refreshTick = Runnable { refresh() }

    fun refresh() {
        if (!Prefs.weatherEnabled(context)) {
            LogBus.trace("weather sync is switched off")
            return
        }
        if (inFlight) return
        inFlight = true

        val place = Prefs.weatherPlace(context).trim()
        if (place.isNotEmpty()) {
            fetchForPlace(place)
        } else {
            fetchForCurrentLocation()
        }
    }

    /** Fetches a one-shot reading for an AI query without changing sync scheduling. */
    fun query(callback: QueryCallback) {
        val place = Prefs.weatherPlace(context).trim()
        if (place.isNotEmpty()) {
            net.execute {
                try {
                    val point = Osrm.parsePoint(place)
                    val reading = OpenMeteo.fetch(point[0], point[1], place)
                    conn.post { callback.onSuccess(reading) }
                } catch (e: Exception) {
                    conn.post { callback.onFailure(e) }
                }
            }
        } else {
            queryCurrentLocation(callback)
        }
    }

    private fun queryCurrentLocation(callback: QueryCallback) {
        val done = booleanArrayOf(false)
        val timeout = Runnable {
            if (done[0]) return@Runnable
            done[0] = true
            locationSource.stop()
            callback.onFailure(IllegalStateException("no location fix for weather"))
        }
        conn.postDelayed(timeout, FIX_TIMEOUT_MS)

        locationSource.start(object : LocationSource.Listener {
            override fun onFix(lat: Double, lon: Double, speedMps: Float, bearing: Float) {
                if (done[0]) return
                done[0] = true
                conn.removeCallbacks(timeout)
                locationSource.stop()
                net.execute {
                    try {
                        val reading = OpenMeteo.fetch(lat, lon, null)
                        conn.post { callback.onSuccess(reading) }
                    } catch (e: Exception) {
                        conn.post { callback.onFailure(e) }
                    }
                }
            }

            override fun onUnavailable(reason: String) {
                if (done[0]) return
                done[0] = true
                conn.removeCallbacks(timeout)
                locationSource.stop()
                callback.onFailure(IllegalStateException("location unavailable for weather: $reason"))
            }
        })
    }

    fun syncReading(reading: Weather.Reading) {
        conn.post {
            val json = Weather.build(reading)
            lastWeatherJson = json
            sender.send(json)
            LogBus.log(
                "weather synced: ${reading.condition} ${reading.temp}°C" +
                        if (reading.areaName == null) "" else " (${reading.areaName})"
            )
        }
    }

    interface QueryCallback {
        fun onSuccess(reading: Weather.Reading)

        fun onFailure(error: Exception)
    }

    private fun fetchForPlace(place: String) {
        net.execute {
            try {
                val p = Osrm.parsePoint(place)
                fetchAndSend(p[0], p[1], place)
            } catch (e: Exception) {
                fail("could not resolve \"$place\"", e)
            }
        }
    }

    private fun fetchForCurrentLocation() {
        val done = booleanArrayOf(false)
        val timeout = Runnable {
            if (done[0]) return@Runnable
            done[0] = true
            locationSource.stop()
            fail("no location fix for weather", null)
        }
        conn.postDelayed(timeout, FIX_TIMEOUT_MS)

        locationSource.start(object : LocationSource.Listener {
            override fun onFix(lat: Double, lon: Double, speedMps: Float, bearing: Float) {
                if (done[0]) return
                done[0] = true
                conn.removeCallbacks(timeout)
                locationSource.stop()
                net.execute { fetchAndSend(lat, lon, null) }
            }

            override fun onUnavailable(reason: String) {
                if (done[0]) return
                done[0] = true
                conn.removeCallbacks(timeout)
                fail("location unavailable for weather: $reason", null)
            }
        })
    }

    private fun fetchAndSend(lat: Double, lon: Double, areaName: String?) {
        try {
            val r = OpenMeteo.fetch(lat, lon, areaName)
            val json = Weather.build(r)
            conn.post {
                lastWeatherJson = json
                sender.send(json)
                LogBus.log(
                    "weather synced: ${r.condition} ${r.temp}°C" +
                            if (r.areaName == null) "" else " (${r.areaName})"
                )
                done(refreshMs)
            }
        } catch (e: Exception) {
            fail("weather fetch failed", e)
        }
    }

    private fun fail(message: String, e: Exception?) {
        conn.post {
            if (e != null) LogBus.warn("$message: $e")
            else LogBus.warn(message)
            val cached = lastWeatherJson
            if (cached != null) {
                sender.send(cached)
                LogBus.log("re-synced cached weather")
            }
            done(Math.min(RETRY_MS, refreshMs))
        }
    }

    private fun done(nextInMs: Long) {
        inFlight = false
        conn.removeCallbacks(refreshTick)
        if (running) conn.postDelayed(refreshTick, nextInMs)
    }

    companion object {
        private const val DEFAULT_REFRESH_MS = 60 * 60 * 1000L
        private const val RETRY_MS = 5 * 60 * 1000L
        private const val FIX_TIMEOUT_MS = 25 * 1000L
    }
}
