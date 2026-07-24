package com.myvu.client.transport.ble;

import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;

import com.myvu.client.core.LogBus;

/**
 * Keep-alive for the BLE link with adaptive duty-cycling.
 *
 * Bytes and standard interval are taken verbatim from the app's
 * BleRequestDispatcher.HEART_BEAT_DATA / HEART_BEAT_INTERVAL. Without this the
 * glasses' watchdog drops the link after a few seconds of quiet, which presents
 * as a mysterious "disconnected by peer" partway through the handshake.
 *
 * When active data (RFCOMM / notifications) is being transmitted, the heartbeat
 * interval dynamically switches to an extended duration (15000ms) to conserve
 * battery while maintaining connection integrity.
 *
 * The writes are queued through GattQueue like any other operation, so they
 * interleave safely with data rather than racing it.
 */
public class BleHeartbeat {

    public static final byte[] HEARTBEAT_DATA = { 0, 0, 9, 16, 0 };
    public static final long STANDARD_INTERVAL_MS = 3000;
    public static final long EXTENDED_INTERVAL_MS = 15000;
    public static final long ACTIVE_DATA_TIMEOUT_MS = 15000;

    public interface Scheduler {
        void postDelayed(Runnable runnable, long delayMs);
        void removeCallbacks(Runnable runnable);
    }

    public interface TimeProvider {
        long currentTimeMillis();
    }

    private final GattQueue queue;
    private final BluetoothGattCharacteristic urgentChar;
    private final Scheduler scheduler;
    private final TimeProvider timeProvider;

    private boolean running;
    private int count;
    private long lastDataActivityTime = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (queue != null) {
                queue.enqueue(GattOp.write(urgentChar, HEARTBEAT_DATA));
            }
            count++;
            long currentInterval = getInterval();
            if (count == 1) {
                LogBus.log("BLE heartbeat active (every " + (currentInterval / 1000) + "s)");
            }
            scheduler.postDelayed(this, currentInterval);
        }
    };

    public BleHeartbeat(GattQueue queue, BluetoothGattCharacteristic urgentChar, Handler gatt) {
        this(queue, urgentChar, gatt != null ? new HandlerScheduler(gatt) : null, System::currentTimeMillis);
    }

    public BleHeartbeat(GattQueue queue, BluetoothGattCharacteristic urgentChar, Scheduler scheduler) {
        this(queue, urgentChar, scheduler, System::currentTimeMillis);
    }

    public BleHeartbeat(GattQueue queue, BluetoothGattCharacteristic urgentChar, Scheduler scheduler, TimeProvider timeProvider) {
        this.queue = queue;
        this.urgentChar = urgentChar;
        this.scheduler = scheduler != null ? scheduler : new HandlerScheduler(null);
        this.timeProvider = timeProvider != null ? timeProvider : System::currentTimeMillis;
    }

    public void notifyDataActivity() {
        this.lastDataActivityTime = timeProvider.currentTimeMillis();
    }

    public boolean isDataActive() {
        return (timeProvider.currentTimeMillis() - lastDataActivityTime) < ACTIVE_DATA_TIMEOUT_MS;
    }

    public long getInterval() {
        return isDataActive() ? EXTENDED_INTERVAL_MS : STANDARD_INTERVAL_MS;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running) return;
        running = true;
        count = 0;
        scheduler.postDelayed(tick, getInterval());
    }

    public void stop() {
        running = false;
        scheduler.removeCallbacks(tick);
    }

    private static class HandlerScheduler implements Scheduler {
        private final Handler handler;

        HandlerScheduler(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            if (handler != null) {
                handler.postDelayed(runnable, delayMs);
            }
        }

        @Override
        public void removeCallbacks(Runnable runnable) {
            if (handler != null) {
                handler.removeCallbacks(runnable);
            }
        }
    }
}
