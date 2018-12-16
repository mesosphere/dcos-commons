package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;

import java.util.Optional;

/**
 * This interface allows for the specification of custom recovery logic when presented with a stopped pod encapsulated
 * by the PodInstanceRequirement.
 */
public interface RecoveryPlanOverrider {

  /**
   * Returns a phase to be used when recovering the pod as described in the provided {@link PodLaunch}, or an empty
   * Optional if a default recovery phase should be used instead.
   *
   * @param podInstanceRequirement specifies the pod to be recovered, and the type of recovery
   */
  Optional<Phase> override(PodInstanceRequirement podInstanceRequirement);
}
