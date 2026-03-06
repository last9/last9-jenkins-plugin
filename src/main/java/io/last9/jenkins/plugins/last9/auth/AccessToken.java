package io.last9.jenkins.plugins.last9.auth;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable value object for a cached access token.
 */
public record AccessToken(String token, Instant expiresAt) {

    public boolean isExpiringSoon(Duration buffer) {
        return Instant.now().plus(buffer).isAfter(expiresAt);
    }
}
