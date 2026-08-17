package io.last9.jenkins.plugins.last9.model;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.util.Collections;

/**
 * Named Last9 connection profile (org + credential + API endpoint).
 * Configured globally and selected per job/step via routing profile name.
 */
public class RoutingProfile extends AbstractDescribableImpl<RoutingProfile> {

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

    @Extension
    public static class DescriptorImpl extends Descriptor<RoutingProfile> {
        @Override
        public String getDisplayName() {
            return "Routing Profile";
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
    }
}
