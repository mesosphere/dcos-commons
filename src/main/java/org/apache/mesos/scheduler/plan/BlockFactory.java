package org.apache.mesos.scheduler.plan;

import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.specification.TaskSpecification;

/**
 * Created by gabriel on 8/27/16.
 */
public interface BlockFactory {
    Block getBlock(TaskSpecification taskSpecification) throws InvalidRequirementException;
}
