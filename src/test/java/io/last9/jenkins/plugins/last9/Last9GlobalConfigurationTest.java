package io.last9.jenkins.plugins.last9;

import io.last9.jenkins.plugins.last9.model.RoutingProfile;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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

    /**
     * Renders the real /configure page through Jenkins' own jelly pipeline. A missing
     * getCategory() or a malformed repeatable (name= instead of field=) throws mid-render
     * and truncates this page with an HTTP 200 and no log output — a mocked descriptor
     * lookup would never catch that, only an actual render does.
     */
    @Test
    public void last9SectionRendersOnSystemConfigurePage() throws Exception {
        HtmlPage page = j.createWebClient().goTo("manage/configure");
        String text = page.asNormalizedText();

        assertTrue("Last9 section should render on the System configure page", text.contains("Last9"));
        assertTrue("Routing Profiles field should render", text.contains("Routing Profiles"));
        assertTrue("Add routing profile button should render", text.contains("Add routing profile"));
    }

    /**
     * Deleting every row in the Routing Profiles repeatable submits a form with no
     * "routingProfiles" key at all (not an empty array), so Stapler's default bindJSON
     * never calls setRoutingProfiles and the old list would otherwise survive without
     * the configure() override clearing it explicitly.
     */
    @Test
    public void clearingAllRoutingProfilesPersists() throws Exception {
        Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
        config.setRoutingProfiles(List.of(
            new RoutingProfile("region-eu", "eu-org", "eu-cred", "https://app.last9.io")
        ));

        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("manage/configure");
        HtmlForm form = page.getFormByName("config");

        List<?> deleteButtons = form.getByXPath(
            "//button[normalize-space(string(.)) = 'Remove routing profile']"
                + " | //button[@tooltip = 'Remove routing profile']");
        assertTrue("expected a delete button for the existing routing profile row", !deleteButtons.isEmpty());
        ((HtmlButton) deleteButtons.get(0)).click();

        j.submit(form);

        assertTrue("routing profiles should be cleared after deleting the only row",
            Last9GlobalConfiguration.get().getRoutingProfiles().isEmpty());
    }
}
