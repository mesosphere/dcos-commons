package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;

import java.util.Optional;

/**
 * This interface allows for the specification of custom recovery logic when presented with a stopped pod encapsulated
 * by the PodInstanceRequirement.
 */
public interface RecoveryPlanOverrider {
    Optional<Phase> override(PodInstanceRequirement podInstanceRequirement);
}
