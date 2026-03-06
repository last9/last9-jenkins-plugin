package io.last9.jenkins.plugins.last9.api;

import io.last9.jenkins.plugins.last9.model.ChangeEventPayload;
import io.last9.jenkins.plugins.last9.util.JsonSerializer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP implementation of the Last9 API client using java.net.http.
 * Zero external HTTP dependencies.
 */
public class Last9HttpApiClient implements Last9ApiClient {

    private static final Logger LOGGER = Logger.getLogger(Last9HttpApiClient.class.getName());
    private static final String API_VERSION = "v4";

    private final String baseUrl;
    private final HttpClient httpClient;
    private final RetryPolicy retryPolicy;

    public Last9HttpApiClient(String baseUrl) {
        this(baseUrl,
             HttpClient.newBuilder()
                 .connectTimeout(Duration.ofSeconds(10))
                 .build(),
             RetryPolicy.defaultPolicy());
    }

    public Last9HttpApiClient(String baseUrl, HttpClient httpClient, RetryPolicy retryPolicy) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = httpClient;
        this.retryPolicy = retryPolicy;
    }

    @Override
    public void sendChangeEvent(String orgSlug, String accessToken, ChangeEventPayload payload)
            throws ApiException, InterruptedException {
        String url = baseUrl + "/api/" + API_VERSION + "/organizations/" + orgSlug + "/change_events";
        String body = JsonSerializer.serialize(payload);

        LOGGER.log(Level.FINE, "Sending change event to {0}", url);

        retryPolicy.execute(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .header("X-LAST9-API-TOKEN", "Bearer " + accessToken)
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new ApiException(response.statusCode(), response.body());
            }
            return response;
        }, "Send change event");
    }

    @Override
    public String exchangeToken(String refreshToken) throws ApiException, InterruptedException {
        String url = baseUrl + "/api/" + API_VERSION + "/oauth/access_token";
        String body = JsonSerializer.serializeTokenRequest(refreshToken);

        LOGGER.log(Level.FINE, "Exchanging refresh token at {0}", url);

        return retryPolicy.execute(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new ApiException(response.statusCode(), response.body());
            }
            return response.body();
        }, "Token exchange");
    }
}
