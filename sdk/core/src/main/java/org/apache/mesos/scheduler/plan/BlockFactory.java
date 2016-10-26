package org.apache.mesos.scheduler.plan;

import org.apache.mesos.offer.OfferRequirement;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.mesos.specification.TaskSpecification;

/**
 * An implementation of this interface should provide {@link Block}s based on {@link TaskSpecification}s.  This should
 * include logic detecting that a previously launched Task's specification has changed and so a relaunch of the Task is
 * needed.  In this case the {@link Block} should be initialized with the new {@link OfferRequirement} and be set to
 * Pending.  Conversely, an implementation of this interface should return {@link Block}s with a Complete status when no
 * TaskSpecification change is detected for a Task.
 */
public interface BlockFactory {
    Block getBlock(TaskSpecification taskSpecification)
            throws Block.InvalidBlockException, InvalidProtocolBufferException;
}
