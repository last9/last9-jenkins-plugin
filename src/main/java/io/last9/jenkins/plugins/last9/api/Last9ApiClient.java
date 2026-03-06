package io.last9.jenkins.plugins.last9.api;

import io.last9.jenkins.plugins.last9.model.ChangeEventPayload;

/**
 * Interface for Last9 API communication. Testable via mocking.
 */
public interface Last9ApiClient {

    /**
     * Send a change event to the Last9 API.
     * PUT /api/v4/organizations/{orgSlug}/change_events
     */
    void sendChangeEvent(String orgSlug, String accessToken, ChangeEventPayload payload)
        throws ApiException;

    /**
     * Exchange a refresh token for an access token.
     * POST /api/v4/oauth/access_token
     *
     * @return the access token response as JSON string
     */
    String exchangeToken(String refreshToken) throws ApiException;
}
