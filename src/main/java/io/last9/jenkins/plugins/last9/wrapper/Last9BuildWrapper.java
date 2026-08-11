package io.last9.jenkins.plugins.last9.wrapper;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;
import io.last9.jenkins.plugins.last9.Last9GlobalConfiguration;
import io.last9.jenkins.plugins.last9.event.EventBuilder;
import io.last9.jenkins.plugins.last9.model.EventState;
import io.last9.jenkins.plugins.last9.util.ConnectionOverrides;
import io.last9.jenkins.plugins.last9.util.DescriptorFormSupport;
import io.last9.jenkins.plugins.last9.util.Last9DeploymentMarkerSender;
import jenkins.tasks.SimpleBuildWrapper;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.io.IOException;
import java.util.Map;

/**
 * Build wrapper that tracks a deployment window in both Freestyle and Pipeline jobs.
 */
public class Last9BuildWrapper extends SimpleBuildWrapper {

    private final String serviceName;
    private String environment;
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

    @DataBoundConstructor
    public Last9BuildWrapper(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceName() { return serviceName; }
    public String getEnvironment() { return environment; }
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

    @DataBoundSetter public void setEnvironment(String environment) { this.environment = environment; }
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
    public void setUp(Context context, Run<?, ?> build, FilePath workspace,
                      Launcher launcher, TaskListener listener, EnvVars initialEnvironment)
            throws IOException, InterruptedException {
        sendMarker(build, listener, initialEnvironment, EventState.START);
        context.setDisposer(new StopDisposer(
            serviceName, environment, eventName, dataSourceName,
            connectionOverrides(), customAttributes, new EnvVars(initialEnvironment)
        ));
    }

    private void sendMarker(Run<?, ?> build, TaskListener listener, EnvVars env, EventState eventState)
            throws InterruptedException {
        if (Last9GlobalConfiguration.get() == null) {
            listener.error("[Last9] Plugin not configured. Skipping deployment marker. "
                + "Set it up at Manage Jenkins > System > Last9.");
            return;
        }

        Last9DeploymentMarkerSender.send(
            build, listener, env, connectionOverrides(),
            getEventName(), eventState, dataSourceName,
            serviceName, environment, customAttributes
        );
    }

    private static final class StopDisposer extends Disposer {

        private static final long serialVersionUID = 1L;

        private final String serviceName;
        private final String environment;
        private final String eventName;
        private final String dataSourceName;
        private final ConnectionOverrides connectionOverrides;
        private final Map<String, String> customAttributes;
        private final EnvVars startEnvironment;

        StopDisposer(String serviceName, String environment, String eventName,
                     String dataSourceName, ConnectionOverrides connectionOverrides,
                     Map<String, String> customAttributes, EnvVars startEnvironment) {
            this.serviceName = serviceName;
            this.environment = environment;
            this.eventName = eventName;
            this.dataSourceName = dataSourceName;
            this.connectionOverrides = connectionOverrides;
            this.customAttributes = customAttributes;
            this.startEnvironment = startEnvironment;
        }

        @Override
        public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
                throws IOException, InterruptedException {
            if (Last9GlobalConfiguration.get() == null) {
                listener.error("[Last9] Plugin not configured. Skipping deployment marker. "
                    + "Set it up at Manage Jenkins > System > Last9.");
                return;
            }

            String resolvedEventName = eventName != null ? eventName : EventBuilder.DEFAULT_EVENT_NAME;

            Last9DeploymentMarkerSender.send(
                build, listener, startEnvironment, connectionOverrides,
                resolvedEventName, EventState.STOP, dataSourceName,
                serviceName, environment, customAttributes
            );
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

        @POST
        public ListBoxModel doFillCredentialIdItems(
                @AncestorInPath Item context, @QueryParameter String credentialId) {
            return DescriptorFormSupport.fillCredentialIdItems(context, credentialId);
        }

        @POST
        public ListBoxModel doFillRoutingProfileItems(
                @AncestorInPath Item context, @QueryParameter String routingProfile) {
            return DescriptorFormSupport.fillRoutingProfileItems(context, routingProfile);
        }
    }
}
