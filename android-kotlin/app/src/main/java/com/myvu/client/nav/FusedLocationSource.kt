package com.myvu.client.nav

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.myvu.client.core.LogBus

class FusedLocationSource(context: Context) : LocationSource {

    private val context: Context = context.applicationContext
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this.context)
    private var callback: LocationCallback? = null
    var currentIntervalMs: Long = FAST_INTERVAL_MS
        private set

    override fun start(listener: LocationSource.Listener) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            listener.onUnavailable("location permission not granted")
            return
        }

        currentIntervalMs = FAST_INTERVAL_MS
        val request = createLocationRequest(currentIntervalMs)

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val l = result.lastLocation ?: return
                val speed = if (l.hasSpeed()) l.speed else -1f
                listener.onFix(
                    l.latitude,
                    l.longitude,
                    speed,
                    if (l.hasBearing()) l.bearing else -1f
                )

                adjustPollingIntervalIfNeeded(speed)
            }
        }

        try {
            client.lastLocation.addOnSuccessListener { l: Location? ->
                if (l != null && callback != null) {
                    val speed = if (l.hasSpeed()) l.speed else -1f
                    listener.onFix(
                        l.latitude,
                        l.longitude,
                        speed,
                        if (l.hasBearing()) l.bearing else -1f
                    )
                }
            }
            callback?.let { cb ->
                client.requestLocationUpdates(request, cb, Looper.getMainLooper())
            }
            LogBus.log("location updates started (fused, initial ${currentIntervalMs}ms)")
        } catch (e: SecurityException) {
            listener.onUnavailable("location permission revoked: ${e.message}")
        }
    }

    private fun adjustPollingIntervalIfNeeded(speed: Float) {
        val newInterval = calculateIntervalForSpeed(speed)
        val cb = callback
        if (newInterval != currentIntervalMs && cb != null) {
            currentIntervalMs = newInterval
            try {
                val newRequest = createLocationRequest(newInterval)
                client.requestLocationUpdates(newRequest, cb, Looper.getMainLooper())
                LogBus.log("location polling interval updated to ${newInterval}ms for speed ${speed}m/s")
            } catch (e: SecurityException) {
                LogBus.warn("Failed to update location request interval: ${e.message}")
            }
        }
    }

    override fun stop() {
        callback?.let { cb ->
            client.removeLocationUpdates(cb)
            callback = null
            LogBus.log("location updates stopped")
        }
    }

    companion object {
        const val FAST_INTERVAL_MS: Long = 2000
        const val SLOW_INTERVAL_MS: Long = 5000
        const val STATIONARY_INTERVAL_MS: Long = 15000

        const val FAST_SPEED_THRESHOLD_MPS: Float = 2.0f
        const val SLOW_SPEED_THRESHOLD_MPS: Float = 0.5f

        @JvmStatic
        fun calculateIntervalForSpeed(speed: Float): Long {
            return when {
                speed > FAST_SPEED_THRESHOLD_MPS -> FAST_INTERVAL_MS
                speed >= SLOW_SPEED_THRESHOLD_MPS -> SLOW_INTERVAL_MS
                else -> STATIONARY_INTERVAL_MS
            }
        }

        private fun createLocationRequest(intervalMs: Long): LocationRequest {
            return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs)
                .setWaitForAccurateLocation(false)
                .build()
        }
    }
}
