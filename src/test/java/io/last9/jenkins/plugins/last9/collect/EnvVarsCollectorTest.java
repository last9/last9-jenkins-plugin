package io.last9.jenkins.plugins.last9.collect;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EnvVarsCollectorTest {

    @Test
    public void emitsAllowlistedEnvVarsWithEnvPrefix() throws Exception {
        EnvVars env = new EnvVars();
        env.put("DEPLOY_VERSION", "2.1.0");
        env.put("REGION", "us-east-1");

        Run<?, ?> run = stubRunWithEnv(env);
        EnvVarsCollector collector = new EnvVarsCollector(List.of("DEPLOY_VERSION", "REGION"));

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertEquals("2.1.0", attrs.get("env_deploy_version"));
        assertEquals("us-east-1", attrs.get("env_region"));
    }

    @Test
    public void omitsVarsThatAreNotPresentInBuildEnv() throws Exception {
        EnvVars env = new EnvVars();
        env.put("PRESENT", "yes");

        Run<?, ?> run = stubRunWithEnv(env);
        EnvVarsCollector collector = new EnvVarsCollector(List.of("PRESENT", "ABSENT"));

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertEquals("yes", attrs.get("env_present"));
        assertFalse(attrs.containsKey("env_absent"));
    }

    @Test
    public void returnsEmptyMapWhenAllowlistIsEmpty() throws Exception {
        Run<?, ?> run = stubRunWithEnv(new EnvVars());
        EnvVarsCollector collector = new EnvVarsCollector(List.of());

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertTrue(attrs.isEmpty());
    }

    @Test
    public void lowercasesEnvVarNamesInKey() throws Exception {
        EnvVars env = new EnvVars();
        env.put("MY_VAR", "hello");

        Run<?, ?> run = stubRunWithEnv(env);
        EnvVarsCollector collector = new EnvVarsCollector(List.of("MY_VAR"));

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertEquals("hello", attrs.get("env_my_var"));
        assertFalse(attrs.containsKey("env_MY_VAR"));
    }

    @Test
    public void skipsBlankEntriesInAllowlist() throws Exception {
        EnvVars env = new EnvVars();
        env.put("REAL", "value");

        Run<?, ?> run = stubRunWithEnv(env);
        EnvVarsCollector collector = new EnvVarsCollector(List.of("REAL", "  ", ""));

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertEquals(1, attrs.size());
        assertEquals("value", attrs.get("env_real"));
    }

    @Test
    public void returnsEmptyMapWhenNoConfigAndNoFixedAllowlist() {
        // Default constructor uses global config; with no Jenkins instance, get() returns null
        EnvVarsCollector collector = new EnvVarsCollector();
        Run<?, ?> run = mock(Run.class);

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertNotNull(attrs);
        assertTrue(attrs.isEmpty());
    }

    // --- Helpers ---

    private Run<?, ?> stubRunWithEnv(EnvVars env) throws Exception {
        Run<?, ?> run = mock(Run.class);
        when(run.getEnvironment(TaskListener.NULL)).thenReturn(env);
        return run;
    }
}
