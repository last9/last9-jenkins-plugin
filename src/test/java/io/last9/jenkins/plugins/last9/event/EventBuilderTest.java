package io.last9.jenkins.plugins.last9.event;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.last9.jenkins.plugins.last9.collect.AttributeCollector;
import io.last9.jenkins.plugins.last9.deployment.DeploymentWindowAction;
import io.last9.jenkins.plugins.last9.deployment.DeploymentWindowTracker;
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
        AttributeCollector collector = (run, listener) -> Map.of("jenkins_job_name", "my-job");

        var builder = new EventBuilder(List.of(collector));
        var run = mock(Run.class);
        var listener = mock(TaskListener.class);

        ChangeEvent event = builder.build(
            run, listener, "deployment", EventState.STOP, "ds-1",
            "my-svc", "prod", null);

        assertEquals("deployment", event.eventName());
        assertEquals(EventState.STOP, event.eventState());
        assertEquals("ds-1", event.dataSourceName());
        assertEquals("my-svc", event.attributes().get("service"));
        assertEquals("prod", event.attributes().get("deployment_environment"));
        assertEquals("my-job", event.attributes().get("jenkins_job_name"));
        assertNotNull(event.timestamp());
    }

    @Test
    public void explicitParamsOverrideCollected() {
        // Collector sets service, but explicit param should win
        AttributeCollector collector = (run, listener) -> {
            var map = new LinkedHashMap<String, String>();
            map.put("service", "from-collector");
            return map;
        };

        var builder = new EventBuilder(List.of(collector));
        ChangeEvent event = builder.build(
            mock(Run.class), mock(TaskListener.class),
            "deploy", EventState.START, null,
            "explicit-svc", null, null);

        assertEquals("explicit-svc", event.attributes().get("service"));
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

        assertFalse(event.attributes().containsKey("service"));
        assertFalse(event.attributes().containsKey("deployment_environment"));
    }

    @Test
    public void handlesBlankServiceNameAndEnvironment() {
        var builder = new EventBuilder(Collections.emptyList());
        ChangeEvent event = builder.build(
            mock(Run.class), mock(TaskListener.class),
            "deploy", EventState.STOP, null, "  ", "  ", null);

        assertFalse(event.attributes().containsKey("service"));
        assertFalse(event.attributes().containsKey("deployment_environment"));
    }

    @Test
    public void startEventAttachesDeploymentIdViaTracker() {
        DeploymentWindowTracker tracker = mock(DeploymentWindowTracker.class);
        when(tracker.start(any())).thenReturn("uuid-123");

        var builder = new EventBuilder(Collections.emptyList(), tracker);
        Run<?, ?> run = mock(Run.class);

        ChangeEvent event = builder.build(
            run, mock(TaskListener.class),
            "deploy", EventState.START, null, "svc", null, null);

        assertEquals("uuid-123", event.attributes().get("deployment_id"));
        verify(tracker).start(run);
    }

    @Test
    public void stopEventIncludesDeploymentIdAndDuration() {
        DeploymentWindowTracker tracker = mock(DeploymentWindowTracker.class);
        when(tracker.getDeploymentId(any())).thenReturn("uuid-456");
        when(tracker.stop(any())).thenReturn(1500L);

        var builder = new EventBuilder(Collections.emptyList(), tracker);
        Run<?, ?> run = mock(Run.class);

        ChangeEvent event = builder.build(
            run, mock(TaskListener.class),
            "deploy", EventState.STOP, null, "svc", null, null);

        assertEquals("uuid-456", event.attributes().get("deployment_id"));
        assertEquals("1500", event.attributes().get("deployment_window_duration_ms"));
    }

    @Test
    public void customAttributesCannotOverrideDeploymentWindowFields() {
        DeploymentWindowTracker tracker = mock(DeploymentWindowTracker.class);
        when(tracker.start(any())).thenReturn("real-deployment-id");

        var builder = new EventBuilder(Collections.emptyList(), tracker);
        Map<String, String> custom = Map.of(
            "deployment_id", "attacker-id",
            "deployment_window_duration_ms", "0"
        );

        ChangeEvent event = builder.build(
            mock(Run.class), mock(TaskListener.class),
            "deploy", EventState.START, null, "svc", null, custom);

        assertEquals("real-deployment-id", event.attributes().get("deployment_id"));
        assertFalse(event.attributes().containsKey("deployment_window_duration_ms"));
    }

    @Test
    public void stopEventOmitsDurationWhenTrackerReturnsNegativeOne() {
        DeploymentWindowTracker tracker = mock(DeploymentWindowTracker.class);
        when(tracker.getDeploymentId(any())).thenReturn(null);
        when(tracker.stop(any())).thenReturn(-1L);

        var builder = new EventBuilder(Collections.emptyList(), tracker);

        ChangeEvent event = builder.build(
            mock(Run.class), mock(TaskListener.class),
            "deploy", EventState.STOP, null, "svc", null, null);

        assertFalse(event.attributes().containsKey("deployment_window_duration_ms"));
        assertFalse(event.attributes().containsKey("deployment_id"));
    }
}
