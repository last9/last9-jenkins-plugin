package io.last9.jenkins.plugins.last9.util;

/**
 * Fully resolved Last9 connection target for a single deployment marker.
 */
public record ResolvedConnection(String orgSlug, String credentialId, String apiBaseUrl) {
}
