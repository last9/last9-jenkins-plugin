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
import io.last9.jenkins.plugins.last9.event.EventService;
import io.last9.jenkins.plugins.last9.model.EventState;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Post-build action for Freestyle jobs.
 * Sends a deployment marker to Last9 after the build completes.
 *
 * Also usable in pipelines via: step([$class: 'Last9PostBuildAction', serviceName: '...'])
 */
public class Last9PostBuildAction extends Recorder implements SimpleBuildStep {

    private static final Logger LOGGER = Logger.getLogger(Last9PostBuildAction.class.getName());

    private final String serviceName;
    private String environment;
    private String eventName = "deployment";
    private String dataSourceName;
    private Map<String, String> customAttributes;
    // Send conditions — defaults: only on success, silent on everything else
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
    public String getEventName() { return eventName; }
    public String getDataSourceName() { return dataSourceName; }
    public Map<String, String> getCustomAttributes() { return customAttributes; }
    public boolean isSendOnSuccess() { return sendOnSuccess; }
    public boolean isSendOnFailure() { return sendOnFailure; }
    public boolean isSendOnUnstable() { return sendOnUnstable; }
    public boolean isSendOnAborted() { return sendOnAborted; }

    // --- Setters ---

    @DataBoundSetter public void setEnvironment(String environment) { this.environment = environment; }
    @DataBoundSetter public void setEventName(String eventName) { this.eventName = eventName; }
    @DataBoundSetter public void setDataSourceName(String dataSourceName) { this.dataSourceName = dataSourceName; }
    @DataBoundSetter public void setCustomAttributes(Map<String, String> customAttributes) { this.customAttributes = customAttributes; }
    @DataBoundSetter public void setSendOnSuccess(boolean sendOnSuccess) { this.sendOnSuccess = sendOnSuccess; }
    @DataBoundSetter public void setSendOnFailure(boolean sendOnFailure) { this.sendOnFailure = sendOnFailure; }
    @DataBoundSetter public void setSendOnUnstable(boolean sendOnUnstable) { this.sendOnUnstable = sendOnUnstable; }
    @DataBoundSetter public void setSendOnAborted(boolean sendOnAborted) { this.sendOnAborted = sendOnAborted; }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, EnvVars env,
                        Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {

        // Check build result against send conditions
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

        String orgSlug = config.getOrgSlug();
        String credentialId = config.getCredentialId();
        String dsName = (dataSourceName != null && !dataSourceName.isBlank())
            ? dataSourceName : config.getDefaultDataSourceName();

        EventService eventService = config.getEventService();

        try {
            // Post-build action fires after the build completes, so send a "stop" event
            eventService.sendDeploymentMarker(
                run, listener, credentialId, orgSlug,
                eventName, EventState.STOP, dsName,
                serviceName, environment, customAttributes
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            // Never fail the build due to observability errors
            LOGGER.log(Level.WARNING, "Failed to send deployment marker for " + run.getFullDisplayName(), e);
            listener.error("[Last9] Failed to send deployment marker: " + e.getMessage());
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Extension
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
