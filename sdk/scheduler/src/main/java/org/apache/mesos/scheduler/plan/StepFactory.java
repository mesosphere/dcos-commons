package org.apache.mesos.scheduler.plan;

import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.specification.PodInstance;

import java.util.List;

/**
 * An implementation of this interface should provide {@link Step}s based on {@link TaskSpecification}s.  This should
 * include logic detecting that a previously launched Task's specification has changed and so a relaunch of the Task is
 * needed.  In this case the {@link Step} should be initialized with the new {@link OfferRequirement} and be set to
 * Pending.  Conversely, an implementation of this interface should return {@link Step}s with a Complete status when no
 * TaskSpecification change is detected for a Task.
 */
public interface StepFactory {
    Step getStep(PodInstance podInstance, List<String> tasksToLaunch)
            throws Step.InvalidStepException, InvalidRequirementException;
}
