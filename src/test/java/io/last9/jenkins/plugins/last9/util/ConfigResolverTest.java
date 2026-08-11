package io.last9.jenkins.plugins.last9.util;

import hudson.EnvVars;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import io.last9.jenkins.plugins.last9.Last9GlobalConfiguration;
import io.last9.jenkins.plugins.last9.model.RoutingProfile;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ConfigResolverTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void directCredentialIdWinsOverParamAndGlobal() {
        Run<?, ?> run = runWithParam("CRED_PARAM", "cred-from-param");

        ConnectionOverrides overrides = new ConnectionOverrides();
        overrides.setCredentialId("direct-cred");
        overrides.setCredentialIdParam("CRED_PARAM");

        assertEquals("direct-cred", ConfigResolver.resolveConnection(overrides, run, null).credentialId());
    }

    @Test
    public void paramNameUsedWhenDirectValueBlank() {
        Run<?, ?> run = runWithParam("CRED_PARAM", "cred-from-param");

        ConnectionOverrides overrides = new ConnectionOverrides();
        overrides.setCredentialId("  ");
        overrides.setCredentialIdParam("CRED_PARAM");

        assertEquals("cred-from-param", ConfigResolver.resolveConnection(overrides, run, null).credentialId());
    }

    @Test
    public void envVarUsedWhenDirectAndParamBlank() {
        Run<?, ?> run = mock(Run.class);
        when(run.getAction(ParametersAction.class)).thenReturn(null);

        EnvVars env = new EnvVars();
        env.put("LAST9_ORG", "org-from-env");

        ConnectionOverrides overrides = new ConnectionOverrides();
        overrides.setOrgSlugEnvVar("LAST9_ORG");

        assertEquals("org-from-env", ConfigResolver.resolveConnection(overrides, run, env).orgSlug());
    }

    @Test
    public void routingProfileSuppliesDefaults() throws Exception {
        Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
        config.setOrgSlug("global-org");
        config.setCredentialId("global-cred");
        config.setRoutingProfiles(List.of(
            new RoutingProfile("acme-eu", "acme-eu-org", "last9-eu-cred", "https://app.last9.io")
        ));

        Run<?, ?> run = mock(Run.class);
        when(run.getAction(ParametersAction.class)).thenReturn(null);

        ConnectionOverrides overrides = new ConnectionOverrides();
        overrides.setRoutingProfile("acme-eu");

        ResolvedConnection resolved = ConfigResolver.resolveConnection(overrides, run, null);

        assertEquals("acme-eu-org", resolved.orgSlug());
        assertEquals("last9-eu-cred", resolved.credentialId());
        assertEquals("https://app.last9.io", resolved.apiBaseUrl());
    }

    @Test
    public void routingProfileFromEnvVar() throws Exception {
        Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
        config.setRoutingProfiles(List.of(
            new RoutingProfile("acme-primary", "acme", "last9-primary-cred", null)
        ));

        Run<?, ?> run = mock(Run.class);
        when(run.getAction(ParametersAction.class)).thenReturn(null);

        EnvVars env = new EnvVars();
        env.put("LAST9_ROUTING_PROFILE", "acme-primary");

        ConnectionOverrides overrides = new ConnectionOverrides();
        overrides.setRoutingProfileEnvVar("LAST9_ROUTING_PROFILE");

        ResolvedConnection resolved = ConfigResolver.resolveConnection(overrides, run, env);

        assertEquals("acme", resolved.orgSlug());
        assertEquals("last9-primary-cred", resolved.credentialId());
    }

    @Test
    public void directValueOverridesRoutingProfile() throws Exception {
        Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
        config.setRoutingProfiles(List.of(
            new RoutingProfile("acme-eu", "acme-eu-org", "last9-eu-cred", null)
        ));

        Run<?, ?> run = mock(Run.class);
        when(run.getAction(ParametersAction.class)).thenReturn(null);

        ConnectionOverrides overrides = new ConnectionOverrides();
        overrides.setRoutingProfile("acme-eu");
        overrides.setOrgSlug("override-org");

        assertEquals("override-org", ConfigResolver.resolveConnection(overrides, run, null).orgSlug());
    }

    @Test
    public void resolveRoutingProfileNameFromParam() {
        Run<?, ?> run = runWithParam("PROFILE_PARAM", "acme-eu");

        ConnectionOverrides overrides = new ConnectionOverrides();
        overrides.setRoutingProfileParam("PROFILE_PARAM");

        assertEquals("acme-eu", ConfigResolver.resolveRoutingProfileName(overrides, run, null));
    }

    @Test
    public void normalizeApiBaseUrlUsesDefaultWhenBlank() {
        assertEquals(Last9GlobalConfiguration.DEFAULT_API_BASE_URL,
            ConfigResolver.normalizeApiBaseUrl(null));
    }

    @Test
    public void deprecatedResolveOrgSlugStillWorks() {
        Run<?, ?> run = runWithParam("ORG_PARAM", "org-from-param");

        assertEquals("direct-org",
            ConfigResolver.resolveOrgSlug("direct-org", "ORG_PARAM", run));
    }

    private Run<?, ?> runWithParam(String name, String value) {
        ParameterValue pv = new StringParameterValue(name, value);
        ParametersAction action = mock(ParametersAction.class);
        when(action.getParameter(name)).thenReturn(pv);

        Run<?, ?> run = mock(Run.class);
        when(run.getAction(ParametersAction.class)).thenReturn(action);
        return run;
    }
}
