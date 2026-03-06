package io.last9.jenkins.plugins.last9.util;

import io.last9.jenkins.plugins.last9.model.ChangeEventPayload;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class JsonSerializerTest {

    @Test
    public void serializeMinimalPayload() {
        var payload = new ChangeEventPayload(
            "deployment", "stop", "2024-01-01T00:00:00Z", null, null);
        String json = JsonSerializer.serialize(payload);

        assertTrue(json.contains("\"event_name\":\"deployment\""));
        assertTrue(json.contains("\"event_state\":\"stop\""));
        assertTrue(json.contains("\"timestamp\":\"2024-01-01T00:00:00Z\""));
        assertFalse(json.contains("data_source_name"));
        assertFalse(json.contains("attributes"));
    }

    @Test
    public void serializeFullPayload() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("service", "my-svc");
        attrs.put("env", "prod");

        var payload = new ChangeEventPayload(
            "deploy", "start", "2024-01-01T00:00:00Z", "ds-1", attrs);
        String json = JsonSerializer.serialize(payload);

        assertTrue(json.contains("\"data_source_name\":\"ds-1\""));
        assertTrue(json.contains("\"service\":\"my-svc\""));
        assertTrue(json.contains("\"env\":\"prod\""));
    }

    @Test
    public void serializeTokenRequest() {
        String json = JsonSerializer.serializeTokenRequest("my-refresh-token");
        assertEquals("{\"refresh_token\":\"my-refresh-token\"}", json);
    }

    @Test
    public void escapeJsonSpecialChars() {
        String json = JsonSerializer.serializeTokenRequest("tok\\en\"with\nnewline\ttab");
        assertTrue(json.contains("tok\\\\en\\\"with\\nnewline\\ttab"));
    }

    @Test
    public void escapeJsonControlChars() {
        // NUL, SOH, US control chars must be unicode-escaped in JSON
        String input = "before" + '\0' + "middle" + (char) 1 + "end" + (char) 0x1F;
        String json = JsonSerializer.serializeTokenRequest(input);
        assertTrue(json.contains("\\u0000"));
        assertTrue(json.contains("\\u0001"));
        assertTrue(json.contains("\\u001f"));
    }

    @Test
    public void extractFieldSimple() {
        String json = "{\"access_token\":\"abc123\",\"expires_in\":3600}";
        assertEquals("abc123", JsonSerializer.extractField(json, "access_token"));
        assertEquals("3600", JsonSerializer.extractField(json, "expires_in"));
    }

    @Test
    public void extractFieldWithEscapedQuotes() {
        String json = "{\"access_token\":\"tok\\\"en\",\"other\":\"val\"}";
        assertEquals("tok\\\"en", JsonSerializer.extractField(json, "access_token"));
    }

    @Test
    public void extractFieldMissing() {
        String json = "{\"foo\":\"bar\"}";
        assertNull(JsonSerializer.extractField(json, "missing"));
    }

    @Test
    public void extractFieldWithSpaces() {
        String json = "{ \"access_token\" : \"spaced\" }";
        assertEquals("spaced", JsonSerializer.extractField(json, "access_token"));
    }

    @Test
    public void serializeNullValueInAttributes() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("key", null);

        var payload = new ChangeEventPayload(
            "deployment", "stop", "2024-01-01T00:00:00Z", null, attrs);
        String json = JsonSerializer.serialize(payload);
        assertTrue(json.contains("\"key\":\"\""));
    }
}
