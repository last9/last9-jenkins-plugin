package io.last9.jenkins.plugins.last9;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.last9.jenkins.plugins.last9.api.Last9HttpApiClient;
import io.last9.jenkins.plugins.last9.auth.CachingTokenManager;
import io.last9.jenkins.plugins.last9.event.EventService;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.util.Collections;
import java.util.Objects;

/**
 * Global configuration for the Last9 plugin.
 * Accessible via Manage Jenkins > System > Last9.
 */
@Extension
@Symbol("last9")
public class Last9GlobalConfiguration extends GlobalConfiguration {

    static final String DEFAULT_API_BASE_URL = "https://app.last9.io";

    private String orgSlug;
    private volatile String apiBaseUrl = DEFAULT_API_BASE_URL;
    private String credentialId;
    private String defaultDataSourceName;

    // Singleton service instances — shared across all builds
    private transient volatile EventService eventService;
    private transient volatile String currentApiBaseUrl;
    private transient volatile String currentCredentialId;

    public Last9GlobalConfiguration() {
        load();
    }

    /**
     * Replaces the EventService with a pre-built instance. Intended for testing only.
     */
    public synchronized void setEventServiceForTesting(EventService eventService) {
        this.eventService = eventService;
        this.currentApiBaseUrl = this.apiBaseUrl;
        this.currentCredentialId = this.credentialId;
    }

    /**
     * Returns a shared EventService instance, recreating it if config changed.
     */
    public synchronized EventService getEventService() {
        if (eventService == null
                || !apiBaseUrl.equals(currentApiBaseUrl)
                || !Objects.equals(credentialId, currentCredentialId)) {
            var apiClient = new Last9HttpApiClient(apiBaseUrl);
            var tokenManager = new CachingTokenManager(apiClient);
            eventService = new EventService(apiClient, tokenManager);
            currentApiBaseUrl = apiBaseUrl;
            currentCredentialId = credentialId;
        }
        return eventService;
    }

    public static Last9GlobalConfiguration get() {
        return GlobalConfiguration.all().get(Last9GlobalConfiguration.class);
    }

    // --- Getters ---

    public String getOrgSlug() {
        return orgSlug;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public String getDefaultDataSourceName() {
        return defaultDataSourceName;
    }

    // --- Setters ---

    @DataBoundSetter
    public void setOrgSlug(String orgSlug) {
        this.orgSlug = orgSlug;
        save();
    }

    @DataBoundSetter
    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = (apiBaseUrl == null || apiBaseUrl.isBlank())
            ? DEFAULT_API_BASE_URL : apiBaseUrl.trim();
        save();
    }

    @DataBoundSetter
    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
        save();
    }

    @DataBoundSetter
    public void setDefaultDataSourceName(String defaultDataSourceName) {
        this.defaultDataSourceName = defaultDataSourceName;
        save();
    }

    // --- Form validation ---

    /**
     * Validates the configured credentials and org slug by attempting a token exchange.
     * Called by the "Test Connection" button in the global config UI.
     */
    @POST
    public FormValidation doTestConnection(
            @QueryParameter String orgSlug,
            @QueryParameter String credentialId,
            @QueryParameter String apiBaseUrl) {
        Jenkins.get().checkPermission(Jenkins.MANAGE);

        if (orgSlug == null || orgSlug.isBlank()) {
            return FormValidation.error("Organization slug is required");
        }
        if (credentialId == null || credentialId.isBlank()) {
            return FormValidation.error("API credential is required");
        }

        StringCredentials cred = CredentialsMatchers.firstOrNull(
            CredentialsProvider.lookupCredentialsInItemGroup(
                StringCredentials.class,
                Jenkins.get(),
                ACL.SYSTEM2,
                Collections.emptyList()
            ),
            CredentialsMatchers.withId(credentialId)
        );

        if (cred == null) {
            return FormValidation.error("Credential not found: " + credentialId);
        }

        String resolvedBaseUrl = (apiBaseUrl == null || apiBaseUrl.isBlank())
            ? DEFAULT_API_BASE_URL : apiBaseUrl.trim();

        try {
            var client = new Last9HttpApiClient(resolvedBaseUrl);
            var tokenManager = new CachingTokenManager(client);
            tokenManager.getAccessToken(cred.getSecret().getPlainText());
            return FormValidation.ok("Connected successfully to " + resolvedBaseUrl);
        } catch (Exception e) {
            return FormValidation.error("Connection failed: " + e.getMessage());
        }
    }

    @POST
    public FormValidation doCheckCredentialId(@QueryParameter String value) {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        if (value == null || value.isBlank()) {
            return FormValidation.warning("No credential selected. A Last9 refresh token is required.");
        }
        return FormValidation.ok();
    }

    /**
     * Populates the credentials dropdown in the config UI.
     */
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
}
