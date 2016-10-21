package org.apache.mesos.scheduler.plan;

import org.apache.mesos.specification.TaskSpecification;

import java.util.List;

/**
 * An implementation of this interface should provide Blocks based on TaskSpecifications.  This should include logic
 * detecting that a previously launched Task's specification has changed and so a relaunch of the Task is needed.  In
 * this case the Block should be initiliazed with the new OfferRequirement and be set to Pending.  Conversely, an
 * implementation of this interface should return Blocks with a Complete status when no TaskSpecification change is
 * detected for a Task.
 */
public interface BlockFactory {
    Block getBlock(TaskSpecification taskSpecification) throws Block.InvalidBlockException;
    List<Block> getBlocks(List<TaskSpecification> taskSpecifications) throws Block.InvalidBlockException;
}
