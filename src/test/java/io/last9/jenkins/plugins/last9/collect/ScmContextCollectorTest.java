package io.last9.jenkins.plugins.last9.collect;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ScmContextCollectorTest {

    private final ScmContextCollector collector = new ScmContextCollector();

    @Test
    public void capturesAllGitVariablesWhenPresent() throws Exception {
        Run<?, ?> run = mock(Run.class);
        TaskListener listener = mock(TaskListener.class);

        EnvVars env = new EnvVars();
        env.put("GIT_COMMIT", "abc123def456");
        env.put("GIT_BRANCH", "origin/main");
        env.put("GIT_URL", "https://github.com/acme/payments-api.git");
        env.put("GIT_AUTHOR_NAME", "Alice");
        when(run.getEnvironment(listener)).thenReturn(env);

        Map<String, String> attrs = collector.collect(run, listener);

        assertEquals("abc123def456", attrs.get("scm.commit_sha"));
        assertEquals("origin/main", attrs.get("scm.branch"));
        assertEquals("https://github.com/acme/payments-api.git", attrs.get("scm.url"));
        assertEquals("Alice", attrs.get("scm.author"));
    }

    @Test
    public void omitsAbsentGitVariables() throws Exception {
        Run<?, ?> run = mock(Run.class);
        TaskListener listener = mock(TaskListener.class);

        EnvVars env = new EnvVars();
        env.put("GIT_COMMIT", "abc123");
        // GIT_BRANCH, GIT_URL, GIT_AUTHOR_NAME absent
        when(run.getEnvironment(listener)).thenReturn(env);

        Map<String, String> attrs = collector.collect(run, listener);

        assertTrue(attrs.containsKey("scm.commit_sha"));
        assertFalse(attrs.containsKey("scm.branch"));
        assertFalse(attrs.containsKey("scm.url"));
        assertFalse(attrs.containsKey("scm.author"));
    }

    @Test
    public void omitsBlankGitVariables() throws Exception {
        Run<?, ?> run = mock(Run.class);
        TaskListener listener = mock(TaskListener.class);

        EnvVars env = new EnvVars();
        env.put("GIT_COMMIT", "   ");
        env.put("GIT_BRANCH", "");
        when(run.getEnvironment(listener)).thenReturn(env);

        Map<String, String> attrs = collector.collect(run, listener);

        assertFalse(attrs.containsKey("scm.commit_sha"));
        assertFalse(attrs.containsKey("scm.branch"));
    }

    @Test
    public void returnsEmptyMapWhenNoGitVarsSet() throws Exception {
        Run<?, ?> run = mock(Run.class);
        TaskListener listener = mock(TaskListener.class);
        when(run.getEnvironment(listener)).thenReturn(new EnvVars());

        Map<String, String> attrs = collector.collect(run, listener);

        assertTrue(attrs.isEmpty());
    }

    @Test
    public void degradesGracefullyWhenGetEnvironmentThrows() throws Exception {
        Run<?, ?> run = mock(Run.class);
        TaskListener listener = mock(TaskListener.class);
        when(run.getEnvironment(listener)).thenThrow(new RuntimeException("env unavailable"));

        Map<String, String> attrs = collector.collect(run, listener);

        // Should not throw — returns empty map
        assertNotNull(attrs);
        assertTrue(attrs.isEmpty());
    }

    @Test
    public void degradesGracefullyWhenGitPluginNotInstalled() throws Exception {
        Run<?, ?> run = mock(Run.class);
        TaskListener listener = mock(TaskListener.class);
        when(run.getEnvironment(listener)).thenThrow(new NoClassDefFoundError("hudson/plugins/git/GitSCM"));

        Map<String, String> attrs = collector.collect(run, listener);

        assertNotNull(attrs);
        assertTrue(attrs.isEmpty());
    }
}
