package io.last9.jenkins.plugins.last9.event;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.last9.jenkins.plugins.last9.api.ApiException;
import io.last9.jenkins.plugins.last9.api.Last9ApiClient;
import io.last9.jenkins.plugins.last9.auth.TokenManager;
import io.last9.jenkins.plugins.last9.model.ChangeEvent;
import io.last9.jenkins.plugins.last9.model.ChangeEventPayload;
import io.last9.jenkins.plugins.last9.model.EventState;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Top-level orchestrator: resolves credentials, obtains token, builds event, sends it.
 * This is the single entry point called by both Pipeline and Freestyle integrations.
 */
public class EventService {

    private static final Logger LOGGER = Logger.getLogger(EventService.class.getName());

    private final Last9ApiClient apiClient;
    private final TokenManager tokenManager;
    private final EventBuilder eventBuilder;

    public EventService(Last9ApiClient apiClient, TokenManager tokenManager) {
        this(apiClient, tokenManager, new EventBuilder());
    }

    public EventService(Last9ApiClient apiClient, TokenManager tokenManager, EventBuilder eventBuilder) {
        this.apiClient = apiClient;
        this.tokenManager = tokenManager;
        this.eventBuilder = eventBuilder;
    }

    /**
     * Full lifecycle: resolve credential -> exchange token -> build event -> send.
     */
    public void sendDeploymentMarker(
            Run<?, ?> run,
            TaskListener listener,
            String credentialId,
            String orgSlug,
            String eventName,
            EventState eventState,
            String dataSourceName,
            String serviceName,
            String deploymentEnvironment,
            Map<String, String> customAttributes) throws ApiException {

        listener.getLogger().println("[Last9] Sending deployment marker: "
            + eventName + " (" + eventState.getValue() + ") for " + serviceName);

        // 1. Resolve refresh token from Jenkins Credentials store
        String refreshToken = resolveCredential(run, credentialId);

        // 2. Get (cached) access token
        String accessToken = tokenManager.getAccessToken(refreshToken);

        // 3. Build event with auto-collected + user-supplied attributes
        ChangeEvent event = eventBuilder.build(
            run, listener, eventName, eventState, dataSourceName,
            serviceName, deploymentEnvironment, customAttributes
        );

        // 4. Send
        ChangeEventPayload payload = ChangeEventPayload.from(event);
        apiClient.sendChangeEvent(orgSlug, accessToken, payload);

        listener.getLogger().println("[Last9] Deployment marker sent successfully");
        LOGGER.log(Level.INFO, "Deployment marker sent: {0} ({1}) for {2}",
            new Object[]{eventName, eventState.getValue(), serviceName});
    }

    private String resolveCredential(Run<?, ?> run, String credentialId) throws ApiException {
        if (credentialId == null || credentialId.isBlank()) {
            throw new ApiException("Last9 credential ID is not configured. "
                + "Set it in Manage Jenkins > System > Last9 or pass credentialId to the step.");
        }

        StringCredentials cred = CredentialsProvider.findCredentialById(
            credentialId,
            StringCredentials.class,
            run,
            Collections.emptyList()
        );

        if (cred == null) {
            throw new ApiException("Last9 credential not found: " + credentialId
                + ". Ensure a 'Secret text' credential with this ID exists.");
        }

        return cred.getSecret().getPlainText();
    }
}
