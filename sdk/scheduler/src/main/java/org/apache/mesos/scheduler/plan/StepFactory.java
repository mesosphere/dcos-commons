package org.apache.mesos.scheduler.plan;

import org.apache.mesos.offer.OfferRequirement;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.mesos.specification.Pod;
import org.apache.mesos.specification.TaskSpecification;

/**
 * An implementation of this interface should provide {@link Step}s based on {@link TaskSpecification}s.  This should
 * include logic detecting that a previously launched Pod's specification has changed and so a relaunch of the Pod is
 * needed.  In this case the {@link Step} should be initialized with the new {@link OfferRequirement} and be set to
 * Pending.  Conversely, an implementation of this interface should return {@link Step}s with a Complete status when no
 * PodSpecification change is detected for a Pod.
 */
public interface StepFactory {
    Step getStep(Pod pod)
            throws Step.InvalidStepException, InvalidProtocolBufferException;
}
