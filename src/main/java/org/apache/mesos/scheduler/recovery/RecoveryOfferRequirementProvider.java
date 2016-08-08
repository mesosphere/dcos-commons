package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.OfferRequirement;

import java.util.List;

/**
 * Implementations of this interface provide OfferRequirement objects when presented with Tasks which have failed
 * permanently or transiently.
 */
public interface RecoveryOfferRequirementProvider {
    List<OfferRequirement> getTransientRecoveryOfferRequirements(List<TaskInfo> stoppedTasks);
    List<OfferRequirement> getPermanentRecoveryOfferRequirements(List<TaskInfo> failedTasks);
}
