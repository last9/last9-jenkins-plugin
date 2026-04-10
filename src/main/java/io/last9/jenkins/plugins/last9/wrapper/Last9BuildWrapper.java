package io.last9.jenkins.plugins.last9.wrapper;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import io.last9.jenkins.plugins.last9.Last9GlobalConfiguration;
import io.last9.jenkins.plugins.last9.event.EventBuilder;
import io.last9.jenkins.plugins.last9.event.EventService;
import io.last9.jenkins.plugins.last9.model.EventState;
import jenkins.tasks.SimpleBuildWrapper;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Build wrapper that tracks a deployment window in both Freestyle and Pipeline jobs.
 *
 * Freestyle: add in the "Build Environment" section.
 * Pipeline:  use the {@code withLast9Deployment} block step — the stop marker is sent
 *            even when the block fails:
 * <pre>
 *   withLast9Deployment(serviceName: 'payments-api', environment: 'production') {
 *     sh './deploy.sh'
 *   }
 * </pre>
 */
public class Last9BuildWrapper extends SimpleBuildWrapper {

    private static final Logger LOGGER = Logger.getLogger(Last9BuildWrapper.class.getName());

    private final String serviceName;
    private String environment;
    private String eventName;
    private String dataSourceName;
    private String orgSlug;
    private String credentialId;
    private Map<String, String> customAttributes;

    @DataBoundConstructor
    public Last9BuildWrapper(String serviceName) {
        this.serviceName = serviceName;
    }

    // --- Getters ---

    public String getServiceName() { return serviceName; }
    public String getEnvironment() { return environment; }
    public String getEventName() { return eventName != null ? eventName : EventBuilder.DEFAULT_EVENT_NAME; }
    public String getDataSourceName() { return dataSourceName; }
    public String getOrgSlug() { return orgSlug; }
    public String getCredentialId() { return credentialId; }
    public Map<String, String> getCustomAttributes() { return customAttributes; }

    // --- Setters ---

    @DataBoundSetter public void setEnvironment(String environment) { this.environment = environment; }
    @DataBoundSetter public void setEventName(String eventName) { this.eventName = eventName; }
    @DataBoundSetter public void setDataSourceName(String dataSourceName) { this.dataSourceName = dataSourceName; }
    @DataBoundSetter public void setOrgSlug(String orgSlug) { this.orgSlug = orgSlug; }
    @DataBoundSetter public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    @DataBoundSetter public void setCustomAttributes(Map<String, String> customAttributes) { this.customAttributes = customAttributes; }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace,
                      Launcher launcher, TaskListener listener, EnvVars initialEnvironment)
            throws IOException, InterruptedException {
        sendMarker(build, listener, EventState.START);
        context.setDisposer(new StopDisposer(
            serviceName, environment, eventName, dataSourceName, orgSlug, credentialId, customAttributes
        ));
    }

    private void sendMarker(Run<?, ?> build, TaskListener listener, EventState eventState) {
        Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
        if (config == null) {
            listener.error("[Last9] Plugin not configured. Skipping deployment marker. "
                + "Set it up at Manage Jenkins > System > Last9.");
            return;
        }

        String resolvedOrgSlug = coalesce(orgSlug, config.getOrgSlug());
        String resolvedCredentialId = coalesce(credentialId, config.getCredentialId());
        String dsName = coalesce(dataSourceName, config.getDefaultDataSourceName());
        EventService eventService = config.getEventService();

        try {
            eventService.sendDeploymentMarker(
                build, listener, resolvedCredentialId, resolvedOrgSlug,
                getEventName(), eventState, dsName,
                serviceName, environment, customAttributes
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send " + eventState.getValue()
                + " deployment marker for " + build.getFullDisplayName(), e);
            listener.error("[Last9] Failed to send deployment marker: " + e.getMessage());
        }
    }

    static String coalesce(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    /**
     * Serializable disposer that sends the stop marker at the end of the build wrapper block.
     * Guaranteed to run even when the block fails.
     */
    private static final class StopDisposer extends Disposer {

        private static final long serialVersionUID = 1L;

        private final String serviceName;
        private final String environment;
        private final String eventName;
        private final String dataSourceName;
        private final String orgSlug;
        private final String credentialId;
        private final Map<String, String> customAttributes;

        StopDisposer(String serviceName, String environment, String eventName,
                     String dataSourceName, String orgSlug, String credentialId,
                     Map<String, String> customAttributes) {
            this.serviceName = serviceName;
            this.environment = environment;
            this.eventName = eventName;
            this.dataSourceName = dataSourceName;
            this.orgSlug = orgSlug;
            this.credentialId = credentialId;
            this.customAttributes = customAttributes;
        }

        @Override
        public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
                throws IOException, InterruptedException {
            Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
            if (config == null) {
                listener.error("[Last9] Plugin not configured. Skipping deployment marker. "
                    + "Set it up at Manage Jenkins > System > Last9.");
                return;
            }

            String resolvedOrgSlug = coalesce(orgSlug, config.getOrgSlug());
            String resolvedCredentialId = coalesce(credentialId, config.getCredentialId());
            String dsName = coalesce(dataSourceName, config.getDefaultDataSourceName());
            String resolvedEventName = eventName != null ? eventName : EventBuilder.DEFAULT_EVENT_NAME;
            EventService eventService = config.getEventService();

            try {
                eventService.sendDeploymentMarker(
                    build, listener, resolvedCredentialId, resolvedOrgSlug,
                    resolvedEventName, EventState.STOP, dsName,
                    serviceName, environment, customAttributes
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to send stop deployment marker for "
                    + build.getFullDisplayName(), e);
                listener.error("[Last9] Failed to send deployment marker: " + e.getMessage());
            }
        }
    }

    @Extension
    @Symbol("withLast9Deployment")
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
