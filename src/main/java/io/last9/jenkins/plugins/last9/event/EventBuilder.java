package io.last9.jenkins.plugins.last9.event;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.last9.jenkins.plugins.last9.collect.AttributeCollector;
import io.last9.jenkins.plugins.last9.collect.EnvVarsCollector;
import io.last9.jenkins.plugins.last9.collect.JenkinsContextCollector;
import io.last9.jenkins.plugins.last9.collect.ParametersCollector;
import io.last9.jenkins.plugins.last9.collect.ScmContextCollector;
import io.last9.jenkins.plugins.last9.deployment.DeploymentWindowTracker;
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
    private final DeploymentWindowTracker windowTracker;

    private static final String DEPLOYMENT_ID_KEY = "deployment_id";
    private static final String DEPLOYMENT_WINDOW_DURATION_KEY = "deployment_window_duration_ms";

    public EventBuilder() {
        this(
            List.of(
                new JenkinsContextCollector(),
                new ScmContextCollector(),
                new ParametersCollector(),
                new EnvVarsCollector()
            ),
            new DeploymentWindowTracker()
        );
    }

    public EventBuilder(List<AttributeCollector> collectors) {
        this(collectors, new DeploymentWindowTracker());
    }

    public EventBuilder(List<AttributeCollector> collectors, DeploymentWindowTracker windowTracker) {
        this.collectors = collectors;
        this.windowTracker = windowTracker;
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

        // 1. Auto-collected Jenkins + SCM + parameters + env context (lowest priority)
        for (AttributeCollector collector : collectors) {
            allAttributes.putAll(collector.collect(run, listener));
        }

        // 3. Explicit parameters (override auto-collected)
        if (serviceName != null && !serviceName.isBlank()) {
            allAttributes.put("service", serviceName);
        }
        if (deploymentEnvironment != null && !deploymentEnvironment.isBlank()) {
            allAttributes.put("deployment_environment", deploymentEnvironment);
        }

        // 4. Custom user attributes (reserved deployment keys are plugin-managed)
        if (customAttributes != null) {
            for (Map.Entry<String, String> entry : customAttributes.entrySet()) {
                String key = entry.getKey();
                if (key == null || isReservedDeploymentKey(key)) {
                    continue;
                }
                allAttributes.put(key, entry.getValue());
            }
        }

        // 5. Deployment window (must not be overridden by custom attributes)
        if (eventState == EventState.START) {
            String deploymentId = windowTracker.start(run);
            allAttributes.put("deployment_id", deploymentId);
        } else if (eventState == EventState.STOP) {
            String deploymentId = windowTracker.getDeploymentId(run);
            if (deploymentId != null) {
                allAttributes.put("deployment_id", deploymentId);
            }
            long durationMs = windowTracker.stop(run);
            if (durationMs >= 0) {
                allAttributes.put("deployment_window_duration_ms", String.valueOf(durationMs));
            }
        }

        return new ChangeEvent(
            eventName != null ? eventName : DEFAULT_EVENT_NAME,
            eventState,
            ISO_FORMAT.format(Instant.now()),
            dataSourceName,
            Collections.unmodifiableMap(allAttributes)
        );
    }

    private static boolean isReservedDeploymentKey(String key) {
        return DEPLOYMENT_ID_KEY.equals(key) || DEPLOYMENT_WINDOW_DURATION_KEY.equals(key);
    }
}
