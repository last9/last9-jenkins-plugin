package io.last9.jenkins.plugins.last9.model;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Named Last9 connection profile (org + credential + API endpoint).
 * Configured globally and selected per job/step via routing profile name.
 */
public class RoutingProfile {

    private String name;
    private String orgSlug;
    private String credentialId;
    private String apiBaseUrl;

    @DataBoundConstructor
    public RoutingProfile(String name, String orgSlug, String credentialId, String apiBaseUrl) {
        this.name = name;
        this.orgSlug = orgSlug;
        this.credentialId = credentialId;
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    public String getOrgSlug() {
        return orgSlug;
    }

    @DataBoundSetter
    public void setOrgSlug(String orgSlug) {
        this.orgSlug = orgSlug;
    }

    public String getCredentialId() {
        return credentialId;
    }

    @DataBoundSetter
    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    @DataBoundSetter
    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }
}
