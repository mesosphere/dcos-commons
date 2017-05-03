package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;

import java.util.Optional;

/**
 * Created by gabriel on 5/1/17.
 */
public interface RecoveryPlanOverrider {
    Optional<Phase> override(PodInstanceRequirement podInstanceRequirement);
}
