package io.last9.jenkins.plugins.last9.util;

import hudson.EnvVars;
import hudson.model.ParametersAction;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.last9.jenkins.plugins.last9.Last9GlobalConfiguration;
import io.last9.jenkins.plugins.last9.model.RoutingProfile;

/**
 * Resolves Last9 connection settings from step overrides, routing profiles,
 * build parameters, environment variables, and global defaults.
 *
 * <p>Priority for each field (org slug, credential ID, API base URL):
 * <ol>
 *   <li>Direct literal on the step</li>
 *   <li>Named build parameter</li>
 *   <li>Named environment variable</li>
 *   <li>Selected routing profile (global named profile)</li>
 *   <li>Global default</li>
 * </ol>
 *
 * Routing profile name resolution (before profile fields apply):
 * direct profile name → profile param → profile env var.
 */
public final class ConfigResolver {

    private ConfigResolver() {
    }

    public static ResolvedConnection resolveConnection(
            ConnectionOverrides overrides, Run<?, ?> run, EnvVars env) {
        Last9GlobalConfiguration config = Last9GlobalConfiguration.get();

        RoutingProfile profile = resolveRoutingProfile(overrides, run, env, config);

        String profileOrg = profile != null ? profile.getOrgSlug() : null;
        String profileCred = profile != null ? profile.getCredentialId() : null;
        String profileApi = profile != null ? profile.getApiBaseUrl() : null;

        String globalOrg = config != null ? config.getOrgSlug() : null;
        String globalCred = config != null ? config.getCredentialId() : null;
        String globalApi = config != null ? config.getApiBaseUrl() : null;

        String orgSlug = resolveField(
            overrides.getOrgSlug(), overrides.getOrgSlugParam(), overrides.getOrgSlugEnvVar(),
            run, env, profileOrg, globalOrg);
        String credentialId = resolveField(
            overrides.getCredentialId(), overrides.getCredentialIdParam(), overrides.getCredentialIdEnvVar(),
            run, env, profileCred, globalCred);
        String apiBaseUrl = normalizeApiBaseUrl(resolveField(
            overrides.getApiBaseUrl(), overrides.getApiBaseUrlParam(), overrides.getApiBaseUrlEnvVar(),
            run, env, profileApi, globalApi));

        return new ResolvedConnection(orgSlug, credentialId, apiBaseUrl);
    }

    /**
     * @deprecated Use {@link #resolveConnection(ConnectionOverrides, Run, EnvVars)}.
     */
    @Deprecated
    public static String resolveCredentialId(String directValue, String paramName, Run<?, ?> run) {
        ConnectionOverrides overrides = new ConnectionOverrides();
        overrides.setCredentialId(directValue);
        overrides.setCredentialIdParam(paramName);
        return resolveConnection(overrides, run, null).credentialId();
    }

    /**
     * @deprecated Use {@link #resolveConnection(ConnectionOverrides, Run, EnvVars)}.
     */
    @Deprecated
    public static String resolveOrgSlug(String directValue, String paramName, Run<?, ?> run) {
        ConnectionOverrides overrides = new ConnectionOverrides();
        overrides.setOrgSlug(directValue);
        overrides.setOrgSlugParam(paramName);
        return resolveConnection(overrides, run, null).orgSlug();
    }

    /**
     * Returns the routing profile name requested by the step (direct, param, or env var),
     * or {@code null} if no profile was requested.
     */
    public static String resolveRoutingProfileName(
            ConnectionOverrides overrides, Run<?, ?> run, EnvVars env) {
        return resolveProfileName(
            overrides.getRoutingProfile(),
            overrides.getRoutingProfileParam(),
            overrides.getRoutingProfileEnvVar(),
            run,
            env
        );
    }

    private static RoutingProfile resolveRoutingProfile(
            ConnectionOverrides overrides, Run<?, ?> run, EnvVars env,
            Last9GlobalConfiguration config) {
        if (config == null) {
            return null;
        }

        String profileName = resolveProfileName(
            overrides.getRoutingProfile(),
            overrides.getRoutingProfileParam(),
            overrides.getRoutingProfileEnvVar(),
            run,
            env
        );
        if (profileName == null) {
            return null;
        }
        return config.findRoutingProfile(profileName);
    }

    private static String resolveProfileName(
            String direct, String paramName, String envVarName, Run<?, ?> run, EnvVars env) {
        String fromDirect = trimToNull(direct);
        if (fromDirect != null) {
            return fromDirect;
        }

        String fromParam = trimToNull(paramValue(run, paramName));
        if (fromParam != null) {
            return fromParam;
        }

        return trimToNull(envValue(env, envVarName));
    }

    private static String resolveField(
            String direct, String paramName, String envVarName,
            Run<?, ?> run, EnvVars env,
            String profileDefault, String globalDefault) {
        String fromDirect = trimToNull(direct);
        if (fromDirect != null) {
            return fromDirect;
        }

        String fromParam = trimToNull(paramValue(run, paramName));
        if (fromParam != null) {
            return fromParam;
        }

        String fromEnv = trimToNull(envValue(env, envVarName));
        if (fromEnv != null) {
            return fromEnv;
        }

        String fromProfile = trimToNull(profileDefault);
        if (fromProfile != null) {
            return fromProfile;
        }

        return trimToNull(globalDefault);
    }

    private static String paramValue(Run<?, ?> run, String paramName) {
        if (paramName == null || paramName.isBlank() || run == null) {
            return null;
        }
        ParametersAction action = run.getAction(ParametersAction.class);
        if (action == null) {
            return null;
        }
        ParameterValue pv = action.getParameter(paramName.trim());
        if (pv == null) {
            return null;
        }
        Object value = pv.getValue();
        return value != null ? value.toString() : null;
    }

    private static String envValue(EnvVars env, String envVarName) {
        if (envVarName == null || envVarName.isBlank()) {
            return null;
        }
        if (env != null) {
            return env.get(envVarName.trim());
        }
        return null;
    }

    /**
     * Loads environment variables from the build when not already provided.
     */
    public static EnvVars environmentOrLoad(Run<?, ?> run, TaskListener listener, EnvVars env) {
        if (env != null) {
            return env;
        }
        if (run == null) {
            return new EnvVars();
        }
        try {
            return run.getEnvironment(listener);
        } catch (Exception e) {
            return new EnvVars();
        }
    }

    public static String normalizeApiBaseUrl(String apiBaseUrl) {
        String trimmed = trimToNull(apiBaseUrl);
        if (trimmed == null) {
            return Last9GlobalConfiguration.DEFAULT_API_BASE_URL;
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
