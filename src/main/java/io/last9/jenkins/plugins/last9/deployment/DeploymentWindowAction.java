package io.last9.jenkins.plugins.last9.deployment;

import hudson.model.InvisibleAction;

/**
 * Invisible build action used to record the deployment window ID and start timestamp.
 * Attached to the run when the START marker is sent; read back when the STOP marker fires.
 */
public class DeploymentWindowAction extends InvisibleAction {

    private final String deploymentId;
    private final long startTimeMs;

    public DeploymentWindowAction(String deploymentId, long startTimeMs) {
        this.deploymentId = deploymentId;
        this.startTimeMs = startTimeMs;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }
}
