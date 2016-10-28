package org.apache.mesos.offer.constrain;

import java.util.Collection;

import org.apache.mesos.Protos.TaskInfo;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
//import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Dynamically creates {@link PlacementRule}s which depend on the current deployed state of the
 * system.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public interface PlacementRuleGenerator {

    /**
     * Returns a new {@link PlacementRule} which defines where a task may be placed given the
     * current deployed state of the system. The returned {@link PlacementRule} may then be tested
     * against one or more {@link Offer}s to find offered resources which match the constraints.
     *
     * @param tasks all currently deployed tasks, against which the returned value is generated
     */
    public PlacementRule generate(Collection<TaskInfo> tasks);

    /**
     * Must be explicitly implemented by all PlacementRuleGenerators.
     */
    public boolean equals(Object o);
}
