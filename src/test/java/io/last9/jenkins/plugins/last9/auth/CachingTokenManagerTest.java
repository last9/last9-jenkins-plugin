package io.last9.jenkins.plugins.last9.auth;

import io.last9.jenkins.plugins.last9.api.ApiException;
import io.last9.jenkins.plugins.last9.api.Last9ApiClient;
import io.last9.jenkins.plugins.last9.model.ChangeEventPayload;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class CachingTokenManagerTest {

    /**
     * Stub API client that returns a controllable token response.
     */
    private static class StubApiClient implements Last9ApiClient {
        private final AtomicInteger exchangeCount = new AtomicInteger(0);
        private String tokenResponse = "{\"access_token\":\"test-token\",\"expires_in\":3600}";

        @Override
        public void sendChangeEvent(String orgSlug, String accessToken, ChangeEventPayload payload) {
        }

        @Override
        public String exchangeToken(String refreshToken) {
            exchangeCount.incrementAndGet();
            return tokenResponse;
        }

        void setTokenResponse(String response) {
            this.tokenResponse = response;
        }

        int getExchangeCount() {
            return exchangeCount.get();
        }
    }

    @Test
    public void exchangesTokenOnFirstCall() throws Exception {
        var stub = new StubApiClient();
        var manager = new CachingTokenManager(stub);

        String token = manager.getAccessToken("refresh-123");
        assertEquals("test-token", token);
        assertEquals(1, stub.getExchangeCount());
    }

    @Test
    public void cachesTokenOnSubsequentCalls() throws Exception {
        var stub = new StubApiClient();
        var manager = new CachingTokenManager(stub);

        manager.getAccessToken("refresh-123");
        manager.getAccessToken("refresh-123");
        manager.getAccessToken("refresh-123");

        assertEquals(1, stub.getExchangeCount());
    }

    @Test
    public void differentRefreshTokensGetSeparateCacheEntries() throws Exception {
        var stub = new StubApiClient();
        var manager = new CachingTokenManager(stub);

        manager.getAccessToken("refresh-aaa");
        manager.getAccessToken("refresh-bbb");

        assertEquals(2, stub.getExchangeCount());
    }

    @Test
    public void invalidateClearsCacheAndForcesReExchange() throws Exception {
        var stub = new StubApiClient();
        var manager = new CachingTokenManager(stub);

        manager.getAccessToken("refresh-123");
        assertEquals(1, stub.getExchangeCount());

        manager.invalidate();

        manager.getAccessToken("refresh-123");
        assertEquals(2, stub.getExchangeCount());
    }

    @Test
    public void throwsOnEmptyAccessToken() {
        var stub = new StubApiClient();
        stub.setTokenResponse("{\"access_token\":\"\",\"expires_in\":3600}");
        var manager = new CachingTokenManager(stub);

        try {
            manager.getAccessToken("refresh-123");
            fail("Should have thrown ApiException");
        } catch (Exception e) {
            assertTrue(e instanceof ApiException);
            assertTrue(e.getMessage().contains("empty access_token"));
        }
    }

    @Test
    public void throwsOnMissingAccessToken() {
        var stub = new StubApiClient();
        stub.setTokenResponse("{\"error\":\"invalid_grant\"}");
        var manager = new CachingTokenManager(stub);

        try {
            manager.getAccessToken("refresh-123");
            fail("Should have thrown ApiException");
        } catch (Exception e) {
            assertTrue(e instanceof ApiException);
            assertTrue(e.getMessage().contains("empty access_token"));
        }
    }

    @Test
    public void usesDefaultLifetimeWhenExpiresInMissing() throws Exception {
        var stub = new StubApiClient();
        stub.setTokenResponse("{\"access_token\":\"tok\"}");
        var manager = new CachingTokenManager(stub);

        // Should not throw — uses default 1h lifetime
        String token = manager.getAccessToken("refresh-123");
        assertEquals("tok", token);
    }

    @Test
    public void usesDefaultLifetimeWhenExpiresInNotParseable() throws Exception {
        var stub = new StubApiClient();
        stub.setTokenResponse("{\"access_token\":\"tok\",\"expires_in\":\"not-a-number\"}");
        var manager = new CachingTokenManager(stub);

        // Should not throw — falls back to default lifetime
        String token = manager.getAccessToken("refresh-123");
        assertEquals("tok", token);
    }
}
