package io.last9.jenkins.plugins.last9;

import io.last9.jenkins.plugins.last9.model.RoutingProfile;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;

import static org.junit.Assert.assertThrows;

public class Last9GlobalConfigurationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void rejectsDuplicateRoutingProfileNames() {
        Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
        assertThrows(IllegalArgumentException.class, () ->
            config.setRoutingProfiles(List.of(
                new RoutingProfile("region-eu", "eu-org", "eu-cred", "https://app.last9.io"),
                new RoutingProfile("region-eu", "other-org", "other-cred", "https://app.last9.io")
            ))
        );
    }
}
