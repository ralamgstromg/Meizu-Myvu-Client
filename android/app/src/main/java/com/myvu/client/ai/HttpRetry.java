package com.myvu.client.ai;

import com.myvu.client.core.LogBus;

import java.io.IOException;
import java.util.Random;

/**
 * Retries transient HTTP failures with exponential backoff and jitter
 * while preserving non-retryable 4xx client errors and supporting cancellation.
 */
public final class HttpRetry {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    public static final long DEFAULT_BASE_DELAY_MS = 300L;
    public static final long MAX_DELAY_MS = 5000L;

    private static final Random RANDOM = new Random();

    private HttpRetry() {}

    public interface Request<T> {
        T execute() throws IOException;
    }

    public static <T> T execute(String service, Request<T> request) throws IOException {
        return execute(service, DEFAULT_MAX_ATTEMPTS, DEFAULT_BASE_DELAY_MS, request);
    }

    public static <T> T execute(String service, int maxAttempts, long baseDelayMs, Request<T> request)
            throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("interrupted while retrying " + service, new InterruptedException());
            }
            try {
                return request.execute();
            } catch (NonRetryableHttpException e) {
                throw e;
            } catch (IOException e) {
                lastError = e;
                if (attempt == maxAttempts) break;
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("interrupted while retrying " + service, new InterruptedException());
                }

                long delayMs = calculateDelayWithJitter(attempt, baseDelayMs, RANDOM);
                LogBus.warn("HTTP retry: service=" + service + " attempt=" + attempt + "/" + maxAttempts
                        + " delayMs=" + delayMs + " error=" + e.getMessage());
                waitBeforeRetry(service, delayMs);
            }
        }
        throw lastError;
    }

    public static long calculateBackoffDelay(int attempt, long baseDelayMs) {
        if (attempt <= 0) return 0;
        long backoff = (long) (baseDelayMs * Math.pow(2, attempt - 1));
        return Math.min(backoff, MAX_DELAY_MS);
    }

    public static long calculateDelayWithJitter(int attempt, long baseDelayMs, Random random) {
        long backoff = calculateBackoffDelay(attempt, baseDelayMs);
        if (backoff <= 0) return 0;
        long half = backoff / 2;
        long jitterRange = backoff - half + 1;
        long jitter = (long) (random.nextDouble() * jitterRange);
        return half + jitter;
    }

    public static IOException statusError(int status, String message) {
        if (status >= 400 && status < 500) {
            return new NonRetryableHttpException(message);
        }
        return new IOException(message);
    }

    private static void waitBeforeRetry(String service, long delayMs) throws IOException {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while retrying " + service, e);
        }
    }

    public static final class NonRetryableHttpException extends IOException {
        public NonRetryableHttpException(String message) {
            super(message);
        }
    }
}
