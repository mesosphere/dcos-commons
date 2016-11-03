package org.apache.mesos.scheduler.plan;

import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirement;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.mesos.specification.PodSpecification;
import org.apache.mesos.specification.TaskSpecification;

/**
 * An implementation of this interface should provide {@link Step}s based on {@link TaskSpecification}s.  This should
 * include logic detecting that a previously launched PodSpecification's specification has changed and so a relaunch of the PodSpecification is
 * needed.  In this case the {@link Step} should be initialized with the new {@link OfferRequirement} and be set to
 * Pending.  Conversely, an implementation of this interface should return {@link Step}s with a Complete status when no
 * PodSpecification change is detected for a PodSpecification.
 */
public interface StepFactory {
    Step getStep(PodSpecification podSpecification)
            throws Step.InvalidStepException, InvalidProtocolBufferException, InvalidRequirementException;
}
