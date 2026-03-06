package io.last9.jenkins.plugins.last9.util;

import io.last9.jenkins.plugins.last9.model.ChangeEventPayload;
import java.util.Map;

/**
 * Minimal JSON serializer. No external dependencies.
 * Handles only the shapes needed for Last9 API payloads.
 */
public final class JsonSerializer {

    private JsonSerializer() {}

    public static String serialize(ChangeEventPayload payload) {
        var sb = new StringBuilder();
        sb.append('{');
        appendField(sb, "event_name", payload.event_name());
        sb.append(',');
        appendField(sb, "event_state", payload.event_state());
        sb.append(',');
        appendField(sb, "timestamp", payload.timestamp());
        if (payload.data_source_name() != null && !payload.data_source_name().isBlank()) {
            sb.append(',');
            appendField(sb, "data_source_name", payload.data_source_name());
        }
        if (payload.attributes() != null && !payload.attributes().isEmpty()) {
            sb.append(',');
            sb.append("\"attributes\":");
            sb.append(serializeMap(payload.attributes()));
        }
        sb.append('}');
        return sb.toString();
    }

    public static String serializeTokenRequest(String refreshToken) {
        var sb = new StringBuilder();
        sb.append('{');
        appendField(sb, "refresh_token", refreshToken);
        sb.append('}');
        return sb.toString();
    }

    public static String extractField(String json, String field) {
        String key = "\"" + field + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) return null;

        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) return null;

        int start = -1;
        boolean isString = false;
        for (int i = colonIdx + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') continue;
            if (c == '"') {
                isString = true;
                start = i + 1;
            } else {
                start = i;
            }
            break;
        }
        if (start < 0) return null;

        if (isString) {
            int end = json.indexOf('"', start);
            return end > start ? json.substring(start, end) : null;
        } else {
            int end = start;
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == ',' || c == '}' || c == ' ' || c == '\n') break;
                end++;
            }
            return json.substring(start, end);
        }
    }

    private static String serializeMap(Map<String, String> map) {
        var sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            appendField(sb, entry.getKey(), entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String key, String value) {
        sb.append('"').append(escapeJson(key)).append("\":\"").append(escapeJson(value != null ? value : "")).append('"');
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
