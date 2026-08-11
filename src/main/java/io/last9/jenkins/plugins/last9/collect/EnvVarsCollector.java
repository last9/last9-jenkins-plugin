package io.last9.jenkins.plugins.last9.collect;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.last9.jenkins.plugins.last9.Last9GlobalConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads the environment variable allowlist from {@link Last9GlobalConfiguration} and emits
 * {@code env_<lowercase_name>} attributes for each variable that is present in the build env.
 */
public class EnvVarsCollector implements AttributeCollector {

    private static final Logger LOGGER = Logger.getLogger(EnvVarsCollector.class.getName());

    /** Explicit allowlist; when non-null, bypasses the global config (used in tests). */
    private final List<String> fixedAllowlist;

    public EnvVarsCollector() {
        this.fixedAllowlist = null;
    }

    /** Package-private constructor that bypasses global config — intended for unit tests. */
    EnvVarsCollector(List<String> allowlist) {
        this.fixedAllowlist = allowlist;
    }

    @Override
    public Map<String, String> collect(Run<?, ?> run, TaskListener listener) {
        Map<String, String> attrs = new LinkedHashMap<>();

        try {
            List<String> allowlist;
            if (fixedAllowlist != null) {
                allowlist = fixedAllowlist;
            } else {
                Last9GlobalConfiguration config = Last9GlobalConfiguration.get();
                if (config == null) {
                    return attrs;
                }
                allowlist = config.getAdditionalEnvVarsList();
            }
            if (allowlist == null || allowlist.isEmpty()) {
                return attrs;
            }

            EnvVars env = run.getEnvironment(TaskListener.NULL);

            for (String varName : allowlist) {
                if (varName == null || varName.isBlank()) {
                    continue;
                }
                String value = env.get(varName.trim());
                if (value != null) {
                    attrs.put("env_" + varName.trim().toLowerCase(), value);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error collecting environment variables", e);
        }

        return attrs;
    }
}
