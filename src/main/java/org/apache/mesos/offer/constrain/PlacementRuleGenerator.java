package org.apache.mesos.offer.constrain;

import java.util.Collection;

import org.apache.mesos.Protos.TaskInfo;

/**
 * Dynamically creates {@link PlacementRule}s which depend on the current deployed state of the
 * system.
 */
public interface PlacementRuleGenerator {

    /**
     * Returns a new {@link PlacementRule} which defines where a task may be placed given the
     * current deployed state of the system. The returned {@link PlacementRule} may then be tested
     * against one or more {@link Offer}s to find offered resources which match the constraints.
     *
     * @param tasks all currently deployed tasks, against which the returned value is generated
     */
    public PlacementRule generate(Collection<TaskInfo> tasks);
}
