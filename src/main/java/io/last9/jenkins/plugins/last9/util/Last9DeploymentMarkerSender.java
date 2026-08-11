package io.last9.jenkins.plugins.last9.util;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.last9.jenkins.plugins.last9.Last9GlobalConfiguration;
import io.last9.jenkins.plugins.last9.event.EventService;
import io.last9.jenkins.plugins.last9.model.EventState;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared send path for post-build actions and build wrappers.
 */
public final class Last9DeploymentMarkerSender {

    private static final Logger LOGGER = Logger.getLogger(Last9DeploymentMarkerSender.class.getName());

    private Last9DeploymentMarkerSender() {
    }

    public static void send(
            Run<?, ?> run,
            TaskListener listener,
            EnvVars env,
            ConnectionOverrides overrides,
            String eventName,
            EventState eventState,
            String dataSourceName,
            String serviceName,
            String deploymentEnvironment,
            Map<String, String> customAttributes) throws InterruptedException {

        Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
        if (config == null) {
            listener.error("[Last9] Plugin not configured. Skipping deployment marker. "
                + "Set it up at Manage Jenkins > System > Last9.");
            return;
        }

        ResolvedConnection connection = ConfigResolver.resolveConnection(overrides, run, env);

        String requestedProfile = ConfigResolver.resolveRoutingProfileName(overrides, run, env);
        if (requestedProfile != null && config.findRoutingProfile(requestedProfile) == null) {
            listener.error("[Last9] Routing profile not found: " + requestedProfile
                + ". Skipping deployment marker.");
            return;
        }

        String dsName = firstNonBlank(dataSourceName, config.getDefaultDataSourceName());
        EventService eventService = config.getEventService(connection.apiBaseUrl());

        try {
            eventService.sendDeploymentMarker(
                run, listener, connection.credentialId(), connection.orgSlug(),
                eventName, eventState, dsName,
                serviceName, deploymentEnvironment, customAttributes
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send deployment marker for " + run.getFullDisplayName(), e);
            listener.error("[Last9] Failed to send deployment marker: " + e.getMessage());
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
