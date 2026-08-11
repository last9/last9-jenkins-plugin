package io.last9.jenkins.plugins.last9.deployment;

import hudson.model.Run;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Manages the lifecycle of a deployment window attached to a Jenkins build.
 *
 * <p>On {@link #start(Run)} a {@link DeploymentWindowAction} is added to the run,
 * recording a unique {@code deployment_id} and the wall-clock start time.
 *
 * <p>On {@link #stop(Run)} the action is retrieved and the elapsed
 * {@code deployment_window_duration_ms} is computed.
 */
public class DeploymentWindowTracker {

    private static final Logger LOGGER = Logger.getLogger(DeploymentWindowTracker.class.getName());

    /**
     * Attaches a new {@link DeploymentWindowAction} to the run and returns the generated
     * deployment ID so callers can include it as an event attribute.
     */
    public String start(Run<?, ?> run) {
        DeploymentWindowAction existing = run.getAction(DeploymentWindowAction.class);
        if (existing != null) {
            return existing.getDeploymentId();
        }
        String deploymentId = UUID.randomUUID().toString();
        run.addAction(new DeploymentWindowAction(deploymentId, System.currentTimeMillis()));
        return deploymentId;
    }

    /**
     * Reads the previously stored {@link DeploymentWindowAction} from the run and returns
     * the elapsed duration in milliseconds, or {@code -1} if no start action is found.
     */
    public long stop(Run<?, ?> run) {
        DeploymentWindowAction action = run.getAction(DeploymentWindowAction.class);
        if (action == null) {
            LOGGER.warning("DeploymentWindowAction not found on run " + run.getFullDisplayName()
                + "; cannot compute deployment_window_duration_ms");
            return -1L;
        }
        return System.currentTimeMillis() - action.getStartTimeMs();
    }

    /**
     * Returns the deployment ID recorded at start, or {@code null} if not found.
     */
    public String getDeploymentId(Run<?, ?> run) {
        DeploymentWindowAction action = run.getAction(DeploymentWindowAction.class);
        return action != null ? action.getDeploymentId() : null;
    }
}
