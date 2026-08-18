package io.last9.jenkins.plugins.last9;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.last9.jenkins.plugins.last9.api.Last9HttpApiClient;
import io.last9.jenkins.plugins.last9.auth.CachingTokenManager;
import io.last9.jenkins.plugins.last9.event.EventService;
import io.last9.jenkins.plugins.last9.model.RoutingProfile;
import io.last9.jenkins.plugins.last9.util.ConfigResolver;
import io.last9.jenkins.plugins.last9.util.DescriptorFormSupport;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Global configuration for the Last9 plugin.
 * Accessible via Manage Jenkins > System > Last9.
 */
@Extension
@Symbol("last9")
public class Last9GlobalConfiguration extends GlobalConfiguration {

    public static final String DEFAULT_API_BASE_URL = "https://app.last9.io";

    private String orgSlug;
    private volatile String apiBaseUrl = DEFAULT_API_BASE_URL;
    private String credentialId;
    private String defaultDataSourceName;
    /**
     * Comma-separated list of environment variable names to capture as
     * {@code env_<lowercase>} attributes on each change event.
     */
    private String additionalEnvVars;
    /**
     * Comma-separated list of build parameter names to capture as
     * {@code build_param_<name>} attributes. Password parameters are never exported.
     */
    private String additionalBuildParams;
    private List<RoutingProfile> routingProfiles = new ArrayList<>();

    // Legacy singleton — used when callers don't specify apiBaseUrl
    private transient volatile EventService defaultEventService;
    private transient volatile String currentApiBaseUrl;
    private transient volatile String currentCredentialId;

    private transient volatile Map<String, EventService> eventServiceByApiBaseUrl = new ConcurrentHashMap<>();

    public Last9GlobalConfiguration() {
        load();
    }

    /**
     * Replaces the EventService with a pre-built instance. Intended for testing only.
     */
    public synchronized void setEventServiceForTesting(EventService eventService) {
        this.defaultEventService = eventService;
        this.currentApiBaseUrl = this.apiBaseUrl;
        this.currentCredentialId = this.credentialId;
        this.eventServiceByApiBaseUrl = new ConcurrentHashMap<>();
    }

    /**
     * Returns a shared EventService for the global default API base URL.
     */
    public synchronized EventService getEventService() {
        return getEventService(apiBaseUrl);
    }

    /**
     * Returns a shared EventService for the given API base URL (cached per URL).
     */
    public synchronized EventService getEventService(String requestedApiBaseUrl) {
        String resolvedApiBaseUrl = ConfigResolver.normalizeApiBaseUrl(requestedApiBaseUrl);

        if (resolvedApiBaseUrl.equals(apiBaseUrl)) {
            if (defaultEventService == null
                    || !apiBaseUrl.equals(currentApiBaseUrl)
                    || !Objects.equals(credentialId, currentCredentialId)) {
                defaultEventService = createEventService(resolvedApiBaseUrl);
                currentApiBaseUrl = apiBaseUrl;
                currentCredentialId = credentialId;
            }
            eventServiceByApiBaseUrl.put(resolvedApiBaseUrl, defaultEventService);
            return defaultEventService;
        }

        return eventServiceByApiBaseUrl.computeIfAbsent(resolvedApiBaseUrl, this::createEventService);
    }

    private EventService createEventService(String resolvedApiBaseUrl) {
        var apiClient = new Last9HttpApiClient(resolvedApiBaseUrl);
        var tokenManager = new CachingTokenManager(apiClient);
        return new EventService(apiClient, tokenManager);
    }

    public static Last9GlobalConfiguration get() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return null;
        }
        return GlobalConfiguration.all().get(Last9GlobalConfiguration.class);
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws Descriptor.FormException {
        // Stapler's default bindJSON only calls setRoutingProfiles when the submitted form
        // has a "routingProfiles" key. Deleting every row leaves that key absent rather than
        // an empty array, so the old list would otherwise survive a "remove all" edit.
        boolean result = super.configure(req, json);
        if (!json.has("routingProfiles")) {
            setRoutingProfiles(Collections.emptyList());
        }
        return result;
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

    public String getAdditionalEnvVars() {
        return additionalEnvVars;
    }

    public List<RoutingProfile> getRoutingProfiles() {
        return routingProfiles != null ? routingProfiles : Collections.emptyList();
    }

    public RoutingProfile findRoutingProfile(String name) {
        if (name == null || name.isBlank() || routingProfiles == null) {
            return null;
        }
        String trimmed = name.trim();
        for (RoutingProfile profile : routingProfiles) {
            if (profile != null && profile.getName() != null && profile.getName().trim().equals(trimmed)) {
                return profile;
            }
        }
        return null;
    }

    /**
     * Returns the parsed list of additional env var names from the comma-separated config value.
     */
    public List<String> getAdditionalEnvVarsList() {
        if (additionalEnvVars == null || additionalEnvVars.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(additionalEnvVars.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    public String getAdditionalBuildParams() {
        return additionalBuildParams;
    }

    public List<String> getAdditionalBuildParamsList() {
        if (additionalBuildParams == null || additionalBuildParams.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(additionalBuildParams.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
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
        clearEventServiceCache();
        save();
    }

    @DataBoundSetter
    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
        clearEventServiceCache();
        save();
    }

    @DataBoundSetter
    public void setDefaultDataSourceName(String defaultDataSourceName) {
        this.defaultDataSourceName = defaultDataSourceName;
        save();
    }

    @DataBoundSetter
    public void setAdditionalEnvVars(String additionalEnvVars) {
        this.additionalEnvVars = additionalEnvVars;
        save();
    }

    @DataBoundSetter
    public void setAdditionalBuildParams(String additionalBuildParams) {
        this.additionalBuildParams = additionalBuildParams;
        save();
    }

    @DataBoundSetter
    public void setRoutingProfiles(List<RoutingProfile> routingProfiles) {
        List<RoutingProfile> profiles = routingProfiles != null ? new ArrayList<>(routingProfiles) : new ArrayList<>();
        String duplicateName = findDuplicateRoutingProfileName(profiles);
        if (duplicateName != null) {
            throw new IllegalArgumentException("Duplicate routing profile name: " + duplicateName);
        }
        this.routingProfiles = profiles;
        clearEventServiceCache();
        save();
    }

    /**
     * Builds routing profile dropdown options (no permission check — caller must gate).
     */
    public ListBoxModel buildRoutingProfileListBox(String routingProfile) {
        StandardListBoxModel model = new StandardListBoxModel();
        model.includeCurrentValue(routingProfile);
        model.add("", "");
        for (RoutingProfile profile : getRoutingProfiles()) {
            if (profile != null && profile.getName() != null && !profile.getName().isBlank()) {
                model.add(profile.getName(), profile.getName());
            }
        }
        return model;
    }

    private static String findDuplicateRoutingProfileName(List<RoutingProfile> profiles) {
        Set<String> seen = new HashSet<>();
        for (RoutingProfile profile : profiles) {
            if (profile == null || profile.getName() == null || profile.getName().isBlank()) {
                continue;
            }
            String name = profile.getName().trim();
            if (!seen.add(name)) {
                return name;
            }
        }
        return null;
    }

    private void clearEventServiceCache() {
        defaultEventService = null;
        eventServiceByApiBaseUrl = new ConcurrentHashMap<>();
    }

    // --- Form validation ---

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

        String resolvedBaseUrl = ConfigResolver.normalizeApiBaseUrl(apiBaseUrl);

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

    @POST
    public ListBoxModel doFillCredentialIdItems(@QueryParameter String credentialId) {
        return DescriptorFormSupport.fillCredentialIdItems(null, credentialId);
    }

    @POST
    public ListBoxModel doFillRoutingProfileItems(@QueryParameter String routingProfile) {
        if (!Jenkins.get().hasPermission(Jenkins.MANAGE)) {
            return new StandardListBoxModel().includeCurrentValue(routingProfile);
        }
        return buildRoutingProfileListBox(routingProfile);
    }
}
