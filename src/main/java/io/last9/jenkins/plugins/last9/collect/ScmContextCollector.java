package io.last9.jenkins.plugins.last9.collect;

import hudson.model.Run;
import hudson.model.TaskListener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collects SCM context (commit SHA, branch) from the Git plugin if available.
 * Degrades gracefully if the Git plugin is not installed.
 */
public class ScmContextCollector implements AttributeCollector {

    private static final Logger LOGGER = Logger.getLogger(ScmContextCollector.class.getName());

    @Override
    public Map<String, String> collect(Run<?, ?> run, TaskListener listener) {
        Map<String, String> attrs = new LinkedHashMap<>();

        try {
            // Try to get SCM info from environment variables (set by Git plugin)
            var env = run.getEnvironment(listener);

            String commitSha = env.get("GIT_COMMIT");
            if (commitSha != null && !commitSha.isBlank()) {
                attrs.put("scm.commit_sha", commitSha);
            }

            String branch = env.get("GIT_BRANCH");
            if (branch != null && !branch.isBlank()) {
                attrs.put("scm.branch", branch);
            }

            String gitUrl = env.get("GIT_URL");
            if (gitUrl != null && !gitUrl.isBlank()) {
                attrs.put("scm.url", gitUrl);
            }

            String gitAuthor = env.get("GIT_AUTHOR_NAME");
            if (gitAuthor != null && !gitAuthor.isBlank()) {
                attrs.put("scm.author", gitAuthor);
            }
        } catch (NoClassDefFoundError e) {
            // Git plugin not installed — degrade gracefully
            LOGGER.log(Level.FINE, "Git plugin not available, skipping SCM context collection");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error collecting SCM context", e);
        }

        return attrs;
    }
}
