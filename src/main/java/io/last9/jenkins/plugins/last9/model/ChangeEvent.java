package io.last9.jenkins.plugins.last9.model;

import java.util.Map;

/**
 * Immutable domain object representing a deployment change event.
 */
public record ChangeEvent(
    String eventName,
    EventState eventState,
    String timestamp,
    String dataSourceName,
    Map<String, String> attributes
) {}
