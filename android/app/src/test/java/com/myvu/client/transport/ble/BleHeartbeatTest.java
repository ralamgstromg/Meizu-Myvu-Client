package com.myvu.client.transport.ble;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class BleHeartbeatTest {

    private TestScheduler testScheduler;
    private TestTimeProvider testTimeProvider;
    private BleHeartbeat heartbeat;

    private static class ScheduledTask {
        Runnable runnable;
        long delayMs;

        ScheduledTask(Runnable runnable, long delayMs) {
            this.runnable = runnable;
            this.delayMs = delayMs;
        }
    }

    private static class TestScheduler implements BleHeartbeat.Scheduler {
        List<ScheduledTask> tasks = new ArrayList<>();
        boolean removed = false;

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            tasks.add(new ScheduledTask(runnable, delayMs));
        }

        @Override
        public void removeCallbacks(Runnable runnable) {
            removed = true;
            tasks.removeIf(t -> t.runnable == runnable);
        }
    }

    private static class TestTimeProvider implements BleHeartbeat.TimeProvider {
        long currentTime = 100000L;

        @Override
        public long currentTimeMillis() {
            return currentTime;
        }
    }

    @Before
    public void setUp() {
        testScheduler = new TestScheduler();
        testTimeProvider = new TestTimeProvider();
        heartbeat = new BleHeartbeat(null, null, testScheduler, testTimeProvider);
    }

    @Test
    public void testInitialStateIsStopped() {
        assertFalse(heartbeat.isRunning());
        assertEquals(BleHeartbeat.STANDARD_INTERVAL_MS, heartbeat.getInterval());
    }

    @Test
    public void testStartAndStop() {
        heartbeat.start();
        assertTrue(heartbeat.isRunning());
        assertEquals(1, testScheduler.tasks.size());
        assertEquals(BleHeartbeat.STANDARD_INTERVAL_MS, testScheduler.tasks.get(0).delayMs);

        heartbeat.stop();
        assertFalse(heartbeat.isRunning());
        assertTrue(testScheduler.removed);
    }

    @Test
    public void testDataActivityExtendsInterval() {
        heartbeat.start();
        assertFalse(heartbeat.isDataActive());
        assertEquals(BleHeartbeat.STANDARD_INTERVAL_MS, heartbeat.getInterval());

        heartbeat.notifyDataActivity();
        assertTrue(heartbeat.isDataActive());
        assertEquals(BleHeartbeat.EXTENDED_INTERVAL_MS, heartbeat.getInterval());

        // Run tick task
        Runnable tick = testScheduler.tasks.get(0).runnable;
        testScheduler.tasks.clear();
        tick.run();

        assertEquals(1, testScheduler.tasks.size());
        assertEquals(BleHeartbeat.EXTENDED_INTERVAL_MS, testScheduler.tasks.get(0).delayMs);
    }

    @Test
    public void testDataActivityTimeoutRevertsToStandardInterval() {
        heartbeat.start();
        heartbeat.notifyDataActivity();
        assertTrue(heartbeat.isDataActive());

        // Advance time past 15000ms timeout
        testTimeProvider.currentTime += 15001L;

        assertFalse(heartbeat.isDataActive());
        assertEquals(BleHeartbeat.STANDARD_INTERVAL_MS, heartbeat.getInterval());
    }
}
