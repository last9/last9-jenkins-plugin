package io.last9.jenkins.plugins.last9.collect;

import hudson.model.Cause;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collects Jenkins build metadata: job name, build number, URL, result, duration, user.
 */
public class JenkinsContextCollector implements AttributeCollector {

    private static final Logger LOGGER = Logger.getLogger(JenkinsContextCollector.class.getName());

    @Override
    public Map<String, String> collect(Run<?, ?> run, TaskListener listener) {
        Map<String, String> attrs = new LinkedHashMap<>();

        try {
            var parent = run.getParent();
            if (parent != null) {
                attrs.put("jenkins.job_name", parent.getFullName());
            }
            attrs.put("jenkins.build_number", String.valueOf(run.getNumber()));

            String buildUrl = run.getAbsoluteUrl();
            if (buildUrl != null) {
                attrs.put("jenkins.build_url", buildUrl);
            }

            var result = run.getResult();
            if (result != null) {
                attrs.put("jenkins.build_result", result.toString());
            }

            long durationMs = System.currentTimeMillis() - run.getStartTimeInMillis();
            attrs.put("jenkins.build_duration_ms", String.valueOf(durationMs));

            Cause.UserIdCause userCause = run.getCause(Cause.UserIdCause.class);
            if (userCause != null) {
                String userId = userCause.getUserId();
                if (userId != null) {
                    attrs.put("jenkins.build_user", userId);
                }
            }

            String nodeName = nodeNameFrom(run);
            if (nodeName != null) {
                attrs.put("jenkins.node_name", nodeName);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error collecting Jenkins context", e);
        }

        return attrs;
    }

    private String nodeNameFrom(Run<?, ?> run) {
        try {
            var env = run.getEnvironment(TaskListener.NULL);
            return env.get("NODE_NAME");
        } catch (Exception e) {
            return null;
        }
    }
}
