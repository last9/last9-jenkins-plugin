package io.last9.jenkins.plugins.last9.auth;

import io.last9.jenkins.plugins.last9.api.ApiException;
import io.last9.jenkins.plugins.last9.api.Last9ApiClient;
import io.last9.jenkins.plugins.last9.util.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe token manager with caching.
 * Caches access tokens keyed by a hash of the refresh token.
 * Refreshes 5 minutes before expiry to avoid edge-case failures.
 */
public class CachingTokenManager implements TokenManager {

    private static final Logger LOGGER = Logger.getLogger(CachingTokenManager.class.getName());
    private static final Duration EXPIRY_BUFFER = Duration.ofMinutes(5);
    private static final Duration DEFAULT_TOKEN_LIFETIME = Duration.ofHours(1);

    private final Last9ApiClient apiClient;
    private final ConcurrentHashMap<String, AccessToken> cache = new ConcurrentHashMap<>();

    public CachingTokenManager(Last9ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public String getAccessToken(String refreshToken) throws ApiException {
        String cacheKey = hashToken(refreshToken);

        AccessToken cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpiringSoon(EXPIRY_BUFFER)) {
            LOGGER.log(Level.FINE, "Using cached access token");
            return cached.token();
        }

        synchronized (this) {
            // Double-check after acquiring lock
            cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpiringSoon(EXPIRY_BUFFER)) {
                return cached.token();
            }

            LOGGER.log(Level.INFO, "Exchanging refresh token for new access token");
            String responseJson = apiClient.exchangeToken(refreshToken);

            String accessToken = JsonSerializer.extractField(responseJson, "access_token");
            if (accessToken == null || accessToken.isBlank()) {
                throw new ApiException("Token exchange returned empty access_token");
            }

            String expiresInStr = JsonSerializer.extractField(responseJson, "expires_in");
            Duration lifetime = DEFAULT_TOKEN_LIFETIME;
            if (expiresInStr != null) {
                try {
                    lifetime = Duration.ofSeconds(Long.parseLong(expiresInStr));
                } catch (NumberFormatException e) {
                    LOGGER.log(Level.WARNING, "Could not parse expires_in: {0}, using default", expiresInStr);
                }
            }

            AccessToken newToken = new AccessToken(accessToken, Instant.now().plus(lifetime));
            cache.put(cacheKey, newToken);
            LOGGER.log(Level.INFO, "Access token cached, expires in {0}s", lifetime.toSeconds());
            return accessToken;
        }
    }

    @Override
    public void invalidate() {
        cache.clear();
        LOGGER.log(Level.INFO, "Token cache invalidated");
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java
            throw new RuntimeException(e);
        }
    }
}
