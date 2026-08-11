package io.last9.jenkins.plugins.last9.util;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.last9.jenkins.plugins.last9.Last9GlobalConfiguration;
import io.last9.jenkins.plugins.last9.event.EventService;
import io.last9.jenkins.plugins.last9.model.EventState;
import io.last9.jenkins.plugins.last9.model.RoutingProfile;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class Last9DeploymentMarkerSenderTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void skipsWhenRoutingProfileNotFound() throws Exception {
        Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
        EventService eventService = mock(EventService.class);
        config.setEventServiceForTesting(eventService);

        ConnectionOverrides overrides = new ConnectionOverrides();
        overrides.setRoutingProfile("missing-profile");

        TaskListener listener = mock(TaskListener.class);
        Run<?, ?> run = mock(Run.class);

        Last9DeploymentMarkerSender.send(
            run, listener, new EnvVars(), overrides,
            "deployment", EventState.START, null,
            "payments-api", "production", null
        );

        verify(listener).error(contains("Routing profile not found"));
        verifyNoInteractions(eventService);
    }

    @Test
    public void sendsWhenRoutingProfileExists() throws Exception {
        Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
        config.setRoutingProfiles(List.of(
            new RoutingProfile("acme-eu", "acme-eu-org", "last9-eu-cred", "https://app.last9.io")
        ));

        EventService eventService = mock(EventService.class);
        config.setEventServiceForTesting(eventService);

        ConnectionOverrides overrides = new ConnectionOverrides();
        overrides.setRoutingProfile("acme-eu");

        TaskListener listener = mock(TaskListener.class);
        Run<?, ?> run = mock(Run.class);

        Last9DeploymentMarkerSender.send(
            run, listener, new EnvVars(), overrides,
            "deployment", EventState.START, null,
            "payments-api", "production", null
        );

        verify(eventService).sendDeploymentMarker(
            eq(run), eq(listener), eq("last9-eu-cred"), eq("acme-eu-org"),
            eq("deployment"), eq(EventState.START), isNull(),
            eq("payments-api"), eq("production"), isNull()
        );
    }
}
