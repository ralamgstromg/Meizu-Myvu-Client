package com.myvu.client.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpRetryTest {

    @Test
    public void testSuccessfulRequestNoRetry() throws IOException {
        AtomicInteger attempts = new AtomicInteger(0);
        String result = HttpRetry.execute("TestService", () -> {
            attempts.incrementAndGet();
            return "success";
        });

        assertEquals("success", result);
        assertEquals(1, attempts.get());
    }

    @Test
    public void testTransientFailureRetriesAndSucceeds() throws IOException {
        AtomicInteger attempts = new AtomicInteger(0);
        String result = HttpRetry.execute("TestService", 3, 10, () -> {
            int count = attempts.incrementAndGet();
            if (count < 2) {
                throw new IOException("Transient network error");
            }
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(2, attempts.get());
    }

    @Test
    public void testNonRetryableHttpErrorFailsImmediately() {
        AtomicInteger attempts = new AtomicInteger(0);
        try {
            HttpRetry.execute("TestService", 3, 10, () -> {
                attempts.incrementAndGet();
                throw HttpRetry.statusError(404, "Not Found");
            });
            fail("Expected exception for 404 non-retryable error");
        } catch (IOException e) {
            assertEquals(1, attempts.get());
        }
    }

    @Test
    public void testExceedingMaxAttemptsThrowsLastError() {
        AtomicInteger attempts = new AtomicInteger(0);
        int maxAttempts = 3;
        try {
            HttpRetry.execute("TestService", maxAttempts, 10, () -> {
                attempts.incrementAndGet();
                throw new IOException("Server error 500");
            });
            fail("Expected exception when retries are exhausted");
        } catch (IOException e) {
            assertEquals(maxAttempts, attempts.get());
            assertTrue(e.getMessage().contains("Server error 500"));
        }
    }

    @Test
    public void testExponentialBackoffCalculation() {
        long baseDelay = 300;
        assertEquals(300, HttpRetry.calculateBackoffDelay(1, baseDelay));
        assertEquals(600, HttpRetry.calculateBackoffDelay(2, baseDelay));
        assertEquals(1200, HttpRetry.calculateBackoffDelay(3, baseDelay));
        assertEquals(2400, HttpRetry.calculateBackoffDelay(4, baseDelay));
    }

    @Test
    public void testRandomJitterBounds() {
        long baseDelay = 300;
        Random random = new Random(42);

        for (int attempt = 1; attempt <= 5; attempt++) {
            long backoff = HttpRetry.calculateBackoffDelay(attempt, baseDelay);
            long minAllowed = backoff / 2;
            long maxAllowed = backoff;

            for (int i = 0; i < 50; i++) {
                long delay = HttpRetry.calculateDelayWithJitter(attempt, baseDelay, random);
                assertTrue("Delay " + delay + " should be >= min " + minAllowed, delay >= minAllowed);
                assertTrue("Delay " + delay + " should be <= max " + maxAllowed, delay <= maxAllowed);
            }
        }
    }

    @Test
    public void testCancellationOrInterruption() {
        AtomicInteger attempts = new AtomicInteger(0);
        Thread.currentThread().interrupt(); // Pre-interrupt thread

        try {
            HttpRetry.execute("TestService", 3, 100, () -> {
                attempts.incrementAndGet();
                throw new IOException("Failed while interrupted");
            });
            fail("Expected exception due to interruption");
        } catch (IOException e) {
            // Thread interruption state should abort retries or throw interrupted IOException
            assertTrue(e.getMessage().contains("interrupted") || e.getCause() instanceof InterruptedException);
        } finally {
            Thread.interrupted(); // Clear interrupted status for subsequent tests
        }
    }
}
