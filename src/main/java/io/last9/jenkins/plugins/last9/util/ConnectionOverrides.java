package io.last9.jenkins.plugins.last9.util;

/**
 * Per-step overrides for Last9 connection settings.
 * Populated from Pipeline steps, freestyle UI, or build wrapper config.
 */
public class ConnectionOverrides implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

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

    public String getOrgSlug() { return orgSlug; }
    public void setOrgSlug(String orgSlug) { this.orgSlug = orgSlug; }

    public String getOrgSlugParam() { return orgSlugParam; }
    public void setOrgSlugParam(String orgSlugParam) { this.orgSlugParam = orgSlugParam; }

    public String getOrgSlugEnvVar() { return orgSlugEnvVar; }
    public void setOrgSlugEnvVar(String orgSlugEnvVar) { this.orgSlugEnvVar = orgSlugEnvVar; }

    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }

    public String getCredentialIdParam() { return credentialIdParam; }
    public void setCredentialIdParam(String credentialIdParam) { this.credentialIdParam = credentialIdParam; }

    public String getCredentialIdEnvVar() { return credentialIdEnvVar; }
    public void setCredentialIdEnvVar(String credentialIdEnvVar) { this.credentialIdEnvVar = credentialIdEnvVar; }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }

    public String getApiBaseUrlParam() { return apiBaseUrlParam; }
    public void setApiBaseUrlParam(String apiBaseUrlParam) { this.apiBaseUrlParam = apiBaseUrlParam; }

    public String getApiBaseUrlEnvVar() { return apiBaseUrlEnvVar; }
    public void setApiBaseUrlEnvVar(String apiBaseUrlEnvVar) { this.apiBaseUrlEnvVar = apiBaseUrlEnvVar; }

    public String getRoutingProfile() { return routingProfile; }
    public void setRoutingProfile(String routingProfile) { this.routingProfile = routingProfile; }

    public String getRoutingProfileParam() { return routingProfileParam; }
    public void setRoutingProfileParam(String routingProfileParam) { this.routingProfileParam = routingProfileParam; }

    public String getRoutingProfileEnvVar() { return routingProfileEnvVar; }
    public void setRoutingProfileEnvVar(String routingProfileEnvVar) { this.routingProfileEnvVar = routingProfileEnvVar; }
}
