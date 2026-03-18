package io.last9.jenkins.plugins.last9.api;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class RetryPolicyTest {

    @Test
    public void successOnFirstAttempt() throws Exception {
        var policy = new RetryPolicy(3, 10, 2.0);
        String result = policy.execute(() -> "ok", "test-op");
        assertEquals("ok", result);
    }

    @Test
    public void retriesOnRetryableError() throws Exception {
        var attempts = new AtomicInteger(0);
        var policy = new RetryPolicy(3, 10, 2.0);

        String result = policy.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ApiException(500, "server error");
            }
            return "recovered";
        }, "test-op");

        assertEquals("recovered", result);
        assertEquals(3, attempts.get());
    }

    @Test
    public void doesNotRetryNonRetryable() {
        var attempts = new AtomicInteger(0);
        var policy = new RetryPolicy(3, 10, 2.0);

        try {
            policy.execute(() -> {
                attempts.incrementAndGet();
                throw new ApiException(400, "bad request");
            }, "test-op");
            fail("Should have thrown");
        } catch (Exception e) {
            assertInstanceOf(ApiException.class, e);
            assertEquals(400, ((ApiException) e).getStatusCode());
        }
        assertEquals(1, attempts.get());
    }

    @Test
    public void throwsAfterMaxRetries() {
        var attempts = new AtomicInteger(0);
        var policy = new RetryPolicy(2, 10, 2.0);

        try {
            policy.execute(() -> {
                attempts.incrementAndGet();
                throw new ApiException(503, "unavailable");
            }, "test-op");
            fail("Should have thrown");
        } catch (Exception e) {
            assertInstanceOf(ApiException.class, e);
            assertEquals(503, ((ApiException) e).getStatusCode());
        }
        assertEquals(2, attempts.get());
    }

    @Test
    public void propagatesInterruptedException() {
        var policy = new RetryPolicy(3, 10, 2.0);

        Thread.currentThread().interrupt();
        try {
            policy.execute(() -> {
                throw new ApiException(500, "error");
            }, "test-op");
            fail("Should have thrown");
        } catch (InterruptedException e) {
            // Expected — interrupt flag should be set
            assertTrue(Thread.currentThread().isInterrupted());
        } catch (ApiException e) {
            fail("Should have thrown InterruptedException, not ApiException");
        } finally {
            // Clear interrupt flag for test runner
            Thread.interrupted();
        }
    }

    @Test
    public void wrapsUnexpectedExceptions() {
        var policy = new RetryPolicy(3, 10, 2.0);

        try {
            policy.execute(() -> {
                throw new RuntimeException("boom");
            }, "test-op");
            fail("Should have thrown");
        } catch (Exception e) {
            assertInstanceOf(ApiException.class, e);
            assertTrue(e.getMessage().contains("Unexpected error"));
            assertTrue(e.getMessage().contains("test-op"));
        }
    }

    @Test
    public void isRetryableForServerErrors() {
        assertTrue(new ApiException(500, "").isRetryable());
        assertTrue(new ApiException(502, "").isRetryable());
        assertTrue(new ApiException(503, "").isRetryable());
        assertTrue(new ApiException(429, "").isRetryable());
        assertFalse(new ApiException(400, "").isRetryable());
        assertFalse(new ApiException(401, "").isRetryable());
        assertFalse(new ApiException(404, "").isRetryable());
    }
}
