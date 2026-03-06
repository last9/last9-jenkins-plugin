package io.last9.jenkins.plugins.last9.model;

import java.util.Map;

/**
 * Wire-format DTO matching the Last9 Change Events API contract.
 */
public record ChangeEventPayload(
    String event_name,
    String event_state,
    String timestamp,
    String data_source_name,
    Map<String, String> attributes
) {
    public static ChangeEventPayload from(ChangeEvent event) {
        return new ChangeEventPayload(
            event.eventName(),
            event.eventState().getValue(),
            event.timestamp(),
            event.dataSourceName(),
            event.attributes()
        );
    }
}
