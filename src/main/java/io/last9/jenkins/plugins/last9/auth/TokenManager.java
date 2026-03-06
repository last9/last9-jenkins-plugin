package io.last9.jenkins.plugins.last9.auth;

import io.last9.jenkins.plugins.last9.api.ApiException;

/**
 * Manages OAuth token lifecycle (exchange + caching).
 */
public interface TokenManager {

    /**
     * Get a valid access token, refreshing from the API if the cached one is expired.
     *
     * @param refreshToken the refresh token from Jenkins credentials store
     * @return a valid access token string
     */
    String getAccessToken(String refreshToken) throws ApiException, InterruptedException;

    /**
     * Force-invalidate all cached tokens.
     */
    void invalidate();
}
