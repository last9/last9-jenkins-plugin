package io.last9.jenkins.plugins.last9.integration;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import io.last9.jenkins.plugins.last9.Last9GlobalConfiguration;
import io.last9.jenkins.plugins.last9.freestyle.Last9PostBuildAction;
import io.last9.jenkins.plugins.last9.wrapper.Last9BuildWrapper;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * Integration tests that spin up a real Jenkins instance.
 *
 * Focus: verify that the plugin's non-fatal contract holds — API errors never
 * fail a build — and that Freestyle condition flags are respected. These tests
 * deliberately do not configure real credentials; the credential-resolution
 * failure IS the failure path under test.
 */
public class Last9PipelineIntegrationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * A pipeline step must not fail the build when the API is unreachable.
     * This is the single most important contract of the plugin.
     */
    @Test
    public void pipelineStep_apiFailure_buildStillSucceeds() throws Exception {
        configureGlobalPlugin(/* credentialId */ null);

        WorkflowJob job = j.createProject(WorkflowJob.class, "test-nonfatal-pipeline");
        job.setDefinition(new CpsFlowDefinition(
            "node { last9DeploymentMarker serviceName: 'payments-api', environment: 'production' }",
            true
        ));

        WorkflowRun run = j.buildAndAssertSuccess(job);
        j.assertLogContains("[Last9]", run);
    }

    /**
     * Invalid eventState is a user error. It should log and continue, not explode.
     */
    @Test
    public void pipelineStep_invalidEventState_buildStillSucceeds() throws Exception {
        configureGlobalPlugin(null);

        WorkflowJob job = j.createProject(WorkflowJob.class, "test-bad-state");
        job.setDefinition(new CpsFlowDefinition(
            "node { last9DeploymentMarker serviceName: 'svc', eventState: 'launched' }",
            true
        ));

        WorkflowRun run = j.buildAndAssertSuccess(job);
        j.assertLogContains("Invalid eventState", run);
        j.assertLogContains("Skipping", run);
    }

    /**
     * Freestyle action with sendOnSuccess=false must skip without calling the API.
     * The build should still succeed.
     */
    @Test
    public void freestyleAction_sendOnSuccessFalse_skipsOnSuccess() throws Exception {
        configureGlobalPlugin(null);

        AtomicBoolean apiCalled = new AtomicBoolean(false);

        FreeStyleProject project = j.createFreeStyleProject("test-freestyle-skip");
        Last9PostBuildAction action = new Last9PostBuildAction("payments-api");
        action.setSendOnSuccess(false);
        project.getPublishersList().add(action);

        FreeStyleBuild build = j.buildAndAssertSuccess(project);
        assertEquals(Result.SUCCESS, build.getResult());
        // No [Last9] log lines at all — the action returned before logging anything
        assertFalse(build.getLog(100).stream().anyMatch(line -> line.contains("[Last9] Sending")));
    }

    /**
     * Freestyle action with sendOnFailure=false must skip when the build fails.
     * Critically: the build result must remain FAILURE, not be worsened by the plugin.
     */
    @Test
    public void freestyleAction_sendOnFailureFalse_skipsOnFailure() throws Exception {
        configureGlobalPlugin(null);

        FreeStyleProject project = j.createFreeStyleProject("test-freestyle-fail-skip");
        project.getBuildersList().add(new hudson.tasks.Shell("exit 1"));
        Last9PostBuildAction action = new Last9PostBuildAction("payments-api");
        action.setSendOnSuccess(false);
        action.setSendOnFailure(false);
        project.getPublishersList().add(action);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, build);
        // Plugin skipped — no [Last9] sending logs
        assertFalse(build.getLog(100).stream().anyMatch(line -> line.contains("[Last9] Sending")));
    }

    /**
     * Freestyle action with sendOnFailure=true proceeds (and gracefully handles missing credential).
     * The build result must remain FAILURE — plugin must not worsen it.
     */
    @Test
    public void freestyleAction_sendOnFailureTrue_proceedsButDoesNotWorsenResult() throws Exception {
        configureGlobalPlugin(null);

        FreeStyleProject project = j.createFreeStyleProject("test-freestyle-fail-send");
        project.getBuildersList().add(new hudson.tasks.Shell("exit 1"));
        Last9PostBuildAction action = new Last9PostBuildAction("payments-api");
        action.setSendOnSuccess(false);
        action.setSendOnFailure(true);
        project.getPublishersList().add(action);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        // Build is FAILURE (from shell exit 1), plugin ran but didn't make it worse
        j.assertBuildStatus(Result.FAILURE, build);
        j.assertLogContains("[Last9] Sending deployment marker", build);
    }

    /**
     * Build wrapper sends start before any build step and stop after — even on failure.
     * Neither event should fail the build.
     */
    @Test
    public void buildWrapper_sendsStartAndStop_neverFailsBuild() throws Exception {
        configureGlobalPlugin(null);

        FreeStyleProject project = j.createFreeStyleProject("test-wrapper-success");
        project.getBuildWrappersList().add(new Last9BuildWrapper("payments-api"));

        FreeStyleBuild build = j.buildAndAssertSuccess(project);
        // Both start and stop markers attempted — credential missing so both log warnings
        j.assertLogContains("[Last9] Sending deployment marker: deployment (start)", build);
        j.assertLogContains("[Last9] Sending deployment marker: deployment (stop)", build);
    }

    /**
     * Build wrapper tearDown runs even when the build fails.
     * The build result must remain FAILURE — wrapper must not change it.
     */
    @Test
    public void buildWrapper_tearDownRunsOnFailure() throws Exception {
        configureGlobalPlugin(null);

        FreeStyleProject project = j.createFreeStyleProject("test-wrapper-fail");
        project.getBuildersList().add(new hudson.tasks.Shell("exit 1"));
        project.getBuildWrappersList().add(new Last9BuildWrapper("payments-api"));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, build);
        // Start was sent before the failing step; stop was sent after
        j.assertLogContains("[Last9] Sending deployment marker: deployment (start)", build);
        j.assertLogContains("[Last9] Sending deployment marker: deployment (stop)", build);
    }

    // --- Helpers ---

    private void configureGlobalPlugin(String credentialId) {
        Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
        assertNotNull("Global config must be present in JenkinsRule", config);
        config.setOrgSlug("test-org");
        if (credentialId != null) {
            config.setCredentialId(credentialId);
        }
    }
}
