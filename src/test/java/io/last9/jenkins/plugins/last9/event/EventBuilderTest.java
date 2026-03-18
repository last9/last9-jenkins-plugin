package io.last9.jenkins.plugins.last9.event;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.last9.jenkins.plugins.last9.collect.AttributeCollector;
import io.last9.jenkins.plugins.last9.model.ChangeEvent;
import io.last9.jenkins.plugins.last9.model.EventState;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EventBuilderTest {

    @Test
    public void buildsEventWithCollectedAttributes() {
        AttributeCollector collector = (run, listener) -> Map.of("jenkins.job_name", "my-job");

        var builder = new EventBuilder(List.of(collector));
        var run = mock(Run.class);
        var listener = mock(TaskListener.class);

        ChangeEvent event = builder.build(
            run, listener, "deployment", EventState.STOP, "ds-1",
            "my-svc", "prod", null);

        assertEquals("deployment", event.eventName());
        assertEquals(EventState.STOP, event.eventState());
        assertEquals("ds-1", event.dataSourceName());
        assertEquals("my-svc", event.attributes().get("service_name"));
        assertEquals("prod", event.attributes().get("deployment_environment"));
        assertEquals("my-job", event.attributes().get("jenkins.job_name"));
        assertNotNull(event.timestamp());
    }

    @Test
    public void explicitParamsOverrideCollected() {
        // Collector sets service_name, but explicit param should win
        AttributeCollector collector = (run, listener) -> {
            var map = new LinkedHashMap<String, String>();
            map.put("service_name", "from-collector");
            return map;
        };

        var builder = new EventBuilder(List.of(collector));
        ChangeEvent event = builder.build(
            mock(Run.class), mock(TaskListener.class),
            "deploy", EventState.START, null,
            "explicit-svc", null, null);

        assertEquals("explicit-svc", event.attributes().get("service_name"));
    }

    @Test
    public void customAttributesOverrideAll() {
        AttributeCollector collector = (run, listener) -> Map.of("key", "from-collector");

        var builder = new EventBuilder(List.of(collector));
        Map<String, String> custom = Map.of("key", "from-custom", "extra", "val");

        ChangeEvent event = builder.build(
            mock(Run.class), mock(TaskListener.class),
            "deploy", EventState.STOP, null,
            "svc", "prod", custom);

        assertEquals("from-custom", event.attributes().get("key"));
        assertEquals("val", event.attributes().get("extra"));
    }

    @Test
    public void defaultsEventNameToDeployment() {
        var builder = new EventBuilder(Collections.emptyList());
        ChangeEvent event = builder.build(
            mock(Run.class), mock(TaskListener.class),
            null, EventState.STOP, null, "svc", null, null);

        assertEquals("deployment", event.eventName());
    }

    @Test
    public void handlesNullServiceNameAndEnvironment() {
        var builder = new EventBuilder(Collections.emptyList());
        ChangeEvent event = builder.build(
            mock(Run.class), mock(TaskListener.class),
            "deploy", EventState.STOP, null, null, null, null);

        assertFalse(event.attributes().containsKey("service_name"));
        assertFalse(event.attributes().containsKey("deployment_environment"));
    }

    @Test
    public void handlesBlankServiceNameAndEnvironment() {
        var builder = new EventBuilder(Collections.emptyList());
        ChangeEvent event = builder.build(
            mock(Run.class), mock(TaskListener.class),
            "deploy", EventState.STOP, null, "  ", "  ", null);

        assertFalse(event.attributes().containsKey("service_name"));
        assertFalse(event.attributes().containsKey("deployment_environment"));
    }
}
