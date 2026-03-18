package io.last9.jenkins.plugins.last9.model;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class ChangeEventPayloadTest {

    @Test
    public void fromConvertsCorrectly() {
        var event = new ChangeEvent(
            "deployment", EventState.START, "2024-01-01T00:00:00Z",
            "my-ds", Map.of("key", "val"));

        ChangeEventPayload payload = ChangeEventPayload.from(event);

        assertEquals("deployment", payload.event_name());
        assertEquals("start", payload.event_state());
        assertEquals("2024-01-01T00:00:00Z", payload.timestamp());
        assertEquals("my-ds", payload.data_source_name());
        assertEquals(Map.of("key", "val"), payload.attributes());
    }

    @Test
    public void fromHandlesNullDataSource() {
        var event = new ChangeEvent(
            "deploy", EventState.STOP, "2024-01-01T00:00:00Z",
            null, Map.of());

        ChangeEventPayload payload = ChangeEventPayload.from(event);
        assertNull(payload.data_source_name());
    }
}
