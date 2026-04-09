package io.last9.jenkins.plugins.last9.auth;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable value object for a cached access token.
 * The token field is an in-memory cache entry only and is never serialized to disk.
 */
@SuppressWarnings("lgtm[jenkins/plaintext-password-storage]")
public record AccessToken(String token, Instant expiresAt) {

    public boolean isExpiringSoon(Duration buffer) {
        return Instant.now().plus(buffer).isAfter(expiresAt);
    }
}
