package org.apache.mesos.scheduler.plan;

import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.specification.TaskSpecification;

/**
 * An implementation of this interface should provide Blocks based on TaskSpecifications.  This should include logic
 * detecting that a previously launched Task's specification has changed and so a relaunch of the Task is needed.  In
 * this case the Block should be initialized with the new OfferRequirement and be set to Pending.  Conversely, an
 * implementation of this interface should return Blocks with a Complete status when no TaskSpecification change is
 * detected for a Task.
 */
public interface BlockFactory {
    Block getBlock(String taskType, TaskSpecification taskSpecification)
            throws InvalidRequirementException;
}
