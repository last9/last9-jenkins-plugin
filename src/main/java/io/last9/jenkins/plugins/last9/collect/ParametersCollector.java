package io.last9.jenkins.plugins.last9.collect;

import hudson.model.ParametersAction;
import hudson.model.ParameterValue;
import hudson.model.PasswordParameterValue;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.last9.jenkins.plugins.last9.Last9GlobalConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collects allowlisted build parameters and emits them as
 * {@code build_param_<sanitized_name>} attributes.
 *
 * <p>Only parameter names listed in global config are exported. Password parameters
 * are never exported even when allowlisted.
 */
public class ParametersCollector implements AttributeCollector {

    private static final Logger LOGGER = Logger.getLogger(ParametersCollector.class.getName());

    /** Explicit allowlist; when non-null, bypasses the global config (used in tests). */
    private final List<String> fixedAllowlist;

    public ParametersCollector() {
        this.fixedAllowlist = null;
    }

    /** Package-private constructor that bypasses global config — intended for unit tests. */
    ParametersCollector(List<String> allowlist) {
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
                allowlist = config.getAdditionalBuildParamsList();
            }
            if (allowlist == null || allowlist.isEmpty()) {
                return attrs;
            }

            ParametersAction action = run.getAction(ParametersAction.class);
            if (action == null) {
                return attrs;
            }
            List<ParameterValue> params = action.getParameters();
            if (params == null) {
                return attrs;
            }

            for (String allowedName : allowlist) {
                if (allowedName == null || allowedName.isBlank()) {
                    continue;
                }
                ParameterValue pv = action.getParameter(allowedName.trim());
                if (pv == null || pv instanceof PasswordParameterValue) {
                    continue;
                }
                Object value = pv.getValue();
                if (value == null) {
                    continue;
                }
                String key = "build_param_" + sanitize(pv.getName());
                attrs.put(key, value.toString());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error collecting build parameters", e);
        }

        return attrs;
    }

    /**
     * Lower-cases the name and replaces any character that is not a letter, digit,
     * or underscore with an underscore.
     */
    static String sanitize(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }
}
