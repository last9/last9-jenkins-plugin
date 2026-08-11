package io.last9.jenkins.plugins.last9.deployment;

import hudson.model.Run;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DeploymentWindowTrackerTest {

    private final DeploymentWindowTracker tracker = new DeploymentWindowTracker();

    @Test
    public void startAttachesActionAndReturnsNonNullId() {
        Run<?, ?> run = mock(Run.class);

        String id = tracker.start(run);

        assertNotNull(id);
        assertFalse(id.isBlank());
        verify(run).addAction(any(DeploymentWindowAction.class));
    }

    @Test
    public void startGeneratesUniqueIds() {
        Run<?, ?> run1 = mock(Run.class);
        Run<?, ?> run2 = mock(Run.class);

        String id1 = tracker.start(run1);
        String id2 = tracker.start(run2);

        assertNotEquals(id1, id2);
    }

    @Test
    public void stopReturnsDurationWhenActionPresent() throws Exception {
        Run<?, ?> run = mock(Run.class);
        long startMs = System.currentTimeMillis() - 500;
        DeploymentWindowAction action = new DeploymentWindowAction("test-id", startMs);
        when(run.getAction(DeploymentWindowAction.class)).thenReturn(action);

        long duration = tracker.stop(run);

        assertTrue("Duration should be non-negative", duration >= 0);
        // Should be roughly 500ms; allow generous tolerance for CI slowness
        assertTrue("Duration should be < 10s for a 500ms window", duration < 10_000);
    }

    @Test
    public void stopReturnsNegativeOneWhenNoAction() {
        Run<?, ?> run = mock(Run.class);
        when(run.getAction(DeploymentWindowAction.class)).thenReturn(null);

        long duration = tracker.stop(run);

        assertEquals(-1L, duration);
    }

    @Test
    public void getDeploymentIdReturnsStoredId() {
        Run<?, ?> run = mock(Run.class);
        DeploymentWindowAction action = new DeploymentWindowAction("my-deploy-id", 12345L);
        when(run.getAction(DeploymentWindowAction.class)).thenReturn(action);

        String id = tracker.getDeploymentId(run);

        assertEquals("my-deploy-id", id);
    }

    @Test
    public void startReusesExistingActionOnDuplicateStart() {
        Run<?, ?> run = mock(Run.class);
        DeploymentWindowAction existing = new DeploymentWindowAction("existing-id", 1000L);
        when(run.getAction(DeploymentWindowAction.class)).thenReturn(existing);

        String id = tracker.start(run);

        assertEquals("existing-id", id);
        verify(run, never()).addAction(any(DeploymentWindowAction.class));
    }

    @Test
    public void getDeploymentIdReturnsNullWhenNoAction() {
        Run<?, ?> run = mock(Run.class);
        when(run.getAction(DeploymentWindowAction.class)).thenReturn(null);

        assertNull(tracker.getDeploymentId(run));
    }
}
