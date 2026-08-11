package io.last9.jenkins.plugins.last9.freestyle;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.ListBoxModel;
import io.last9.jenkins.plugins.last9.Last9GlobalConfiguration;
import io.last9.jenkins.plugins.last9.event.EventBuilder;
import io.last9.jenkins.plugins.last9.model.EventState;
import io.last9.jenkins.plugins.last9.util.ConnectionOverrides;
import io.last9.jenkins.plugins.last9.util.Last9DeploymentMarkerSender;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Sends a deployment marker to Last9. Works in both Freestyle and Pipeline.
 */
public class Last9PostBuildAction extends Recorder implements SimpleBuildStep {

    private final String serviceName;
    private String environment;
    private String eventState;
    private String eventName;
    private String dataSourceName;
    private String orgSlug;
    private String orgSlugParam;
    private String orgSlugEnvVar;
    private String credentialId;
    private String credentialIdParam;
    private String credentialIdEnvVar;
    private String apiBaseUrl;
    private String apiBaseUrlParam;
    private String apiBaseUrlEnvVar;
    private String routingProfile;
    private String routingProfileParam;
    private String routingProfileEnvVar;
    private Map<String, String> customAttributes;
    private boolean sendOnSuccess = true;
    private boolean sendOnFailure = false;
    private boolean sendOnUnstable = false;
    private boolean sendOnAborted = false;

    @DataBoundConstructor
    public Last9PostBuildAction(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceName() { return serviceName; }
    public String getEnvironment() { return environment; }
    public String getEventState() { return eventState; }
    public String getEventName() { return eventName != null ? eventName : EventBuilder.DEFAULT_EVENT_NAME; }
    public String getDataSourceName() { return dataSourceName; }
    public String getOrgSlug() { return orgSlug; }
    public String getOrgSlugParam() { return orgSlugParam; }
    public String getOrgSlugEnvVar() { return orgSlugEnvVar; }
    public String getCredentialId() { return credentialId; }
    public String getCredentialIdParam() { return credentialIdParam; }
    public String getCredentialIdEnvVar() { return credentialIdEnvVar; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public String getApiBaseUrlParam() { return apiBaseUrlParam; }
    public String getApiBaseUrlEnvVar() { return apiBaseUrlEnvVar; }
    public String getRoutingProfile() { return routingProfile; }
    public String getRoutingProfileParam() { return routingProfileParam; }
    public String getRoutingProfileEnvVar() { return routingProfileEnvVar; }
    public Map<String, String> getCustomAttributes() { return customAttributes; }
    public boolean isSendOnSuccess() { return sendOnSuccess; }
    public boolean isSendOnFailure() { return sendOnFailure; }
    public boolean isSendOnUnstable() { return sendOnUnstable; }
    public boolean isSendOnAborted() { return sendOnAborted; }

    @DataBoundSetter public void setEnvironment(String environment) { this.environment = environment; }
    @DataBoundSetter public void setEventState(String eventState) { this.eventState = eventState; }
    @DataBoundSetter public void setEventName(String eventName) { this.eventName = eventName; }
    @DataBoundSetter public void setDataSourceName(String dataSourceName) { this.dataSourceName = dataSourceName; }
    @DataBoundSetter public void setOrgSlug(String orgSlug) { this.orgSlug = orgSlug; }
    @DataBoundSetter public void setOrgSlugParam(String orgSlugParam) { this.orgSlugParam = orgSlugParam; }
    @DataBoundSetter public void setOrgSlugEnvVar(String orgSlugEnvVar) { this.orgSlugEnvVar = orgSlugEnvVar; }
    @DataBoundSetter public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    @DataBoundSetter public void setCredentialIdParam(String credentialIdParam) { this.credentialIdParam = credentialIdParam; }
    @DataBoundSetter public void setCredentialIdEnvVar(String credentialIdEnvVar) { this.credentialIdEnvVar = credentialIdEnvVar; }
    @DataBoundSetter public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    @DataBoundSetter public void setApiBaseUrlParam(String apiBaseUrlParam) { this.apiBaseUrlParam = apiBaseUrlParam; }
    @DataBoundSetter public void setApiBaseUrlEnvVar(String apiBaseUrlEnvVar) { this.apiBaseUrlEnvVar = apiBaseUrlEnvVar; }
    @DataBoundSetter public void setRoutingProfile(String routingProfile) { this.routingProfile = routingProfile; }
    @DataBoundSetter public void setRoutingProfileParam(String routingProfileParam) { this.routingProfileParam = routingProfileParam; }
    @DataBoundSetter public void setRoutingProfileEnvVar(String routingProfileEnvVar) { this.routingProfileEnvVar = routingProfileEnvVar; }
    @DataBoundSetter public void setCustomAttributes(Map<String, String> customAttributes) { this.customAttributes = customAttributes; }
    @DataBoundSetter public void setSendOnSuccess(boolean sendOnSuccess) { this.sendOnSuccess = sendOnSuccess; }
    @DataBoundSetter public void setSendOnFailure(boolean sendOnFailure) { this.sendOnFailure = sendOnFailure; }
    @DataBoundSetter public void setSendOnUnstable(boolean sendOnUnstable) { this.sendOnUnstable = sendOnUnstable; }
    @DataBoundSetter public void setSendOnAborted(boolean sendOnAborted) { this.sendOnAborted = sendOnAborted; }

  ConnectionOverrides connectionOverrides() {
        ConnectionOverrides overrides = new ConnectionOverrides();
        overrides.setOrgSlug(orgSlug);
        overrides.setOrgSlugParam(orgSlugParam);
        overrides.setOrgSlugEnvVar(orgSlugEnvVar);
        overrides.setCredentialId(credentialId);
        overrides.setCredentialIdParam(credentialIdParam);
        overrides.setCredentialIdEnvVar(credentialIdEnvVar);
        overrides.setApiBaseUrl(apiBaseUrl);
        overrides.setApiBaseUrlParam(apiBaseUrlParam);
        overrides.setApiBaseUrlEnvVar(apiBaseUrlEnvVar);
        overrides.setRoutingProfile(routingProfile);
        overrides.setRoutingProfileParam(routingProfileParam);
        overrides.setRoutingProfileEnvVar(routingProfileEnvVar);
        return overrides;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, EnvVars env,
                        Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {

        Result result = run.getResult();
        if (result != null) {
            if (Result.SUCCESS.equals(result) && !sendOnSuccess) return;
            if (Result.FAILURE.equals(result) && !sendOnFailure) return;
            if (Result.UNSTABLE.equals(result) && !sendOnUnstable) return;
            if (Result.ABORTED.equals(result) && !sendOnAborted) return;
        }

        if (Last9GlobalConfiguration.get() == null) {
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

        Last9DeploymentMarkerSender.send(
            run, listener, env, connectionOverrides(),
            getEventName(), state, dataSourceName,
            serviceName, environment, customAttributes
        );
    }

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

        @POST
        public ListBoxModel doFillCredentialIdItems(@QueryParameter String credentialId) {
            Jenkins jenkins = Jenkins.get();
            if (!jenkins.hasPermission(Jenkins.MANAGE)) {
                return new StandardListBoxModel().includeCurrentValue(credentialId);
            }
            return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(
                    ACL.SYSTEM2,
                    jenkins,
                    StringCredentials.class,
                    Collections.emptyList(),
                    CredentialsMatchers.always()
                )
                .includeCurrentValue(credentialId);
        }

        @POST
        public ListBoxModel doFillRoutingProfileItems(@QueryParameter String routingProfile) {
            if (!Jenkins.get().hasPermission(Jenkins.MANAGE)) {
                return new StandardListBoxModel().includeCurrentValue(routingProfile);
            }
            Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
            if (config == null) {
                return new ListBoxModel();
            }
            return config.doFillRoutingProfileItems(routingProfile);
        }
    }
}
