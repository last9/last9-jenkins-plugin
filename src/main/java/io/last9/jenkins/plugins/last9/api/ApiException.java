package io.last9.jenkins.plugins.last9.api;

/**
 * Exception for Last9 API errors.
 */
public class ApiException extends Exception {

    private final int statusCode;
    private final String responseBody;

    public ApiException(String message) {
        super(message);
        this.statusCode = -1;
        this.responseBody = null;
    }

    public ApiException(int statusCode, String responseBody) {
        super("Last9 API error (HTTP " + statusCode + "): " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = null;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public boolean isRetryable() {
        return statusCode == 429 || statusCode >= 500;
    }
}
