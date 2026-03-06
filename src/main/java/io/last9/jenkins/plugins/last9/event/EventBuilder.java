package io.last9.jenkins.plugins.last9.event;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.last9.jenkins.plugins.last9.collect.AttributeCollector;
import io.last9.jenkins.plugins.last9.collect.JenkinsContextCollector;
import io.last9.jenkins.plugins.last9.collect.ScmContextCollector;
import io.last9.jenkins.plugins.last9.model.ChangeEvent;
import io.last9.jenkins.plugins.last9.model.EventState;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a ChangeEvent by composing attributes from multiple collectors
 * and merging with user-supplied values.
 */
public class EventBuilder {

    public static final String DEFAULT_EVENT_NAME = "deployment";

    private static final DateTimeFormatter ISO_FORMAT =
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final List<AttributeCollector> collectors;

    public EventBuilder() {
        this(List.of(new JenkinsContextCollector(), new ScmContextCollector()));
    }

    public EventBuilder(List<AttributeCollector> collectors) {
        this.collectors = collectors;
    }

    public ChangeEvent build(
            Run<?, ?> run,
            TaskListener listener,
            String eventName,
            EventState eventState,
            String dataSourceName,
            String serviceName,
            String deploymentEnvironment,
            Map<String, String> customAttributes) {

        Map<String, String> allAttributes = new LinkedHashMap<>();

        // 1. Auto-collected Jenkins + SCM context (lowest priority)
        for (AttributeCollector collector : collectors) {
            allAttributes.putAll(collector.collect(run, listener));
        }

        // 2. Explicit parameters (override auto-collected)
        if (serviceName != null && !serviceName.isBlank()) {
            allAttributes.put("service", serviceName);
        }
        if (deploymentEnvironment != null && !deploymentEnvironment.isBlank()) {
            allAttributes.put("deployment_environment", deploymentEnvironment);
        }

        // 3. Custom user attributes (highest priority override)
        if (customAttributes != null) {
            allAttributes.putAll(customAttributes);
        }

        return new ChangeEvent(
            eventName != null ? eventName : DEFAULT_EVENT_NAME,
            eventState,
            ISO_FORMAT.format(Instant.now()),
            dataSourceName,
            Collections.unmodifiableMap(allAttributes)
        );
    }
}
