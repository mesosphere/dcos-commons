package org.apache.mesos.offer.constrain;

import java.util.Collection;

import org.apache.mesos.Protos.TaskInfo;

/**
 * Directly returns the provided rule when invoked.
 */
public class PassthroughGenerator implements PlacementRuleGenerator {
    private final PlacementRule ruleToReturn;

    public PassthroughGenerator(PlacementRule ruleToReturn) {
        this.ruleToReturn = ruleToReturn;
    }

    @Override
    public PlacementRule generate(Collection<TaskInfo> tasks) {
        return ruleToReturn;
    }

    @Override
    public String toString() {
        return String.format("PassthroughGenerator{rule=%s}", ruleToReturn);
    }
}
