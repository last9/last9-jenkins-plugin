package io.last9.jenkins.plugins.last9.api;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Exponential backoff retry policy for API calls.
 */
public class RetryPolicy {

    private static final Logger LOGGER = Logger.getLogger(RetryPolicy.class.getName());

    private final int maxRetries;
    private final long initialBackoffMs;
    private final double multiplier;

    public RetryPolicy(int maxRetries, long initialBackoffMs, double multiplier) {
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
        this.multiplier = multiplier;
    }

    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(3, 500, 2.0);
    }

    public <T> T execute(RetryableOperation<T> operation, String operationName)
            throws ApiException, InterruptedException {
        long backoff = initialBackoffMs;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return operation.execute();
            } catch (ApiException e) {
                if (!e.isRetryable()) {
                    LOGGER.log(Level.SEVERE, "{0}: non-retryable failure (HTTP {1})",
                        new Object[]{operationName, e.getStatusCode()});
                    throw e;
                }
                if (attempt == maxRetries) {
                    LOGGER.log(Level.SEVERE, "{0}: exhausted {1} retries (last HTTP {2})",
                        new Object[]{operationName, maxRetries, e.getStatusCode()});
                    throw new ApiException("Exhausted all " + maxRetries + " retries for " + operationName, e.getStatusCode(), e);
                }
                long jitter = ThreadLocalRandom.current().nextLong(0, backoff / 4 + 1);
                long sleepMs = backoff + jitter;
                LOGGER.log(Level.WARNING,
                    "{0}: attempt {1}/{2} failed (HTTP {3}), retrying in {4}ms",
                    new Object[]{operationName, attempt, maxRetries, e.getStatusCode(), sleepMs});
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ie;
                }
                backoff = (long) (backoff * multiplier);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                throw new ApiException("Unexpected error during " + operationName, e);
            }
        }
        throw new ApiException("Exhausted all " + maxRetries + " retries for " + operationName);
    }

    @FunctionalInterface
    interface RetryableOperation<T> {
        T execute() throws Exception;
    }
}
