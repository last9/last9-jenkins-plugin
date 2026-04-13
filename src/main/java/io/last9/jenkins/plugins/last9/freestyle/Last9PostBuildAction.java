package io.last9.jenkins.plugins.last9.freestyle;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import io.last9.jenkins.plugins.last9.Last9GlobalConfiguration;
import io.last9.jenkins.plugins.last9.event.EventBuilder;
import io.last9.jenkins.plugins.last9.event.EventService;
import io.last9.jenkins.plugins.last9.model.EventState;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends a deployment marker to Last9. Works in both Freestyle and Pipeline.
 *
 * Freestyle: add as a post-build action. Use the send-on-* flags to control
 * when the marker fires relative to the build result.
 *
 * Pipeline: call directly as a step — no node block required:
 * <pre>
 *   last9DeploymentMarker serviceName: 'payments-api', eventState: 'start'
 *   // ... deploy ...
 *   last9DeploymentMarker serviceName: 'payments-api', eventState: 'stop'
 * </pre>
 */
public class Last9PostBuildAction extends Recorder implements SimpleBuildStep {

    private static final Logger LOGGER = Logger.getLogger(Last9PostBuildAction.class.getName());

    private final String serviceName;
    private String environment;
    private String eventState;    // 'start' or 'stop' — defaults to 'stop'
    private String eventName;
    private String dataSourceName;
    private String orgSlug;       // per-step override (useful for multi-team Jenkins)
    private String credentialId;  // per-step override
    private Map<String, String> customAttributes;
    // Send conditions — only meaningful in Freestyle post-build context
    private boolean sendOnSuccess = true;
    private boolean sendOnFailure = false;
    private boolean sendOnUnstable = false;
    private boolean sendOnAborted = false;

    @DataBoundConstructor
    public Last9PostBuildAction(String serviceName) {
        this.serviceName = serviceName;
    }

    // --- Getters ---

    public String getServiceName() { return serviceName; }
    public String getEnvironment() { return environment; }
    public String getEventState() { return eventState; }
    public String getEventName() { return eventName != null ? eventName : EventBuilder.DEFAULT_EVENT_NAME; }
    public String getDataSourceName() { return dataSourceName; }
    public String getOrgSlug() { return orgSlug; }
    public String getCredentialId() { return credentialId; }
    public Map<String, String> getCustomAttributes() { return customAttributes; }
    public boolean isSendOnSuccess() { return sendOnSuccess; }
    public boolean isSendOnFailure() { return sendOnFailure; }
    public boolean isSendOnUnstable() { return sendOnUnstable; }
    public boolean isSendOnAborted() { return sendOnAborted; }

    // --- Setters ---

    @DataBoundSetter public void setEnvironment(String environment) { this.environment = environment; }
    @DataBoundSetter public void setEventState(String eventState) { this.eventState = eventState; }
    @DataBoundSetter public void setEventName(String eventName) { this.eventName = eventName; }
    @DataBoundSetter public void setDataSourceName(String dataSourceName) { this.dataSourceName = dataSourceName; }
    @DataBoundSetter public void setOrgSlug(String orgSlug) { this.orgSlug = orgSlug; }
    @DataBoundSetter public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    @DataBoundSetter public void setCustomAttributes(Map<String, String> customAttributes) { this.customAttributes = customAttributes; }
    @DataBoundSetter public void setSendOnSuccess(boolean sendOnSuccess) { this.sendOnSuccess = sendOnSuccess; }
    @DataBoundSetter public void setSendOnFailure(boolean sendOnFailure) { this.sendOnFailure = sendOnFailure; }
    @DataBoundSetter public void setSendOnUnstable(boolean sendOnUnstable) { this.sendOnUnstable = sendOnUnstable; }
    @DataBoundSetter public void setSendOnAborted(boolean sendOnAborted) { this.sendOnAborted = sendOnAborted; }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, EnvVars env,
                        Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {

        // Result-based filtering is only meaningful in post-build / Freestyle context.
        // In Pipeline the result may not be set yet when this step runs mid-stage.
        Result result = run.getResult();
        if (result != null) {
            if (Result.SUCCESS.equals(result) && !sendOnSuccess) return;
            if (Result.FAILURE.equals(result) && !sendOnFailure) return;
            if (Result.UNSTABLE.equals(result) && !sendOnUnstable) return;
            if (Result.ABORTED.equals(result) && !sendOnAborted) return;
        }

        Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
        if (config == null) {
            listener.error("[Last9] Plugin not configured. Skipping deployment marker. "
                + "Set it up at Manage Jenkins > System > Last9.");
            return;
        }

        EventState state;
        try {
            state = EventState.fromString(eventState);
        } catch (IllegalArgumentException e) {
            listener.error("[Last9] Invalid eventState '" + eventState
                + "'. Use 'start' or 'stop'. Skipping.");
            return;
        }

        String resolvedOrgSlug = coalesce(orgSlug, config.getOrgSlug());
        String resolvedCredentialId = coalesce(credentialId, config.getCredentialId());
        String dsName = coalesce(dataSourceName, config.getDefaultDataSourceName());
        EventService eventService = config.getEventService();

        try {
            eventService.sendDeploymentMarker(
                run, listener, resolvedCredentialId, resolvedOrgSlug,
                getEventName(), state, dsName,
                serviceName, environment, customAttributes
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send deployment marker for " + run.getFullDisplayName(), e);
            listener.error("[Last9] Failed to send deployment marker: " + e.getMessage());
        }
    }

    // XStream does not call field initializers on deserialization. sendOnSuccess defaults
    // to true, but jobs saved before this field existed will deserialize it as false.
    // readResolve restores the intended default when all flags are false (impossible via UI).
    private Object readResolve() {
        if (!sendOnSuccess && !sendOnFailure && !sendOnUnstable && !sendOnAborted) {
            sendOnSuccess = true;
        }
        return this;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean requiresWorkspace() {
        return false;
    }

    static String coalesce(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    @Extension
    @Symbol("last9DeploymentMarker")
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public String getDisplayName() {
            return "Send Last9 Deployment Marker";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
