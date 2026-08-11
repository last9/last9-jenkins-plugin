package io.last9.jenkins.plugins.last9.util;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import io.last9.jenkins.plugins.last9.Last9GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.util.Collections;

/**
 * Shared Stapler form helpers for job and global configuration UIs.
 */
public final class DescriptorFormSupport {

    private DescriptorFormSupport() {
    }

    /**
     * Job configurators need {@link Item#CONFIGURE}; global system config needs {@link Jenkins#MANAGE}.
     */
    public static boolean hasItemOrManagePermission(Item context) {
        if (context != null) {
            return context.hasPermission(Item.CONFIGURE);
        }
        return Jenkins.get().hasPermission(Jenkins.MANAGE);
    }

    public static ListBoxModel fillCredentialIdItems(Item context, String credentialId) {
        if (!hasItemOrManagePermission(context)) {
            return new StandardListBoxModel().includeCurrentValue(credentialId);
        }
        if (context != null) {
            return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(
                    ACL.SYSTEM2,
                    context,
                    StringCredentials.class,
                    Collections.emptyList(),
                    CredentialsMatchers.always()
                )
                .includeCurrentValue(credentialId);
        }
        return new StandardListBoxModel()
            .includeEmptyValue()
            .includeMatchingAs(
                ACL.SYSTEM2,
                Jenkins.get(),
                StringCredentials.class,
                Collections.emptyList(),
                CredentialsMatchers.always()
            )
            .includeCurrentValue(credentialId);
    }

    public static ListBoxModel fillRoutingProfileItems(Item context, String routingProfile) {
        if (!hasItemOrManagePermission(context)) {
            return new StandardListBoxModel().includeCurrentValue(routingProfile);
        }
        Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
        if (config == null) {
            return new ListBoxModel();
        }
        return config.buildRoutingProfileListBox(routingProfile);
    }
}
