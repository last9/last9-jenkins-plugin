package io.last9.jenkins.plugins.last9.collect;

import hudson.EnvVars;
import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JenkinsContextCollectorTest {

    private final JenkinsContextCollector collector = new JenkinsContextCollector();

    @Test
    public void capturesAllFieldsWhenFullyPopulated() throws Exception {
        Run<?, ?> run = stubRun("payments-api", 42, "http://jenkins/job/payments-api/42/", Result.SUCCESS, "alice");

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertEquals("payments-api", attrs.get("jenkins.job_name"));
        assertEquals("42", attrs.get("jenkins.build_number"));
        assertEquals("http://jenkins/job/payments-api/42/", attrs.get("jenkins.build_url"));
        assertEquals("SUCCESS", attrs.get("jenkins.build_result"));
        assertEquals("alice", attrs.get("jenkins.build_user"));
        assertTrue(attrs.containsKey("jenkins.build_duration_ms"));
    }

    @Test
    public void omitsBuildResultWhenNull() throws Exception {
        Run<?, ?> run = stubRun("my-job", 1, "http://ci/1/", null, null);

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertFalse(attrs.containsKey("jenkins.build_result"));
    }

    @Test
    public void omitsBuildUrlWhenNull() throws Exception {
        Run<?, ?> run = stubRun("my-job", 1, null, Result.SUCCESS, null);

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertFalse(attrs.containsKey("jenkins.build_url"));
    }

    @Test
    public void omitsJobNameWhenParentNull() throws Exception {
        Run<?, ?> run = mock(Run.class);
        when(run.getParent()).thenReturn(null);
        when(run.getNumber()).thenReturn(5);
        when(run.getStartTimeInMillis()).thenReturn(System.currentTimeMillis() - 1000);
        when(run.getEnvironment(TaskListener.NULL)).thenReturn(new EnvVars());

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertFalse(attrs.containsKey("jenkins.job_name"));
        assertEquals("5", attrs.get("jenkins.build_number"));
    }

    @Test
    public void omitsBuildUserWhenNoCause() throws Exception {
        Run<?, ?> run = stubRun("my-job", 3, "http://ci/3/", Result.SUCCESS, null);

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertFalse(attrs.containsKey("jenkins.build_user"));
    }

    @Test
    public void omitsBuildUserWhenUserIdNull() throws Exception {
        Run<?, ?> run = mock(Run.class);
        Job<?, ?> parent = mock(Job.class);
        when(parent.getFullName()).thenReturn("my-job");
        doReturn(parent).when(run).getParent();
        when(run.getNumber()).thenReturn(7);
        when(run.getStartTimeInMillis()).thenReturn(System.currentTimeMillis() - 500);
        when(run.getEnvironment(TaskListener.NULL)).thenReturn(new EnvVars());

        Cause.UserIdCause userCause = mock(Cause.UserIdCause.class);
        when(userCause.getUserId()).thenReturn(null);
        when(run.getCause(Cause.UserIdCause.class)).thenReturn(userCause);

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertFalse(attrs.containsKey("jenkins.build_user"));
    }

    @Test
    public void capturesNodeNameFromEnvironment() throws Exception {
        Run<?, ?> run = mock(Run.class);
        Job<?, ?> parent = mock(Job.class);
        when(parent.getFullName()).thenReturn("my-job");
        doReturn(parent).when(run).getParent();
        when(run.getNumber()).thenReturn(10);
        when(run.getStartTimeInMillis()).thenReturn(System.currentTimeMillis() - 200);

        EnvVars env = new EnvVars();
        env.put("NODE_NAME", "agent-linux-01");
        when(run.getEnvironment(TaskListener.NULL)).thenReturn(env);

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertEquals("agent-linux-01", attrs.get("jenkins.node_name"));
    }

    @Test
    public void omitsNodeNameWhenEnvironmentThrows() throws Exception {
        Run<?, ?> run = mock(Run.class);
        Job<?, ?> parent = mock(Job.class);
        when(parent.getFullName()).thenReturn("my-job");
        doReturn(parent).when(run).getParent();
        when(run.getNumber()).thenReturn(11);
        when(run.getStartTimeInMillis()).thenReturn(System.currentTimeMillis() - 100);
        when(run.getEnvironment(TaskListener.NULL)).thenThrow(new RuntimeException("env unavailable"));

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        // Node name absent but everything else captured
        assertFalse(attrs.containsKey("jenkins.node_name"));
        assertEquals("my-job", attrs.get("jenkins.job_name"));
    }

    @Test
    public void buildDurationMsIsPositive() throws Exception {
        Run<?, ?> run = stubRun("my-job", 1, "http://ci/1/", Result.SUCCESS, null);

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        long duration = Long.parseLong(attrs.get("jenkins.build_duration_ms"));
        assertTrue("Duration should be non-negative", duration >= 0);
    }

    @Test
    public void returnsEmptyMapOnException() {
        Run<?, ?> run = mock(Run.class);
        when(run.getParent()).thenThrow(new RuntimeException("unexpected"));

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertNotNull(attrs);
        assertTrue(attrs.isEmpty());
    }

    // --- Helpers ---

    @SuppressWarnings("unchecked")
    private Run<?, ?> stubRun(String jobName, int buildNumber, String buildUrl,
                               Result result, String userId) throws Exception {
        Run<?, ?> run = mock(Run.class);

        Job<?, ?> parent = mock(Job.class);
        when(parent.getFullName()).thenReturn(jobName);
        doReturn(parent).when(run).getParent();
        when(run.getNumber()).thenReturn(buildNumber);
        when(run.getAbsoluteUrl()).thenReturn(buildUrl);
        when(run.getResult()).thenReturn(result);
        when(run.getStartTimeInMillis()).thenReturn(System.currentTimeMillis() - 1000);

        if (userId != null) {
            Cause.UserIdCause userCause = mock(Cause.UserIdCause.class);
            when(userCause.getUserId()).thenReturn(userId);
            when(run.getCause(Cause.UserIdCause.class)).thenReturn(userCause);
        } else {
            when(run.getCause(Cause.UserIdCause.class)).thenReturn(null);
        }

        when(run.getEnvironment(TaskListener.NULL)).thenReturn(new EnvVars());

        return run;
    }
}
