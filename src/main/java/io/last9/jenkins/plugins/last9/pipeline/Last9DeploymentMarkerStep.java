package io.last9.jenkins.plugins.last9.pipeline;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import io.last9.jenkins.plugins.last9.Last9GlobalConfiguration;
import io.last9.jenkins.plugins.last9.api.ApiException;
import io.last9.jenkins.plugins.last9.event.EventService;
import io.last9.jenkins.plugins.last9.model.EventState;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pipeline step: last9DeploymentMarker(serviceName: '...', ...)
 *
 * Usage:
 *   last9DeploymentMarker serviceName: 'my-service'
 *   last9DeploymentMarker serviceName: 'my-service', environment: 'production', eventState: 'start'
 */
public class Last9DeploymentMarkerStep extends Step {

    private final String serviceName;
    private String environment;
    private static final String DEFAULT_EVENT_STATE = "stop";
    private static final String DEFAULT_EVENT_NAME = "deployment";
    private String eventState;
    private String eventName;
    private String dataSourceName;
    private Map<String, String> customAttributes;
    // Per-step overrides (optional — defaults come from global config)
    private String orgSlug;
    private String credentialId;

    @DataBoundConstructor
    public Last9DeploymentMarkerStep(String serviceName) {
        this.serviceName = serviceName;
    }

    // --- Getters ---

    public String getServiceName() { return serviceName; }
    public String getEnvironment() { return environment; }
    public String getEventState() { return eventState != null ? eventState : DEFAULT_EVENT_STATE; }
    public String getEventName() { return eventName != null ? eventName : DEFAULT_EVENT_NAME; }
    public String getDataSourceName() { return dataSourceName; }
    public Map<String, String> getCustomAttributes() { return customAttributes; }
    public String getOrgSlug() { return orgSlug; }
    public String getCredentialId() { return credentialId; }

    // --- Setters ---

    @DataBoundSetter public void setEnvironment(String environment) { this.environment = environment; }
    @DataBoundSetter public void setEventState(String eventState) { this.eventState = eventState; }
    @DataBoundSetter public void setEventName(String eventName) { this.eventName = eventName; }
    @DataBoundSetter public void setDataSourceName(String dataSourceName) { this.dataSourceName = dataSourceName; }
    @DataBoundSetter public void setCustomAttributes(Map<String, String> customAttributes) { this.customAttributes = customAttributes; }
    @DataBoundSetter public void setOrgSlug(String orgSlug) { this.orgSlug = orgSlug; }
    @DataBoundSetter public void setCredentialId(String credentialId) { this.credentialId = credentialId; }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context, this);
    }

    /**
     * Pipeline step execution. Runs on a background thread (non-blocking to CPS VM).
     */
    private static class Execution extends SynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;
        private static final Logger LOGGER = Logger.getLogger(Execution.class.getName());
        private final transient Last9DeploymentMarkerStep step;

        Execution(StepContext context, Last9DeploymentMarkerStep step) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            Run<?, ?> run = getContext().get(Run.class);
            TaskListener listener = getContext().get(TaskListener.class);

            Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
            if (config == null) {
                throw new AbortException("[Last9] Plugin global configuration not found. "
                    + "Ensure the Last9 plugin is installed and configured in Manage Jenkins > System.");
            }

            // Resolve: step override > global config
            String orgSlug = coalesce(step.getOrgSlug(), config.getOrgSlug());
            String credentialId = coalesce(step.getCredentialId(), config.getCredentialId());
            String dataSourceName = coalesce(step.getDataSourceName(), config.getDefaultDataSourceName());

            EventService eventService = config.getEventService();

            EventState state;
            try {
                state = EventState.fromString(step.getEventState());
            } catch (IllegalArgumentException e) {
                throw new AbortException("[Last9] " + e.getMessage());
            }

            try {
                eventService.sendDeploymentMarker(
                    run, listener, credentialId, orgSlug,
                    step.getEventName(), state, dataSourceName,
                    step.getServiceName(), step.getEnvironment(),
                    step.getCustomAttributes()
                );
            } catch (ApiException e) {
                LOGGER.log(Level.WARNING, "Failed to send deployment marker for " + run.getFullDisplayName(), e);
                listener.error("[Last9] Failed to send deployment marker: " + e.getMessage());
                throw new AbortException("[Last9] " + e.getMessage());
            }

            return null;
        }

        static String coalesce(String... values) {
            for (String v : values) {
                if (v != null && !v.isBlank()) return v;
            }
            return null;
        }
    }

    @Extension
    @Symbol("last9DeploymentMarker")
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "last9DeploymentMarker";
        }

        @Override
        public String getDisplayName() {
            return "Send Last9 Deployment Marker";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class, TaskListener.class, EnvVars.class);
        }
    }
}
