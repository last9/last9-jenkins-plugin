package io.last9.jenkins.plugins.last9;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.util.Collections;

/**
 * Global configuration for the Last9 plugin.
 * Accessible via Manage Jenkins > System > Last9.
 */
@Extension
@Symbol("last9")
public class Last9GlobalConfiguration extends GlobalConfiguration {

    private String orgSlug;
    private String apiBaseUrl = "https://app.last9.io";
    private String credentialId;
    private String defaultDataSourceName;

    public Last9GlobalConfiguration() {
        load();
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
            ? "https://app.last9.io" : apiBaseUrl.trim();
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

    @POST
    public FormValidation doCheckOrgSlug(@QueryParameter String value) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if (value == null || value.isBlank()) {
            return FormValidation.error("Organization slug is required");
        }
        return FormValidation.ok();
    }

    @POST
    public FormValidation doCheckCredentialId(@QueryParameter String value) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if (value == null || value.isBlank()) {
            return FormValidation.warning("No credential selected. A Last9 refresh token is required.");
        }
        return FormValidation.ok();
    }

    /**
     * Populates the credentials dropdown in the config UI.
     */
    public ListBoxModel doFillCredentialIdItems(@QueryParameter String credentialId) {
        Jenkins jenkins = Jenkins.get();
        if (!jenkins.hasPermission(Jenkins.ADMINISTER)) {
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
