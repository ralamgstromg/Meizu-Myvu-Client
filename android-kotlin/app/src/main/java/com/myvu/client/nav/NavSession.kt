package com.myvu.client.nav

import android.content.Context
import android.os.Handler
import com.myvu.client.app.feature.NavCommands
import com.myvu.client.core.LogBus
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NavSession(
    context: Context,
    private val conn: Handler,
    private val sender: Sender,
    private val locationSource: LocationSource
) {

    fun interface Sender {
        fun send(actionJson: String, targetPkg: String, sourcePkg: String)
    }

    private val context: Context = context.applicationContext
    private val net: ExecutorService = Executors.newSingleThreadExecutor()

    private var route: Route? = null
    private var tracker: RouteTracker? = null
    @Volatile
    private var active: Boolean = false

    private var destLat: Double = 0.0
    private var destLon: Double = 0.0
    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0
    private var lastRerouteAt: Long = 0
    private var rideDistanceM: Double = 0.0

    fun isActive(): Boolean = active

    fun start(destination: String) {
        if (active) {
            LogBus.warn("navigation already running -- stop it first")
            return
        }
        active = true
        rideDistanceM = 0.0

        locationSource.start(object : LocationSource.Listener {
            private var routed = false

            override fun onFix(lat: Double, lon: Double, speedMps: Float, bearing: Float) {
                if (!active) return
                if (!routed) {
                    routed = true
                    beginRouting(lat, lon, destination)
                }
                onPosition(lat, lon, speedMps)
            }

            override fun onUnavailable(reason: String) {
                LogBus.warn("navigation cannot start: $reason")
                stop()
            }
        })
    }

    private fun beginRouting(lat: Double, lon: Double, destination: String) {
        LogBus.log("routing to \"$destination\"...")
        net.execute {
            try {
                val dest = Osrm.parsePoint(destination)
                val r = Osrm.route(lat, lon, dest[0], dest[1], "driving")
                destLat = dest[0]
                destLon = dest[1]
                conn.post { adoptRoute(r, true) }
            } catch (e: Exception) {
                LogBus.error("routing failed", e)
                stop()
            }
        }
    }

    private fun adoptRoute(r: Route, openHud: Boolean) {
        route = r
        tracker = RouteTracker(r)
        if (!openHud) {
            LogBus.log("re-routed: ${r.totalDistanceM}m remaining")
            return
        }
        try {
            val first = if (r.steps.isEmpty()) null else r.steps[0]
            val actionJson = NavCommands.buildStart(
                first?.ic ?: IcMap.DEFAULT_IC,
                r.totalDistanceM,
                r.totalDistanceM,
                r.totalDurationS.toInt(),
                first?.road ?: "",
                first?.atM?.toInt() ?: 0,
                "0", 0, 1, 0, 0, 0, false, false
            )
            sender.send(actionJson, NavCommands.LAUNCH_TARGET_PKG, NavCommands.SOURCE_PKG)
            LogBus.log(
                "navigation started: ${r.totalDistanceM}m, " +
                        "${Math.round(r.totalDurationS / 60)} min, ${r.steps.size} steps"
            )
        } catch (e: Exception) {
            LogBus.error("could not start navigation", e)
        }
    }

    private fun onPosition(lat: Double, lon: Double, speedMps: Float) {
        conn.post {
            val trk = tracker ?: return@post
            val r = route ?: return@post
            if (!active) return@post

            if (lastLat != 0.0 || lastLon != 0.0) {
                rideDistanceM += Geo.haversine(lastLat, lastLon, lat, lon)
            }
            lastLat = lat
            lastLon = lon

            val s = trk.update(lat, lon)
            if (s.offRoute) {
                maybeReroute(lat, lon, s.deviationM)
                return@post
            }
            pushFrame(s, speedMps, r)
        }
    }

    private fun pushFrame(s: RouteTracker.State, speedMps: Float, r: Route) {
        try {
            val next = s.nextStep
            val fraction = if (r.totalDistanceM > 0) s.remainingM / r.totalDistanceM else 0.0
            val remainingS = (r.totalDurationS * fraction).toInt()

            val speedText = if (speedMps >= 0) Math.round(speedMps * 3.6).toString() else "0"

            val actionJson = NavCommands.buildNaviInfo(
                next?.ic ?: IcMap.DEFAULT_IC,
                r.totalDistanceM,
                s.remainingM.toInt(),
                remainingS,
                next?.road ?: "",
                s.distToNextM.toInt(),
                speedText,
                rideDistanceM.toInt(),
                1, 0, 0
            )
            sender.send(actionJson, NavCommands.FRAME_TARGET_PKG, NavCommands.SOURCE_PKG)

            if (s.remainingM < 20) {
                LogBus.log("destination reached")
                stop()
            }
        } catch (e: Exception) {
            LogBus.error("could not send a nav frame", e)
        }
    }

    private fun maybeReroute(lat: Double, lon: Double, deviation: Double) {
        val now = System.currentTimeMillis()
        if (now - lastRerouteAt < REROUTE_COOLDOWN_MS) return
        lastRerouteAt = now

        LogBus.log(String.format(Locale.US, "off route by %.0fm -- recalculating", deviation))
        net.execute {
            try {
                val r = Osrm.route(lat, lon, destLat, destLon, "driving")
                conn.post {
                    if (active) adoptRoute(r, false)
                }
            } catch (e: Exception) {
                LogBus.error("re-routing failed", e)
            }
        }
    }

    fun stop() {
        if (!active) return
        active = false
        locationSource.stop()
        try {
            sender.send(
                NavCommands.buildStop(),
                NavCommands.FRAME_TARGET_PKG,
                NavCommands.SOURCE_PKG
            )
        } catch (e: Exception) {
            LogBus.error("could not send navi_stop", e)
        }
        route = null
        tracker = null
        lastLat = 0.0
        lastLon = 0.0
        LogBus.log("navigation stopped")
    }

    fun shutdown() {
        stop()
        net.shutdownNow()
    }

    fun sendCalibrationFrame(ic: Int, roadName: String) {
        try {
            sender.send(
                NavCommands.buildNaviInfo(ic, 1000, 1000, 120, roadName, 300, "0", 0, 1, 0, 0),
                NavCommands.FRAME_TARGET_PKG,
                NavCommands.SOURCE_PKG
            )
            LogBus.log("calibration frame sent with ic=$ic")
        } catch (e: Exception) {
            LogBus.error("calibration frame failed", e)
        }
    }

    companion object {
        private const val REROUTE_COOLDOWN_MS = 15000L
    }
}
