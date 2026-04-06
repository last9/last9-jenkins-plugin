package io.last9.jenkins.plugins.last9.wrapper;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import io.last9.jenkins.plugins.last9.Last9GlobalConfiguration;
import io.last9.jenkins.plugins.last9.event.EventService;
import io.last9.jenkins.plugins.last9.model.EventState;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Build wrapper for Freestyle jobs.
 *
 * Sends a "start" deployment marker before the first build step runs,
 * and a "stop" marker after the last build step finishes (including on failure).
 *
 * This is the right way to track a deployment window in Freestyle jobs.
 * For Pipeline, use two explicit last9DeploymentMarker steps instead.
 */
public class Last9BuildWrapper extends BuildWrapper {

    private static final Logger LOGGER = Logger.getLogger(Last9BuildWrapper.class.getName());

    private final String serviceName;
    private String environment;
    private String eventName = "deployment";
    private String dataSourceName;
    private Map<String, String> customAttributes;

    @DataBoundConstructor
    public Last9BuildWrapper(String serviceName) {
        this.serviceName = serviceName;
    }

    // --- Getters ---

    public String getServiceName() { return serviceName; }
    public String getEnvironment() { return environment; }
    public String getEventName() { return eventName; }
    public String getDataSourceName() { return dataSourceName; }
    public Map<String, String> getCustomAttributes() { return customAttributes; }

    // --- Setters ---

    @DataBoundSetter public void setEnvironment(String environment) { this.environment = environment; }
    @DataBoundSetter public void setEventName(String eventName) { this.eventName = eventName; }
    @DataBoundSetter public void setDataSourceName(String dataSourceName) { this.dataSourceName = dataSourceName; }
    @DataBoundSetter public void setCustomAttributes(Map<String, String> customAttributes) { this.customAttributes = customAttributes; }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {

        sendMarker(build, listener, EventState.START);

        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                sendMarker(build, listener, EventState.STOP);
                return true;
            }
        };
    }

    private void sendMarker(AbstractBuild<?, ?> build, BuildListener listener, EventState eventState) {
        Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
        if (config == null) {
            listener.error("[Last9] Plugin not configured. Skipping deployment marker. "
                + "Set it up at Manage Jenkins > System > Last9.");
            return;
        }

        String orgSlug = config.getOrgSlug();
        String credentialId = config.getCredentialId();
        String dsName = (dataSourceName != null && !dataSourceName.isBlank())
            ? dataSourceName : config.getDefaultDataSourceName();

        EventService eventService = config.getEventService();

        try {
            eventService.sendDeploymentMarker(
                build, listener, credentialId, orgSlug,
                eventName, eventState, dsName,
                serviceName, environment, customAttributes
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Never fail the build over observability
            LOGGER.log(Level.WARNING, "Failed to send " + eventState.getValue()
                + " deployment marker for " + build.getFullDisplayName(), e);
            listener.error("[Last9] Failed to send deployment marker: " + e.getMessage());
        }
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        @Override
        public String getDisplayName() {
            return "Track Last9 Deployment Window (start + stop)";
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }
    }
}
