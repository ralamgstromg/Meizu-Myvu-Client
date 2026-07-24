package com.myvu.client.nav;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.myvu.client.core.LogBus;

/**
 * FusedLocationProvider-backed {@link LocationSource}: better battery behaviour
 * and better urban/indoor fixes than raw GPS.
 *
 * Supports dynamic speed-based polling interval switching to save battery when
 * stationary or moving slowly, while providing high precision when moving fast.
 */
public class FusedLocationSource implements LocationSource {

    public static final long FAST_INTERVAL_MS = 2000;
    public static final long SLOW_INTERVAL_MS = 5000;
    public static final long STATIONARY_INTERVAL_MS = 15000;

    public static final float FAST_SPEED_THRESHOLD_MPS = 2.0f;
    public static final float SLOW_SPEED_THRESHOLD_MPS = 0.5f;

    private final Context context;
    private final FusedLocationProviderClient client;
    private LocationCallback callback;
    private long currentIntervalMs = FAST_INTERVAL_MS;

    public FusedLocationSource(Context context) {
        this.context = context.getApplicationContext();
        this.client = LocationServices.getFusedLocationProviderClient(this.context);
    }

    public static long calculateIntervalForSpeed(float speed) {
        if (speed > FAST_SPEED_THRESHOLD_MPS) {
            return FAST_INTERVAL_MS;
        } else if (speed >= SLOW_SPEED_THRESHOLD_MPS) {
            return SLOW_INTERVAL_MS;
        } else {
            return STATIONARY_INTERVAL_MS;
        }
    }

    public long getCurrentIntervalMs() {
        return currentIntervalMs;
    }

    @Override
    public void start(final Listener listener) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            listener.onUnavailable("location permission not granted");
            return;
        }

        currentIntervalMs = FAST_INTERVAL_MS;
        LocationRequest request = createLocationRequest(currentIntervalMs);

        callback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                Location l = result.getLastLocation();
                if (l == null) return;
                float speed = l.hasSpeed() ? l.getSpeed() : -1f;
                listener.onFix(
                        l.getLatitude(),
                        l.getLongitude(),
                        speed,
                        l.hasBearing() ? l.getBearing() : -1f);

                adjustPollingIntervalIfNeeded(speed);
            }
        };

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper());
            LogBus.log("location updates started (fused, initial " + currentIntervalMs + "ms)");
        } catch (SecurityException e) {
            listener.onUnavailable("location permission revoked: " + e.getMessage());
        }
    }

    private void adjustPollingIntervalIfNeeded(float speed) {
        long newInterval = calculateIntervalForSpeed(speed);
        if (newInterval != currentIntervalMs && callback != null && client != null) {
            currentIntervalMs = newInterval;
            try {
                LocationRequest newRequest = createLocationRequest(newInterval);
                client.requestLocationUpdates(newRequest, callback, Looper.getMainLooper());
                LogBus.log("location polling interval updated to " + newInterval + "ms for speed " + speed + "m/s");
            } catch (SecurityException e) {
                LogBus.warn("Failed to update location request interval: " + e.getMessage());
            }
        }
    }

    private static LocationRequest createLocationRequest(long intervalMs) {
        return new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs)
                .setWaitForAccurateLocation(false)
                .build();
    }

    @Override
    public void stop() {
        if (callback != null) {
            if (client != null) {
                client.removeLocationUpdates(callback);
            }
            callback = null;
            LogBus.log("location updates stopped");
        }
    }
}
