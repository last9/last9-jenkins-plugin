package io.last9.jenkins.plugins.last9.collect;

import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Map;

/**
 * Strategy interface for collecting contextual attributes.
 * Composable: multiple collectors merge their results.
 */
public interface AttributeCollector {

    /**
     * Collect attributes from the current build context.
     *
     * @return key-value pairs to include as event attributes
     */
    Map<String, String> collect(Run<?, ?> run, TaskListener listener);
}
