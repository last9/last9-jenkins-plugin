package io.last9.jenkins.plugins.last9.collect;

import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.PasswordParameterValue;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ParametersCollectorTest {

    @Test
    public void emitsAllowlistedParametersWithBuildParamPrefix() {
        ParametersCollector collector = new ParametersCollector(List.of("DEPLOY_ENV", "VERSION"));
        Run<?, ?> run = runWithParams(
            new StringParameterValue("DEPLOY_ENV", "production"),
            new StringParameterValue("VERSION", "1.2.3"),
            new StringParameterValue("OTHER", "ignored")
        );

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertEquals("production", attrs.get("build_param_deploy_env"));
        assertEquals("1.2.3", attrs.get("build_param_version"));
        assertFalse(attrs.containsKey("build_param_other"));
    }

    @Test
    public void returnsEmptyWhenAllowlistEmpty() {
        ParametersCollector collector = new ParametersCollector(List.of());
        Run<?, ?> run = runWithParams(new StringParameterValue("DEPLOY_ENV", "production"));

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertTrue(attrs.isEmpty());
    }

    @Test
    public void sanitizesParamNamesWithSpecialChars() {
        ParametersCollector collector = new ParametersCollector(List.of("MY-PARAM.NAME"));
        Run<?, ?> run = runWithParams(new StringParameterValue("MY-PARAM.NAME", "val"));

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertEquals("val", attrs.get("build_param_my_param_name"));
    }

    @Test
    public void returnsEmptyMapWhenNoParametersAction() {
        ParametersCollector collector = new ParametersCollector(List.of("DEPLOY_ENV"));
        Run<?, ?> run = mock(Run.class);
        when(run.getAction(ParametersAction.class)).thenReturn(null);

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertNotNull(attrs);
        assertTrue(attrs.isEmpty());
    }

    @Test
    public void skipsPasswordParametersEvenWhenAllowlisted() {
        ParametersCollector collector = new ParametersCollector(List.of("DEPLOY_ENV", "SECRET_PARAM"));
        Run<?, ?> run = runWithParams(
            new StringParameterValue("DEPLOY_ENV", "production"),
            new PasswordParameterValue("SECRET_PARAM", "must-not-export", "description")
        );

        Map<String, String> attrs = collector.collect(run, mock(TaskListener.class));

        assertEquals("production", attrs.get("build_param_deploy_env"));
        assertFalse(attrs.containsKey("build_param_secret_param"));
    }

    @Test
    public void sanitizeLowercasesAndReplacesNonAlphanumeric() {
        assertEquals("hello_world", ParametersCollector.sanitize("Hello World"));
        assertEquals("a_b_c", ParametersCollector.sanitize("A-B.C"));
        assertEquals("my_param_123", ParametersCollector.sanitize("MY_PARAM_123"));
        assertEquals("___", ParametersCollector.sanitize("!@#"));
    }

    private Run<?, ?> runWithParams(ParameterValue... values) {
        ParametersAction action = mock(ParametersAction.class);
        when(action.getParameters()).thenReturn(List.of(values));
        for (ParameterValue value : values) {
            when(action.getParameter(value.getName())).thenReturn(value);
        }

        Run<?, ?> run = mock(Run.class);
        when(run.getAction(ParametersAction.class)).thenReturn(action);
        return run;
    }
}
